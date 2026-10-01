<script setup lang="ts">
/**
 * A5 审核队列（business/03 F-A2 / F-A3 / F-A4 / F-A9）—— 管理端每日最大工作量
 *
 * <p>三个来源（商品审核 / 举报处理 / 申诉复审）**压成一条队列**，才能共用同一套交互：
 *   ① 排序：申诉 > 举报 > 商品审核，同组内按**权重分**降序（不能按时间——
 *      按时间等于让风险最高的单子排在后面，F-A2 判据）；
 *   ② 连续审核：处置完自动跳下一条，J / K 键前后切换，详情用 Drawer 不跳页
 *      （单条处置 ≤30 秒是 SLA 的操作效率指标，跳页就达不到）；
 *   ③ 留痕：每次处置写一条操作日志，申诉时才能复盘。
 *
 * <p>权重不是黑盒：表格里点权重分能看到四项组成（举报×3 / 风险 / 信用 / 价格异常 / 等待补偿），
 * 论文里可以直接引用 `calcWeight` 的公式。
 *
 * <p>换人复审（F-A4）：申诉任务带原处置人，页面提示「原处置人不得复审自己的单」。
 * 单人毕设演示无法强制换人，所以这里只做提示，规则本身要写清楚。
 */

import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  OFFLINE_REASONS,
  PERM_CODES,
  QUEUE_KIND_LABELS,
  SLA_LEVEL_LABELS,
  isPriceAbnormal,
  slaLevel,
  type OfflineReason,
  type QueueKind,
  type QueueTask,
  type SlaLevel
} from '@campus/shared'
import { QUEUE_TASKS, sortedQueue, waitMinutesOf } from '@/mock/review'
import { DEFAULT_SLA, OPERATION_LOGS } from '@/mock/settings'
import { categoryName } from '@/mock/dict'
import { ITEMS } from '@/mock/items'
import { humanizeWait } from '@/mock/now'
import { tintStyle } from '@/utils/tint'

const sla = DEFAULT_SLA

const queue = ref<QueueTask[]>(sortedQueue(QUEUE_TASKS))
const kindFilter = ref<QueueKind | 'ALL'>('ALL')
const loading = ref(false)

const view = computed<QueueTask[]>(() =>
  kindFilter.value === 'ALL' ? queue.value : queue.value.filter((t) => t.kind === kindFilter.value)
)

/** 当前游标：连续审核靠它，处置完不动它即可自动落到下一条 */
const cursor = ref(0)
const current = computed<QueueTask | null>(() => view.value[cursor.value] ?? null)
const drawerOpen = ref(false)

const openAt = (index: number): void => {
  if (index < 0 || index >= view.value.length) return
  cursor.value = index
  resetForm()
  drawerOpen.value = true
}

/* ── SLA ── */

const waitOf = (task: QueueTask): number => waitMinutesOf(task)
const levelOf = (task: QueueTask): SlaLevel => slaLevel(waitOf(task) / 60, sla)
const levelMeta = (level: SlaLevel): { label: string; color: string } => SLA_LEVEL_LABELS[level]

const overdueCount = computed(
  () => queue.value.filter((t) => levelOf(t) === 'overdue').length
)
const dueCount = computed(() => queue.value.filter((t) => levelOf(t) === 'due').length)

/* ── 处置 ── */

const form = reactive<{ reason: OfflineReason | ''; note: string }>({ reason: '', note: '' })

const resetForm = (): void => {
  form.reason = ''
  form.note = ''
}

/** 写操作日志：business/03 要求「所有写操作必须记操作日志」，申诉时才能复盘 */
const writeLog = (task: QueueTask, action: string, basis?: string): void => {
  OPERATION_LOGS.unshift({
    id: `l-${Date.now()}`,
    operator: '当前管理员',
    module: task.kind === 'appeal' ? '申诉' : task.kind === 'report' ? '举报' : '审核',
    action,
    target: `${task.itemTitle}（${task.itemId}）`,
    basis,
    ip: '10.12.33.7',
    at: new Date().toISOString()
  })
}

