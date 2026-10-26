<script setup lang="ts">
/**
 * A6 订单管理（business/03 A-B7 / F-A6，接口见 AdminOrderController）
 *
 * <p>本页的设计要点只有一个：**看商品快照，不看当前商品**。
 * 卖家可能事后改价改描述，用当前商品去裁决纠纷会取错证。所以详情抽屉把「快照」单独成一栏，
 * 并把「卖家已改成什么」并排列出来——只说「不一致」而不说「从多少变成多少」，管理员还是判不了。
 *
 * <p>漂移标记由后端算（`snapshotDrifted`）：规则若留给前端，Web 与小程序迟早各写一套。
 *
 * <p>可处置范围被刻意收窄，与后端状态机一致：
 *   ① 强制退款——仅「退款中 / 纠纷中」，其余状态交易仍在进行或已终态；
 *   ② 纠纷裁决——仅「纠纷中」，且两种结果：退款买家 / 放款卖家（不做部分退）。
 *
 * <p>权限：order.view 开页面，order.refund 管退款，order.arbitrate 管裁决。
 * 只读角色只有 order.view，能看到纠纷但处置按钮不渲染——这是权限矩阵的设计，不是功能缺失。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { ARBITRATE_ACTIONS, ORDER_STATUS_LABELS, PERM_CODES } from '@campus/shared'
import { adminOrderApi, type AdminOrderStatus, type AdminOrderVO } from '@campus/api'

const PAGE_SIZE = 10

/* ── 列表 ── */

const rows = ref<AdminOrderVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

const filters = reactive<{
  keyword: string
  status: AdminOrderStatus | ''
  disputeOnly: boolean
}>({ keyword: '', status: '', disputeOnly: false })

const queryParams = () => ({
  keyword: filters.keyword.trim() || undefined,
  status: filters.status || undefined,
  disputeOnly: filters.disputeOnly || undefined,
  pageNum: pageNum.value,
  pageSize: PAGE_SIZE
})

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const r = await adminOrderApi.list(queryParams())
    rows.value = r?.list ?? []
    total.value = r?.total ?? 0
  } catch {
    // 请求层已提示（含 traceId）。保留上一页数据，空表容易被误读成"真没有订单"
  } finally {
    loading.value = false
  }
}

/** 待介入数：单独取一次纠纷总数，不能从当前页数出来（当前页可能一条纠纷都没有） */
const pendingDispute = ref(0)
const loadPending = async (): Promise<void> => {
  try {
    const r = await adminOrderApi.list({ disputeOnly: true, pageNum: 1, pageSize: 1 })
    pendingDispute.value = r?.total ?? 0
  } catch {
    pendingDispute.value = 0
  }
}

onMounted(() => {
  void Promise.all([load(), loadPending()])
})

const onSearch = (): void => {
  pageNum.value = 1
  void load()
}

const reset = (): void => {
  filters.keyword = ''
  filters.status = ''
  filters.disputeOnly = false
  pageNum.value = 1
  void load()
}

const onTableChange = (pagination: { current?: number }): void => {
  pageNum.value = pagination.current ?? 1
  void load()
}

/* ── 展示辅助 ── */

const statusMeta = (s: AdminOrderStatus) => ORDER_STATUS_LABELS[s]

/** 主图是演示外链（cdn.campus.local），解析不到会满屏碎图标，失败后退成占位块 */
const thumbFailed = reactive<Record<string, boolean>>({})

const money = (v?: string | null): string => (v ? `¥${v}` : '—')

/** 面交时间在列表里只留「月-日 时:分」，后端格式是 yyyy-MM-dd HH:mm:ss */
const shortTime = (v?: string | null): string => (v ? v.slice(5, 16) : '—')

const columns: TableColumnsType<AdminOrderVO> = [
  { title: '订单号', dataIndex: 'orderNo', width: 160 },
  { title: '商品（快照）', key: 'item', width: 260 },
  { title: '金额', key: 'amount', width: 100 },
  { title: '买家 / 卖家', key: 'party', width: 170 },
  { title: '状态', key: 'status', width: 100 },
  { title: '面交', key: 'meet', width: 190 },
  { title: '操作', key: 'action', width: 150 }
]

