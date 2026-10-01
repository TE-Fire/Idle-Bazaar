<script setup lang="ts">
/**
 * A6 订单管理（business/03 A-B7 / F-A6）
 *
 * <p>本页的设计要点只有一个：**看商品快照，不看当前商品**。
 * 卖家可能事后改价改描述，用当前商品去裁决纠纷会取错证（F-A6 判据）。
 * 所以详情抽屉把「快照」单独成一栏，并在快照与当前商品不一致时给出显眼提示——
 * mock 里 o3002 就是这种情形（下单 899 元，现价 640 元）。
 *
 * <p>裁决只在「纠纷中」开放，三种结果：退款买家 / 放款卖家 / 部分退款。
 * 已退款、已完成这类终态订单不给裁决入口，避免重复处置。
 *
 * <p>权限：order.refund 管退款，order.arbitrate 管仲裁。
 */

import { computed, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ARBITRATE_ACTIONS,
  ORDER_STATUS_LABELS,
  PERM_CODES,
  type OrderStatus,
  type TradeOrder
} from '@campus/shared'
import { ORDERS, snapshotDrifted } from '@/mock/orders'
import { tintStyle } from '@/utils/tint'

const list = ref<TradeOrder[]>(ORDERS.map((o) => ({ ...o })))

const filters = reactive<{ keyword: string; status: OrderStatus | 'ALL'; disputeOnly: boolean }>({
  keyword: '',
  status: 'ALL',
  disputeOnly: false
})

const view = computed<TradeOrder[]>(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return list.value.filter((o) => {
    if (filters.status !== 'ALL' && o.status !== filters.status) return false
    if (filters.disputeOnly && o.status !== 'DISPUTE') return false
    if (!kw) return true
    return (
      o.orderNo.toLowerCase().includes(kw) ||
      o.snapshot.title.toLowerCase().includes(kw) ||
      o.buyerNick.toLowerCase().includes(kw) ||
      o.sellerNick.toLowerCase().includes(kw)
    )
  })
})

const reset = (): void => {
  filters.keyword = ''
  filters.status = 'ALL'
  filters.disputeOnly = false
}

const columns: TableColumnsType<TradeOrder> = [
  { title: '订单号', dataIndex: 'orderNo', width: 150 },
  { title: '商品（快照）', key: 'item', width: 280 },
  { title: '金额', key: 'amount', width: 100 },
  { title: '买家 / 卖家', key: 'party', width: 170 },
  { title: '状态', key: 'status', width: 100 },
  { title: '面交', key: 'meet', width: 200 },
  { title: '操作', key: 'action', width: 160 }
]

const statusMeta = (s: OrderStatus): { label: string; color: string } => ORDER_STATUS_LABELS[s]
const disputeCount = computed(() => list.value.filter((o) => o.status === 'DISPUTE').length)

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const current = ref<TradeOrder | null>(null)
const arbitrateAction = ref<'refundBuyer' | 'paySeller' | 'partial'>('refundBuyer')
const partialAmount = ref(0)

const openDetail = (row: TradeOrder): void => {
  current.value = row
  arbitrateAction.value = 'refundBuyer'
  partialAmount.value = Math.round(row.snapshot.price / 2)
  detailOpen.value = true
}

const drifted = (row: TradeOrder): boolean => snapshotDrifted(row)

const onArbitrate = (): void => {
  const row = current.value
  if (!row) return
  if (arbitrateAction.value === 'partial' && partialAmount.value <= 0) {
    message.warning('请填写部分退款金额')
    return
  }
  const label = ARBITRATE_ACTIONS.find((a) => a.value === arbitrateAction.value)?.label ?? ''
  row.status = arbitrateAction.value === 'paySeller' ? 'DONE' : 'REFUNDED'
  message.success(`已裁决：${label}`)
  detailOpen.value = false
}

