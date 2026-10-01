<script setup lang="ts">
/**
 * A3 商品管理（business/03 A-B4）
 *
 * <p>与 C 端商品列表的最大区别：**这里只能下架，不能代编辑**。
 * business/03 §一 明确「不做替用户改价改描述」——只能强制下架，不能代编辑，避免责任不清。
 * 所以详情页有「下架」没有「编辑」，页面上还要把这句话写出来，否则会被当成功能缺失。
 *
 * <p>价格列额外给出**同类目成交均价**：审核员要判断「这个价合不合理」，
 * 偏离 3 倍以上标红，同时也是审核权重里「价格异常分」的来源（F-A2）。
 *
 * <p>权限：item.view 开页面，item.offline 管下架，item.delete 管删除。
 * 审核员只有 item.view——能看到但不能处置，商品处置必须走 A5 审核队列。
 */

import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ITEM_STATUS_LABELS,
  OFFLINE_REASONS,
  PERM_CODES,
  isPriceAbnormal,
  type AdminItem,
  type ItemStatus,
  type OfflineReason
} from '@campus/shared'
import { ITEMS } from '@/mock/items'
import { ROOT_CATEGORIES, categoryName, campusName } from '@/mock/dict'
import { tintStyle } from '@/utils/tint'

type StatusKey = ItemStatus

/** 状态配色：与 C 端一致，正常绿、待办蓝、异常红、终态灰 */
const STATUS_COLORS: Record<StatusKey, string> = {
  DRAFT: 'default',
  PENDING: 'blue',
  ON_SALE: 'green',
  SOLD: 'default',
  OFFLINE: 'default',
  REJECTED: 'red',
  FORCED_OFFLINE: 'red'
}

const list = ref<AdminItem[]>(ITEMS.map((i) => ({ ...i })))

const filters = reactive<{ keyword: string; categoryId: string; status: StatusKey | 'ALL'; campusId: string }>({
  keyword: '',
  categoryId: 'ALL',
  status: 'ALL',
  campusId: 'ALL'
})

const view = computed<AdminItem[]>(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return list.value.filter((i) => {
    if (filters.status !== 'ALL' && i.status !== filters.status) return false
    if (filters.campusId !== 'ALL' && i.campusId !== filters.campusId) return false
    // 分类按一级过滤：二级分类的 id 以一级 id 开头（如 ca101 ⊂ ca1）
    if (filters.categoryId !== 'ALL' && !i.categoryId.startsWith(filters.categoryId)) return false
    if (!kw) return true
    return i.title.toLowerCase().includes(kw) || i.sellerNick.toLowerCase().includes(kw)
  })
})

const reset = (): void => {
  filters.keyword = ''
  filters.categoryId = 'ALL'
  filters.status = 'ALL'
  filters.campusId = 'ALL'
}

const columns: TableColumnsType<AdminItem> = [
  { title: '商品', key: 'item', width: 300 },
  { title: '价格', key: 'price', width: 150 },
  { title: '成色', key: 'condition', width: 90 },
  { title: '校区 / 面交点', key: 'place', width: 200 },
  { title: '卖家', key: 'seller', width: 150 },
  { title: '状态', key: 'status', width: 110 },
  { title: '浏览 / 想要', key: 'heat', width: 110 },
  { title: '操作', key: 'action', width: 200 }
]

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const current = ref<AdminItem | null>(null)

const openDetail = (row: AdminItem): void => {
  current.value = row
  detailOpen.value = true
}

const priceAbnormal = (row: AdminItem): boolean => isPriceAbnormal(row)

/* ── 强制下架：必须选原因 + 可补充文字（处置要能复盘） ── */

const offlineOpen = ref(false)
const offlineForm = reactive<{ reason: OfflineReason | ''; note: string }>({ reason: '', note: '' })

const openOffline = (row: AdminItem): void => {
  current.value = row
  offlineForm.reason = ''
  offlineForm.note = ''
  offlineOpen.value = true
}