/** 可退款的状态与后端一致：只有两个悬置态 */
const canRefund = (o: AdminOrderVO): boolean =>
  o.status === 'REFUNDING' || o.status === 'DISPUTING'

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const detail = ref<AdminOrderVO | null>(null)
const detailLoading = ref(false)

const openDetail = async (row: AdminOrderVO): Promise<void> => {
  detail.value = null
  detailOpen.value = true
  detailLoading.value = true
  try {
    detail.value = await adminOrderApi.detail(row.id)
  } catch {
    detailOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

/**
 * 漂移明细：不只说「不一致」，要说清「从多少变成多少」。
 * 商品被删除时 currentPrice 缺失，单独给一句——这时快照是唯一可信来源。
 */
const driftItems = (o: AdminOrderVO): string[] => {
  const out: string[] = []
  if (!o.currentPrice) {
    if (o.snapshotDrifted) out.push('商品已不存在，只能以快照为准')
    return out
  }
  if (Number(o.currentPrice) !== Number(o.itemPrice)) {
    out.push(`价格 ${money(o.itemPrice)} → ${money(o.currentPrice)}`)
  }
  if (o.currentGrade !== o.itemGrade) {
    out.push(`成色 ${o.itemGrade ?? '—'} → ${o.currentGrade ?? '—'}`)
  }
  if (o.currentDescription !== undefined && o.currentDescription !== o.itemDescription) {
    out.push('描述已被卖家修改')
  }
  return out
}

/* ── 强制退款 ── */

const refundOpen = ref(false)
const refundTarget = ref<AdminOrderVO | null>(null)
const refundNote = ref('')
const submitting = ref(false)

const openRefund = (o: AdminOrderVO): void => {
  refundTarget.value = o
  refundNote.value = ''
  refundOpen.value = true
}

const submitRefund = async (): Promise<void> => {
  const target = refundTarget.value
  if (!target) return
  submitting.value = true
  try {
    await adminOrderApi.refund(target.id, { note: refundNote.value.trim() || undefined })
    message.success(`${target.orderNo} 已退款，商品恢复在售`)
    refundOpen.value = false
    detailOpen.value = false
    await Promise.all([load(), loadPending()])
  } finally {
    submitting.value = false
  }
}

/* ── 纠纷裁决 ── */

const arbitrateAction = ref<'REFUND_BUYER' | 'PAY_SELLER'>('REFUND_BUYER')
const arbitrateNote = ref('')

const submitArbitrate = async (): Promise<void> => {
  const o = detail.value
  if (!o) return
  submitting.value = true
  try {
    await adminOrderApi.arbitrate(o.id, {
      result: arbitrateAction.value,
      note: arbitrateNote.value.trim() || undefined
    })
    const label = ARBITRATE_ACTIONS.find((a) => a.value === arbitrateAction.value)?.label ?? ''
    message.success(`已裁决：${label}`)
    arbitrateNote.value = ''
    detailOpen.value = false
    await Promise.all([load(), loadPending()])
  } finally {
    submitting.value = false
  }
}

const disputeByLabel = computed(() =>
  detail.value?.disputeBy === 2 ? '卖家' : '买家'
)
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">订单管理</h1>
      <p class="cm-page__sub">
        纠纷裁决以「下单时的商品快照」为准；当前有 {{ pendingDispute }} 单待介入。
      </p>
    </header>

    <div class="cm-card orders__filter">
      <a-space wrap :size="12">
        <a-input
          v-model:value="filters.keyword"
          allow-clear
          placeholder="订单号 / 商品 / 买家 / 卖家"
          style="width: 240px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="filters.status" style="width: 150px" @change="onSearch">
          <a-select-option value="">状态：全部</a-select-option>
          <a-select-option v-for="(m, key) in ORDER_STATUS_LABELS" :key="key" :value="key">
            {{ m.label }}
          </a-select-option>
        </a-select>
        <a-checkbox v-model:checked="filters.disputeOnly" @change="onSearch">只看纠纷</a-checkbox>
        <a-button type="primary" shape="round" @click="onSearch">查询</a-button>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="orders__count">共 {{ total }} 单</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{
        current: pageNum,
        pageSize: PAGE_SIZE,
        total,
        showSizeChanger: false,
        showTotal: (t: number) => `共 ${t} 条`
      }"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'item'">
          <div class="orders__cell">
            <img
              v-if="record.itemMainImage && !thumbFailed[record.id]"
              :src="record.itemMainImage"
              class="orders__thumb"
              alt=""
              @error="thumbFailed[record.id] = true"
            />
            <span v-else class="orders__thumb orders__thumb--empty" />
            <span class="orders__title">
              {{ record.itemTitle || '（商品已删除）' }}
              <span v-if="record.snapshotDrifted" class="orders__drift">快照与当前商品不一致</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'amount'">{{ money(record.amount) }}</template>

        <template v-else-if="column.key === 'party'">
          {{ record.buyerNick || '—' }}
          <span class="orders__muted">←</span>
          {{ record.sellerNick || '—' }}
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).label }}</a-tag>
        </template>

        <template v-else-if="column.key === 'meet'">
          {{ record.meetPointName || '—' }}
          <span class="orders__muted">· {{ shortTime(record.meetTime) }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="canRefund(record)"
              v-perm="PERM_CODES.ORDER_REFUND"
              type="link"
              size="small"
              danger
              @click="openRefund(record)"
            >
              强制退款
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="detailOpen" title="订单详情" :width="560">
      <a-spin :spinning="detailLoading">
        <template v-if="detail">
          <a-descriptions :column="2" bordered size="small" class="orders__desc">
            <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusMeta(detail.status).color">{{ statusMeta(detail.status).label }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="订单金额">{{ money(detail.amount) }}</a-descriptions-item>
            <a-descriptions-item label="下单时间">{{ detail.createTime }}</a-descriptions-item>
            <a-descriptions-item label="买家">{{ detail.buyerNick || '—' }}</a-descriptions-item>
            <a-descriptions-item label="卖家">{{ detail.sellerNick || '—' }}</a-descriptions-item>
            <a-descriptions-item label="面交点">{{ detail.meetPointName || '—' }}</a-descriptions-item>
            <a-descriptions-item label="约定时间">{{ detail.meetTime || '—' }}</a-descriptions-item>
            <a-descriptions-item v-if="detail.payTime" label="付款时间">
              {{ detail.payTime }}
            </a-descriptions-item>
            <a-descriptions-item v-if="detail.cancelReason" label="取消 / 退款原因" :span="2">
              {{ detail.cancelReason }}
            </a-descriptions-item>
          </a-descriptions>

          <!-- 商品快照：纠纷取证的关键，必须与「当前商品」区分开 -->
          <h3 class="orders__sub">
            商品快照
            <span class="orders__sub-note">下单时</span>
          </h3>
          <div class="orders__snapshot">
            <img
              v-if="detail.itemMainImage && !thumbFailed[detail.id]"
              :src="detail.itemMainImage"
              class="orders__thumb orders__thumb--lg"
              alt=""
              @error="thumbFailed[detail.id] = true"
            />
            <span v-else class="orders__thumb orders__thumb--lg orders__thumb--empty" />
            <div>
              <p class="orders__snap-title">{{ detail.itemTitle || '（商品已删除）' }}</p>
              <p class="orders__snap-meta">
                {{ money(detail.itemPrice) }} · 成色 {{ detail.itemGrade || '—' }}
              </p>
              <p class="orders__snap-desc">{{ detail.itemDescription || '（无描述）' }}</p>
            </div>
          </div>

          <div v-if="detail.snapshotDrifted" class="orders__warn">
            <p class="orders__warn-title">该商品在下单后已被修改，裁决请以本快照为准</p>
            <ul class="orders__warn-list">
              <li v-for="d in driftItems(detail)" :key="d">{{ d }}</li>
            </ul>
          </div>

          <template v-if="detail.appealId">
            <h3 class="orders__sub">纠纷信息</h3>
            <a-descriptions :column="1" bordered size="small" class="orders__desc">
              <a-descriptions-item label="发起方">{{ disputeByLabel }}</a-descriptions-item>
              <a-descriptions-item label="发起时间">{{ detail.disputeAt || '—' }}</a-descriptions-item>
              <a-descriptions-item label="理由">{{ detail.disputeReason || '—' }}</a-descriptions-item>
              <a-descriptions-item v-if="detail.disputeEvidence" label="凭证">
                {{ detail.disputeEvidence }}
              </a-descriptions-item>
            </a-descriptions>

            <!-- 裁决只在纠纷中开放：终态订单再裁一次会被后端拒，入口不留 -->
            <template v-if="detail.status === 'DISPUTING'">
              <h3 class="orders__sub">裁决</h3>
              <a-radio-group v-model:value="arbitrateAction" class="orders__radio">
                <a-radio v-for="a in ARBITRATE_ACTIONS" :key="a.value" :value="a.value">
                  {{ a.label }}
                  <span class="orders__radio-hint">{{ a.hint }}</span>
                </a-radio>
              </a-radio-group>
              <a-textarea
                v-model:value="arbitrateNote"
                :rows="2"
                :maxlength="200"
                show-count
                placeholder="裁决理由（买卖双方可见，建议写明依据）"
              />
              <div class="orders__actions">
                <a-button
                  v-perm="PERM_CODES.ORDER_ARBITRATE"
                  type="primary"
                  shape="round"
                  :loading="submitting"
                  @click="submitArbitrate"
                >
                  提交裁决
                </a-button>
              </div>
            </template>
            <p v-else-if="detail.disputeNote" class="orders__note">
              处理结论：{{ detail.disputeNote }}
            </p>
          </template>

          <div v-if="canRefund(detail)" class="orders__actions">
            <a-button
              v-perm="PERM_CODES.ORDER_REFUND"
              danger
              shape="round"
              @click="openRefund(detail)"
            >
              强制退款
            </a-button>
          </div>
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="refundOpen"
      title="强制退款"
      ok-text="确认退款"
      cancel-text="取消"
      :confirm-loading="submitting"
      @ok="submitRefund"
    >
      <p class="orders__modal-tip">
        退款后订单转「已退款」，商品恢复在售。该操作会记入操作日志，请写明依据。
      </p>
      <a-textarea
        v-model:value="refundNote"
        :rows="3"
        :maxlength="200"
        show-count
        placeholder="退款原因（如：卖家超时未响应，平台代为退款）"
      />
    </a-modal>
  </div>
</template>

<style scoped>
.orders__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  padding: var(--cm-space-4) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.orders__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.orders__cell {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.orders__thumb {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--cm-radius-md);
  object-fit: cover;
}

/* 图片缺失或加载失败的占位：不给碎图标，给一块浅灰 */
.orders__thumb--empty {
  display: block;
  background: var(--cm-color-surface-muted);
}

.orders__thumb--lg {
  width: 64px;
  height: 64px;
}

.orders__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

/* 快照漂移提示：本页最重要的提示，不能做成浅色小字 */
.orders__drift {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-orange-600, #ff9f0a);
}

.orders__muted {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-xs);
}