/** 处置完移除队首并自动进入下一条 */
const finish = (task: QueueTask, action: string, basis?: string): void => {
  writeLog(task, action, basis)
  queue.value = queue.value.filter((t) => t.id !== task.id)
  if (cursor.value >= view.value.length) cursor.value = Math.max(0, view.value.length - 1)
  message.success(`${action}：${task.itemTitle}`)
  if (view.value.length === 0) {
    drawerOpen.value = false
    message.info('队列已清空')
  } else {
    drawerOpen.value = true
  }
}

const onPass = (): void => {
  const task = current.value
  if (!task) return
  finish(task, task.kind === 'appeal' ? '申诉撤销' : '通过')
}

const onReject = (): void => {
  const task = current.value
  if (!task) return
  if (!form.reason) {
    message.warning('请选择驳回原因，卖家要据此修改后重提')
    return
  }
  finish(task, task.kind === 'appeal' ? '申诉维持' : '驳回', form.reason)
}

const onForceOff = (): void => {
  const task = current.value
  if (!task) return
  if (!form.reason) {
    message.warning('请选择下架原因')
    return
  }
  const item = ITEMS.find((i) => i.id === task.itemId)
  if (item) item.status = 'FORCED_OFFLINE'
  finish(task, '强制下架', form.reason)
}

const onNext = (): void => openAt(cursor.value + 1)

/* ── 键盘 J / K：连续审核的效率关键 ── */

const onKeydown = (e: KeyboardEvent): void => {
  // 在输入框里打字时不抢快捷键
  const el = e.target as HTMLElement | null
  if (el && ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName)) return
  if (e.key === 'j' || e.key === 'J') {
    openAt(cursor.value + 1)
  } else if (e.key === 'k' || e.key === 'K') {
    openAt(cursor.value - 1)
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => window.removeEventListener('keydown', onKeydown))

/* ── 列 ── */

const columns: TableColumnsType<QueueTask> = [
  { title: '权重', key: 'weight', width: 90 },
  { title: '类型', key: 'kind', width: 100 },
  { title: '商品', key: 'item', width: 280 },
  { title: '价格', key: 'price', width: 140 },
  { title: '卖家信用', key: 'credit', width: 100 },
  { title: '举报 / 风险', key: 'risk', width: 110 },
  { title: '等待时长', key: 'wait', width: 130 },
  { title: '操作', key: 'action', width: 90 }
]

const kindLabel = (k: QueueKind): string => QUEUE_KIND_LABELS[k]

/** 权重组成的可读解释：让「为什么它排第一」可追溯 */
const weightDetail = (t: QueueTask): string =>
  [
    `举报 ${t.reportCount} × 3 = ${t.parts.report}`,
    `风险分归一 = ${t.parts.risk}`,
    `信用扣减 = ${t.parts.credit.toFixed(1)}`,
    `价格异常 = ${t.parts.price}`,
    `等待补偿 = ${t.parts.wait}`
  ].join('；')