const onRefund = (row: TradeOrder): void => {
  row.status = 'REFUNDING'
  message.success(`${row.orderNo} 已进入退款流程`)
}
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">订单管理</h1>
      <p class="cm-page__sub">
        纠纷裁决以「下单时的商品快照」为准；当前有 {{ disputeCount }} 单待介入。
      </p>
    </header>

    <div class="cm-card orders__filter">
      <a-space wrap :size="12">
        <a-input
          v-model:value="filters.keyword"
          allow-clear
          placeholder="订单号 / 商品 / 买家 / 卖家"
          style="width: 240px"
        />
        <a-select v-model:value="filters.status" style="width: 150px">
          <a-select-option value="ALL">状态：全部</a-select-option>
          <a-select-option v-for="(m, key) in ORDER_STATUS_LABELS" :key="key" :value="key">
            {{ m.label }}
          </a-select-option>
        </a-select>
        <a-checkbox v-model:checked="filters.disputeOnly">只看纠纷</a-checkbox>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="orders__count">共 {{ view.length }} 单</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="view"
      row-key="id"
      size="middle"
      :pagination="{ pageSize: 10, showTotal: (t: number) => `共 ${t} 条` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'item'">
          <div class="orders__cell">
            <span class="orders__thumb" :style="tintStyle(record.snapshot.tints)" />
            <span class="orders__title">
              {{ record.snapshot.title }}
              <span v-if="drifted(record)" class="orders__drift">快照与当前商品不一致</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'amount'">¥{{ record.snapshot.price }}</template>

        <template v-else-if="column.key === 'party'">
          {{ record.buyerNick }}
          <span class="orders__muted">←</span>
          {{ record.sellerNick }}
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="statusMeta(record.status).color">{{ statusMeta(record.status).label }}</a-tag>
        </template>

        <template v-else-if="column.key === 'meet'">
          {{ record.meetPoint }}
          <span class="orders__muted">· {{ record.meetAt.slice(5, 16).replace('T', ' ') }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.status === 'PAID' || record.status === 'MEETING'"
              v-perm="PERM_CODES.ORDER_REFUND"
              type="link"
              size="small"
              @click="onRefund(record)"
            >
              退款
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <a-drawer v-model:open="detailOpen" title="订单详情" :width="520">
      <template v-if="current">
        <a-descriptions :column="2" bordered size="small" class="orders__desc">
          <a-descriptions-item label="订单号">{{ current.orderNo }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="statusMeta(current.status).color">{{ statusMeta(current.status).label }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="买家">{{ current.buyerNick }}</a-descriptions-item>
          <a-descriptions-item label="卖家">{{ current.sellerNick }}</a-descriptions-item>
          <a-descriptions-item label="面交点">{{ current.meetPoint }}</a-descriptions-item>
          <a-descriptions-item label="约定时间">
            {{ current.meetAt.slice(0, 16).replace('T', ' ') }}
          </a-descriptions-item>
        </a-descriptions>

        <!-- 商品快照：纠纷取证的关键，必须与「当前商品」区分开 -->
        <h3 class="orders__sub">
          商品快照
          <span class="orders__sub-note">下单时</span>
        </h3>
        <div class="orders__snapshot">
          <span class="orders__thumb orders__thumb--lg" :style="tintStyle(current.snapshot.tints)" />
          <div>
            <p class="orders__snap-title">{{ current.snapshot.title }}</p>
            <p class="orders__snap-meta">
              ¥{{ current.snapshot.price }} · 成色 {{ current.snapshot.condition }}
            </p>
            <p class="orders__snap-desc">{{ current.snapshot.description }}</p>
          </div>
        </div>
        <p v-if="drifted(current)" class="orders__warn">
          该商品在下单后已被修改，裁决请以本快照为准，不要用当前商品信息。
        </p>

        <template v-if="current.dispute">
          <h3 class="orders__sub">纠纷信息</h3>
          <a-descriptions :column="1" bordered size="small" class="orders__desc">
            <a-descriptions-item label="发起方">
              {{ current.dispute.by === 'buyer' ? '买家' : '卖家' }}
            </a-descriptions-item>
            <a-descriptions-item label="发起时间">
              {{ current.dispute.at.slice(0, 16).replace('T', ' ') }}
            </a-descriptions-item>
            <a-descriptions-item label="理由">{{ current.dispute.reason }}</a-descriptions-item>
          </a-descriptions>

          <h3 class="orders__sub">裁决</h3>
          <a-radio-group v-model:value="arbitrateAction" class="orders__radio">
            <a-radio v-for="a in ARBITRATE_ACTIONS" :key="a.value" :value="a.value">{{ a.label }}</a-radio>
          </a-radio-group>
          <a-input-number
            v-if="arbitrateAction === 'partial'"
            v-model:value="partialAmount"
            :min="1"
            :max="current.snapshot.price"
            :precision="0"
            addon-before="退款金额"
            class="orders__amount"
          />
          <div class="orders__actions">
            <a-button v-perm="PERM_CODES.ORDER_ARBITRATE" type="primary" shape="round" @click="onArbitrate">
              提交裁决
            </a-button>
          </div>
        </template>
      </template>
    </a-drawer>
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

/* 快照漂移提示：这是本页最重要的一个提示，不能做成浅色小字 */
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
}

.orders__warn {
  margin: var(--cm-space-3) 0 0;
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-orange-50, #fff7e6);
  color: var(--cm-color-orange-600, #ff9f0a);
  font-size: var(--cm-font-size-sm);
}

.orders__radio {
  margin-bottom: var(--cm-space-4);
}

.orders__amount {
  width: 180px;
}

.orders__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-4);
}
</style>