.orders__desc {
  margin-bottom: var(--cm-space-5);
}

.orders__sub {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.orders__sub-note {
  font-size: var(--cm-font-size-xs);
  font-weight: 400;
  color: var(--cm-color-text-3);
}

.orders__snapshot {
  display: flex;
  gap: var(--cm-space-4);
  padding: var(--cm-space-4);
  border-radius: var(--cm-radius-lg);
  background: var(--cm-color-surface-muted);
}

.orders__snap-title {
  margin: 0;
  font-weight: 500;
}

.orders__snap-meta {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.orders__snap-desc {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
  line-height: 1.6;
  white-space: pre-wrap;
}

.orders__warn {
  margin: var(--cm-space-3) 0 0;
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-orange-50, #fff7e6);
  color: var(--cm-color-orange-600, #ff9f0a);
  font-size: var(--cm-font-size-sm);
}

.orders__warn-title {
  margin: 0;
  font-weight: 600;
}

.orders__warn-list {
  margin: var(--cm-space-2) 0 0;
  padding-left: 18px;
  line-height: 1.7;
}

.orders__note {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.orders__radio {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-4);
}

.orders__radio-hint {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.orders__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--cm-space-3);
  margin-top: var(--cm-space-4);
}

.orders__modal-tip {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}
</style>