const submitOffline = (): void => {
  const row = current.value
  if (!row) return
  if (!offlineForm.reason) {
    message.warning('请选择下架原因，处置依据要留痕')
    return
  }
  row.status = 'FORCED_OFFLINE'
  offlineOpen.value = false
  detailOpen.value = false
  message.success(`已下架「${row.title}」，原因：${offlineForm.reason}`)
}

/** 删除只开放给终态商品：在售商品先下架再删，避免误删正在交易的货 */
const canDelete = (row: AdminItem): boolean =>
  row.status === 'OFFLINE' || row.status === 'REJECTED' || row.status === 'FORCED_OFFLINE'

const onDelete = (row: AdminItem): void => {
  Modal.confirm({
    title: `删除「${row.title}」？`,
    content: '删除后不可恢复。建议先下架、观察一段时间再清理。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => {
      list.value = list.value.filter((i) => i.id !== row.id)
      message.success('已删除')
    }
  })
}

const conditionLabel = (code: AdminItem['condition']): string =>
  ({ A: '全新未拆', B: '几乎全新', C: '轻微使用痕迹', D: '明显使用痕迹' })[code]
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">商品管理</h1>
      <p class="cm-page__sub">
        平台只能下架、不能代用户改价改描述；价格异常按同类目成交均价判定。
      </p>
    </header>

    <div class="cm-card items__filter">
      <a-space wrap :size="12">
        <a-input
          v-model:value="filters.keyword"
          allow-clear
          placeholder="标题 / 卖家昵称"
          style="width: 220px"
        />
        <a-select v-model:value="filters.categoryId" style="width: 150px">
          <a-select-option value="ALL">分类：全部</a-select-option>
          <a-select-option v-for="c in ROOT_CATEGORIES" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="filters.status" style="width: 150px">
          <a-select-option value="ALL">状态：全部</a-select-option>
          <a-select-option v-for="(label, key) in ITEM_STATUS_LABELS" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="filters.campusId" style="width: 130px">
          <a-select-option value="ALL">校区：全部</a-select-option>
          <a-select-option value="cp1">中心校区</a-select-option>
          <a-select-option value="cp2">东校区</a-select-option>
          <a-select-option value="cp3">南苑宿舍区</a-select-option>
        </a-select>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="items__count">共 {{ view.length }} 件</span>
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
          <div class="items__cell">
            <span class="items__thumb" :style="tintStyle(record.tints)" />
            <span class="items__title">
              {{ record.title }}
              <span class="items__muted">{{ categoryName(record.categoryId) }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'price'">
          <span :class="{ 'items__abnormal': priceAbnormal(record) }">¥{{ record.price }}</span>
          <span class="items__muted">/ 均价 ¥{{ record.categoryAvgPrice }}</span>
        </template>

        <template v-else-if="column.key === 'condition'">
          <a-tag>{{ record.condition }}</a-tag>
        </template>

        <template v-else-if="column.key === 'place'">
          {{ campusName(record.campusId) }}
          <span class="items__muted">· {{ record.meetPoint }}</span>
        </template>

        <template v-else-if="column.key === 'seller'">
          {{ record.sellerNick }}
          <span class="items__muted">信用 {{ record.sellerCredit }}</span>
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status as StatusKey]">
            {{ ITEM_STATUS_LABELS[record.status as StatusKey] }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'heat'">
          {{ record.viewCount }} / {{ record.wantCount }}
          <span v-if="record.reportCount > 0" class="items__report">举报 {{ record.reportCount }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.status === 'ON_SALE' || record.status === 'PENDING'"
              v-perm="PERM_CODES.ITEM_OFFLINE"
              type="link"
              size="small"
              danger
              @click="openOffline(record)"
            >
              下架
            </a-button>
            <a-button
              v-if="canDelete(record)"
              v-perm="PERM_CODES.ITEM_DELETE"
              type="link"
              size="small"
              danger
              @click="onDelete(record)"
            >
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 详情：只读，没有「编辑」入口 -->
    <a-drawer v-model:open="detailOpen" title="商品详情" :width="520">
      <template v-if="current">
        <div class="items__gallery" :style="tintStyle(current.tints)">
          <span>（图片占位 · 共 1 张）</span>
        </div>

        <h2 class="items__title-lg">{{ current.title }}</h2>

        <a-descriptions :column="2" bordered size="small" class="items__desc">
          <a-descriptions-item label="价格">
            ¥{{ current.price }}
            <span v-if="priceAbnormal(current)" class="items__abnormal">异常</span>
          </a-descriptions-item>
          <a-descriptions-item label="同类目均价">¥{{ current.categoryAvgPrice }}</a-descriptions-item>
          <a-descriptions-item label="成色">
            {{ current.condition }} · {{ conditionLabel(current.condition) }}
          </a-descriptions-item>
          <a-descriptions-item label="原价">
            {{ current.originalPrice ? `¥${current.originalPrice}` : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="分类">{{ categoryName(current.categoryId) }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ ITEM_STATUS_LABELS[current.status as StatusKey] }}
          </a-descriptions-item>
          <a-descriptions-item label="面交点">{{ current.meetPoint }}</a-descriptions-item>
          <a-descriptions-item label="卖家">
            {{ current.sellerNick }}（信用 {{ current.sellerCredit }}）
          </a-descriptions-item>
          <a-descriptions-item label="举报次数">{{ current.reportCount }}</a-descriptions-item>
          <a-descriptions-item label="图片风险分">{{ current.riskScore }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="items__sub">商品描述</h3>
        <p class="items__text">{{ current.description }}</p>

        <p class="items__note">
          平台不代用户修改价格与描述——如需更正，请下架后由卖家自行修改重提。
        </p>

        <div class="items__actions">
          <a-button
            v-if="current.status === 'ON_SALE' || current.status === 'PENDING'"
            v-perm="PERM_CODES.ITEM_OFFLINE"
            danger
            shape="round"
            @click="openOffline(current)"
          >
            强制下架
          </a-button>
        </div>
      </template>
    </a-drawer>

    <a-modal v-model:open="offlineOpen" title="强制下架" ok-text="确认下架" cancel-text="取消" @ok="submitOffline">
      <p class="items__note">处置依据会记入操作日志，卖家可据此申诉。</p>
      <a-form layout="vertical">
        <a-form-item label="下架原因" required>
          <a-select v-model:value="offlineForm.reason" placeholder="选择预设原因">
            <a-select-option v-for="r in OFFLINE_REASONS" :key="r" :value="r">{{ r }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="补充说明">
          <a-textarea v-model:value="offlineForm.note" :rows="3" placeholder="可补充具体依据（选填）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.items__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  padding: var(--cm-space-4) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.items__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.items__cell {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.items__thumb {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--cm-radius-md);
}

.items__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.items__muted {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-xs);
}

/* 价格异常：红字 + 角标，因为它是审核权重的输入之一 */
.items__abnormal {
  margin-left: var(--cm-space-2);
  padding: 0 6px;
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-red-100, #fcecec);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

span.items__abnormal {
  display: inline-block;
}

.items__report {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

.items__gallery {
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cm-radius-lg);
  margin-bottom: var(--cm-space-5);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
}

.items__title-lg {
  margin: 0 0 var(--cm-space-4);
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.items__desc {
  margin-bottom: var(--cm-space-5);
}

.items__sub {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.items__text {
  margin: 0;
  color: var(--cm-color-text-2);
  line-height: 1.7;
}

.items__note {
  margin: var(--cm-space-4) 0 0;
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.items__actions {
  margin-top: var(--cm-space-5);
  display: flex;
  justify-content: flex-end;
}
</style>