const priceAbnormal = (t: QueueTask): boolean =>
  isPriceAbnormal({ price: t.itemPrice, categoryAvgPrice: t.categoryAvgPrice })
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head review__head">
      <div>
        <h1 class="cm-page__title">审核队列</h1>
        <p class="cm-page__sub">
          按权重排序，申诉优先于举报、举报优先于商品审核；处置后自动跳下一条（J / K 切换）。
        </p>
      </div>
      <a-space :size="12">
        <a-button shape="round" :loading="loading" @click="openAt(cursor)">继续审核</a-button>
      </a-space>
    </header>

    <!-- SLA 概览：business/03 F-A9 的两级指标，这里呈现「响应时效」那一级 -->
    <div class="cm-card review__sla">
      <div class="review__sla-item">
        <span class="review__sla-num">{{ queue.length }}</span>
        <span class="review__sla-label">待处置</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num review__sla-num--due">{{ dueCount }}</span>
        <span class="review__sla-label">临期（≥{{ sla.firstResponseHours }}h）</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num review__sla-num--over">{{ overdueCount }}</span>
        <span class="review__sla-label">积压红牌（≥{{ sla.backlogHours }}h）</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num">{{ sla.avgHandleSeconds }}s</span>
        <span class="review__sla-label">单条处置目标</span>
      </div>
    </div>

    <div class="cm-card review__tabs">
      <a-space :size="8">
        <a-button :type="kindFilter === 'ALL' ? 'primary' : 'default'" shape="round" size="small" @click="kindFilter = 'ALL'">
          全部 {{ QUEUE_TASKS.length }}
        </a-button>
        <a-button :type="kindFilter === 'appeal' ? 'primary' : 'default'" shape="round" size="small" @click="kindFilter = 'appeal'">
          申诉复审
        </a-button>
        <a-button :type="kindFilter === 'report' ? 'primary' : 'default'" shape="round" size="small" @click="kindFilter = 'report'">
          举报处理
        </a-button>
        <a-button :type="kindFilter === 'item' ? 'primary' : 'default'" shape="round" size="small" @click="kindFilter = 'item'">
          商品审核
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="view"
      row-key="id"
      size="middle"
      :pagination="false"
      :row-class-name="(_: unknown, index: number) => (index === cursor ? 'review__row-active' : '')"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'weight'">
          <a-tooltip :title="weightDetail(record)">
            <span class="review__weight">{{ record.weight }}</span>
          </a-tooltip>
        </template>

        <template v-else-if="column.key === 'kind'">
          <a-tag :color="record.kind === 'appeal' ? 'purple' : record.kind === 'report' ? 'orange' : 'blue'">
            {{ kindLabel(record.kind) }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'item'">
          <div class="review__cell">
            <span class="review__thumb" :style="tintStyle(record.itemTints)" />
            <span class="review__title">
              {{ record.itemTitle }}
              <span class="review__muted">{{ categoryName(ITEMS.find((i) => i.id === record.itemId)?.categoryId ?? '') }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'price'">
          <span :class="{ 'review__abnormal': priceAbnormal(record) }">¥{{ record.itemPrice }}</span>
          <span class="review__muted">/ 均价 ¥{{ record.categoryAvgPrice }}</span>
        </template>

        <template v-else-if="column.key === 'credit'">
          <span :class="{ 'review__low': record.sellerCredit < 60 }">{{ record.sellerCredit }}</span>
        </template>

        <template v-else-if="column.key === 'risk'">
          {{ record.reportCount }} / {{ record.riskScore }}
        </template>

        <template v-else-if="column.key === 'wait'">
          <a-tag :color="levelMeta(levelOf(record)).color">
            {{ levelMeta(levelOf(record)).label }}
          </a-tag>
          <span class="review__muted">{{ humanizeWait(waitOf(record)) }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openAt(view.indexOf(record))">处置</a-button>
        </template>
      </template>
    </a-table>

    <!-- 处置抽屉：不跳页，处置完自动进下一条 -->
    <a-drawer v-model:open="drawerOpen" :width="560" title="审核处置">
      <template v-if="current">
        <div class="review__head-row">
          <a-tag :color="current.kind === 'appeal' ? 'purple' : current.kind === 'report' ? 'orange' : 'blue'">
            {{ kindLabel(current.kind) }}
          </a-tag>
          <span class="review__muted">权重 {{ current.weight }} · {{ weightDetail(current) }}</span>
        </div>

        <!-- 换人复审提示（F-A4） -->
        <p v-if="current.kind === 'appeal'" class="review__notice">
          原处置人：<b>{{ current.handledBy }}</b>（{{ current.handledResult }}）。
          规则要求原处置人不得复审自己的单；单人演示时此处仅作提示。
        </p>
        <p v-if="current.reason" class="review__notice">
          发起方理由：{{ current.reason }}
        </p>

        <!-- 判读四项：图片 / 标题描述 / 价格 / 分类成色 -->
        <div class="review__gallery" :style="tintStyle(current.itemTints)">
          <span>（图片占位 · 审核要点：是否网图、是否含联系方式或二维码）</span>
        </div>

        <h2 class="review__title-lg">{{ current.itemTitle }}</h2>

        <a-descriptions :column="2" bordered size="small" class="review__desc">
          <a-descriptions-item label="售价">
            ¥{{ current.itemPrice }}
            <span v-if="priceAbnormal(current)" class="review__abnormal">异常</span>
          </a-descriptions-item>
          <a-descriptions-item label="同类目均价">¥{{ current.categoryAvgPrice }}</a-descriptions-item>
          <a-descriptions-item label="卖家">{{ current.sellerNick }}</a-descriptions-item>
          <a-descriptions-item label="卖家信用">
            <span :class="{ 'review__low': current.sellerCredit < 60 }">{{ current.sellerCredit }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="举报次数">{{ current.reportCount }}</a-descriptions-item>
          <a-descriptions-item label="风险分">{{ current.riskScore }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="review__sub">商品描述</h3>
        <p class="review__text">{{ ITEMS.find((i) => i.id === current?.itemId)?.description }}</p>

        <h3 class="review__sub">处置</h3>
        <a-form layout="vertical">
          <a-form-item label="原因（驳回 / 强制下架必填）">
            <a-select v-model:value="form.reason" placeholder="选择预设原因">
              <a-select-option v-for="r in OFFLINE_REASONS" :key="r" :value="r">{{ r }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="补充说明">
            <a-textarea v-model:value="form.note" :rows="2" placeholder="处置依据（选填，会记入操作日志）" />
          </a-form-item>
        </a-form>

        <div class="review__actions">
          <a-space>
            <a-button v-perm="PERM_CODES.REVIEW_JUDGE" type="primary" shape="round" @click="onPass">
              {{ current.kind === 'appeal' ? '撤销原判' : '通过' }}
            </a-button>
            <a-button v-perm="PERM_CODES.REVIEW_JUDGE" shape="round" @click="onReject">
              {{ current.kind === 'appeal' ? '维持原判' : '驳回' }}
            </a-button>
            <a-button v-perm="PERM_CODES.REVIEW_FORCE_OFF" danger shape="round" @click="onForceOff">
              强制下架
            </a-button>
            <a-button shape="round" @click="onNext">跳过（J）</a-button>
          </a-space>
        </div>
        <p class="review__hint">键盘 J / K 前后切换；处置后自动进入下一条。</p>
      </template>
    </a-drawer>
  </div>
</template>

<style scoped>
.review__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

/* SLA 概览条：四个数字一行，靠留白分隔 */
.review__sla {
  display: flex;
  align-items: center;
  gap: var(--cm-space-6);
  padding: var(--cm-space-4) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.review__sla-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.review__sla-num {
  font-size: var(--cm-font-size-xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.review__sla-num--due {
  color: var(--cm-color-orange-600, #ff9f0a);
}

.review__sla-num--over {
  color: var(--cm-color-red-600, #d92c2c);
}

.review__sla-label {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.review__sla-sep {
  width: 1px;
  height: 32px;
  background: var(--cm-color-border);
}

.review__tabs {
  padding: var(--cm-space-3) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.review__weight {
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-brand);
  cursor: help;
}

.review__cell {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.review__thumb {
  width: 40px;
  height: 40px;
  flex: none;
  border-radius: var(--cm-radius-md);
}

.review__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.review__muted {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-xs);
}

.review__abnormal,
.review__low {
  margin-left: var(--cm-space-2);
  padding: 0 6px;
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-red-100, #fcecec);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

.review__head-row {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  margin-bottom: var(--cm-space-4);
}

.review__notice {
  margin: 0 0 var(--cm-space-3);
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  line-height: 1.6;
}

.review__gallery {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--cm-space-5);
  border-radius: var(--cm-radius-lg);
  margin-bottom: var(--cm-space-4);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
  text-align: center;
}

.review__title-lg {
  margin: 0 0 var(--cm-space-4);
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.review__desc {
  margin-bottom: var(--cm-space-5);
}

.review__sub {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.review__text {
  margin: 0 0 var(--cm-space-2);
  color: var(--cm-color-text-2);
  line-height: 1.7;
}

.review__actions {
  margin-top: var(--cm-space-4);
}

.review__hint {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 当前游标行：连续审核时眼睛要知道自己在哪里 */
:deep(.review__row-active) {
  background: var(--cm-color-brand-bg);
}
</style>
