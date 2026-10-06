<script setup lang="ts">
/**
 * A5 审核队列（business/03 F-A2 / F-A3 / F-A4 / F-A9）—— 管理端每日最大工作量
 *
 * <p>三个来源（商品审核 / 举报处理 / 申诉复审）**压成一条队列**，才能共用同一套交互：
 *   ① 排序：申诉 > 举报 > 商品审核，同组内按**权重分**降序（不能按时间——
 *      按时间等于让风险最高的单子排在后面，F-A2 判据）；
 *   ② 连续审核：处置完自动跳下一条，J / K 键前后切换，详情用 Drawer 不跳页
 *      （单条处置 ≤30 秒是 SLA 的操作效率指标，跳页就达不到）；
 *   ③ 留痕：每次处置写一条操作日志，申诉时才能复盘。`@OpLog` 还没落地，
 *      后端已记 `handler_id` / `first_handle_at` / `reject_reason`，日志由注解统一补。
 *
 * <p>**排序与权重都由后端给，页面不再本地算**（notes/09 问题 2）：
 * 前端若自己排一次，就会出现「排第一的那条解释出来的分不是最高」。
 * 权重五项拆开展示，是为了能回答「它为什么排第一」——只给一个数字，审核员无法判断该不该信它。
 *
 * <p>**SLA 阈值也由后端下发**（`ReviewQueueVO.sla`）：阈值将来要挪到 A9 系统设置里可配，
 * 前端各存一份常量就会出现「页面按 2 小时标红、后端按 4 小时统计」的口径分裂。
 *
 * <p>换人复审（F-A4）：申诉任务带原处置人，复审人不得是他，命中后端返回 3002。
 * 单人毕设演示时这就是唯一能跑到的分支：用 super 复审会被拦，换 reviewer 才走得通。
 *
 * <p>不做「受理」这一步（notes/09 问题 4）：举报点开即受理，裁决一步到位。
 */

import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ITEM_STATUS_LABELS,
  OFFLINE_REASONS,
  PERM_CODES,
  QUEUE_KIND_LABELS,
  SLA_LEVEL_LABELS,
  isPriceAbnormal,
  slaLevel,
  type OfflineReason,
  type QueueKind,
  type SlaLevel
} from '@campus/shared'
import { reviewApi, type ReviewSlaVO, type ReviewTaskVO } from '@campus/api'

/* ── 数据源：整个队列一次拉全（后端不分页） ── */

const rows = ref<ReviewTaskVO[]>([])
const sla = ref<ReviewSlaVO | null>(null)
const loading = ref(false)

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const r = await reviewApi.queue()
    rows.value = r?.list ?? []
    sla.value = r?.sla ?? null
  } catch {
    // 请求层已提示（含 traceId）。保留上一次的数据不清空：
    // 拉失败时看到旧队列，比看到一张空表更容易判断是「没拉到」而不是「真没有」
  } finally {
    loading.value = false
    clampCursor()
  }
}

onMounted(() => void load())

/* ── 筛选与游标 ── */

const kindFilter = ref<QueueKind | 'ALL'>('ALL')

const view = computed<ReviewTaskVO[]>(() =>
  kindFilter.value === 'ALL' ? rows.value : rows.value.filter((t) => t.kind === kindFilter.value)
)

const kindCount = (kind: QueueKind): number => rows.value.filter((t) => t.kind === kind).length

/** 当前游标：连续审核靠它，处置完不动它即可自动落到下一条 */
const cursor = ref(0)
const current = computed<ReviewTaskVO | null>(() => view.value[cursor.value] ?? null)
const drawerOpen = ref(false)

function clampCursor(): void {
  if (cursor.value > view.value.length - 1) {
    cursor.value = Math.max(0, view.value.length - 1)
  }
}

const openAt = (index: number): void => {
  if (index < 0 || index >= view.value.length) return
  cursor.value = index
  resetForm()
  drawerOpen.value = true
}

const onNext = (): void => openAt(cursor.value + 1)

/* ── SLA ── */

/** 后端算好的等待小时数直接用，不做前后端时钟对齐 */
const levelOf = (task: ReviewTaskVO): SlaLevel =>
  sla.value ? slaLevel(task.waitHours, sla.value) : 'normal'
const levelMeta = (level: SlaLevel): { label: string; color: string } => SLA_LEVEL_LABELS[level]

/** 「3 小时」「2 天 6 小时」这种人读格式；后端只给小时数，不替前端定措辞 */
const humanizeWait = (hours: number): string => {
  if (hours < 1) return '不足 1 小时'
  if (hours < 24) return `${hours} 小时`
  const d = Math.floor(hours / 24)
  return hours % 24 === 0 ? `${d} 天` : `${d} 天 ${hours % 24} 小时`
}

/* ── 处置 ── */

const form = reactive<{ reason: OfflineReason | ''; note: string }>({ reason: '', note: '' })
const submitting = ref(false)

const resetForm = (): void => {
  form.reason = ''
  form.note = ''
}

/** 处置成功后把这条从队列里摘掉，游标不动 = 自动落到下一条 */
const removeCurrent = (task: ReviewTaskVO): void => {
  rows.value = rows.value.filter((t) => !(t.id === task.id && t.kind === task.kind))
  clampCursor()
  if (view.value.length === 0) {
    drawerOpen.value = false
    message.info('队列已清空')
  } else {
    drawerOpen.value = true
  }
}

/**
 * 统一出口：所有处置动作都走这里，避免六个按钮各写一遍 try/catch。
 *
 * <p>失败一律重拉队列：3001（已被别人处置）说明这条已经从队列出去了，
 * 留着它只会让审核员反复点同一个按钮；1106（商品状态不允许下架）则要让他看到最新的商品状态。
 */
const submit = async (action: () => Promise<void>, okText: string): Promise<void> => {
  const task = current.value
  if (!task || submitting.value) return
  submitting.value = true
  try {
    await action()
    message.success(okText)
    removeCurrent(task)
  } catch {
    // 请求层已提示（3001 / 3002 / 1106 / 1000 都在这条路径上）
    await load()
  } finally {
    submitting.value = false
  }
}

/** 需要先选原因的动作：驳回 / 强制下架 / 举报成立——都要处置商品，处置必须写依据 */
const requireReason = (): boolean => {
  if (!form.reason) {
    message.warning('请选择原因：处置依据会记入操作日志，卖家与举报人都要看')
    return false
  }
  return true
}

const noteOrUndefined = (): string | undefined => form.note.trim() || undefined

/* 商品审核（review.judge / review.forceoff） */
const onPass = (): Promise<void> => {
  const t = current.value
  return t ? submit(() => reviewApi.pass(t.id), `已通过：${t.itemTitle}`) : Promise.resolve()
}

const onReject = (): Promise<void> => {
  const t = current.value
  if (!t || !requireReason()) return Promise.resolve()
  return submit(
    () => reviewApi.reject(t.id, { reason: form.reason as string, note: noteOrUndefined() }),
    `已驳回：${t.itemTitle}`
  )
}

const onForceOff = (): Promise<void> => {
  const t = current.value
  if (!t || !requireReason()) return Promise.resolve()
  return submit(
    () => reviewApi.forceOffline(t.id, { reason: form.reason as string, note: noteOrUndefined() }),
    `已强制下架：${t.itemTitle}`
  )
}

/* 举报裁决（review.judge）：成立 → 商品强制下架；不成立 → 商品不动 */
const onReportUpheld = (): Promise<void> => {
  const t = current.value
  if (!t || !requireReason()) return Promise.resolve()
  return submit(
    () =>
      reviewApi.handleReport(t.id, {
        result: 1,
        reason: form.reason as string,
        note: noteOrUndefined()
      }),
    `举报成立，已下架：${t.itemTitle}`
  )
}

const onReportDismissed = (): Promise<void> => {
  const t = current.value
  return t
    ? submit(() => reviewApi.handleReport(t.id, { result: 2, note: noteOrUndefined() }), '举报不成立，商品未变动')
    : Promise.resolve()
}

/* 申诉复审（review.appeal）：撤销 = 按原处置反向恢复商品；维持 = 商品不动 */
const onAppealRevoke = (): Promise<void> => {
  const t = current.value
  return t
    ? submit(() => reviewApi.judgeAppeal(t.id, { result: 3, note: noteOrUndefined() }), '已撤销原判，商品已恢复')
    : Promise.resolve()
}

const onAppealKeep = (): Promise<void> => {
  const t = current.value
  return t
    ? submit(() => reviewApi.judgeAppeal(t.id, { result: 2, note: noteOrUndefined() }), '已维持原判')
    : Promise.resolve()
}

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

const columns: TableColumnsType<ReviewTaskVO> = [
  { title: '权重', key: 'weight', width: 90 },
  { title: '类型', key: 'kind', width: 100 },
  { title: '商品', key: 'item', width: 280 },
  { title: '价格', key: 'price', width: 140 },
  { title: '卖家信用', key: 'credit', width: 100 },
  { title: '举报 / 风险', key: 'risk', width: 110 },
  { title: '等待时长', key: 'wait', width: 140 },
  { title: '操作', key: 'action', width: 90 }
]

/** 跨来源的 id 不唯一，列表 key 必须带上类型 */
const rowKey = (record: ReviewTaskVO): string => `${record.kind}-${record.id}`

const kindLabel = (k: QueueKind): string => QUEUE_KIND_LABELS[k]

const kindColor = (k: QueueKind): string =>
  k === 'appeal' ? 'purple' : k === 'report' ? 'orange' : 'blue'

/**
 * 权重组成的可读解释：让「为什么它排第一」可追溯。
 *
 * <p>写「举报快照」而不是显示实时举报数：权重用的是进队快照（不会因为又来一条举报就跳位），
 * 表格里那一列才是实时值，两者可能不同。
 */
const weightDetail = (t: ReviewTaskVO): string =>
  [
    `举报快照 × 3 = ${t.parts.report}`,
    `风险分归一 = ${t.parts.risk}`,
    `信用扣减 = ${t.parts.credit}`,
    `价格异常 = ${t.parts.price}`,
    `等待补偿 = ${t.parts.wait}`
  ].join('；')

/** 金额是后端给的定长字符串，比较前显式转 number */
const priceAbnormal = (t: ReviewTaskVO): boolean =>
  isPriceAbnormal({ price: Number(t.itemPrice), categoryAvgPrice: Number(t.categoryAvgPrice) })

/**
 * 主图加载失败的 key 集合。演示数据里的图片是 `cdn.campus.local` 外链，
 * 本地解析不到，不降级会满屏碎图标（与商品页同一处理）。
 */
const thumbFailed = reactive<Record<string, boolean>>({})
const imageKey = (scope: string, id: string): string => `${scope}-${id}`

/** 全部商品图都加载失败时给出占位文案，否则 Drawer 里只剩一块空白 */
const visibleDrawerImages = computed(() =>
  (current.value?.images ?? []).filter((img) => !thumbFailed[imageKey('drawer', img.id)])
)
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
        <a-button shape="round" :loading="loading" @click="load">刷新队列</a-button>
      </a-space>
    </header>

    <!-- SLA 概览：business/03 F-A9 的两级指标，这里呈现「响应时效」那一级；数字与阈值都来自后端 -->
    <div class="cm-card review__sla">
      <div class="review__sla-item">
        <span class="review__sla-num">{{ sla?.pending ?? 0 }}</span>
        <span class="review__sla-label">待处置</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num review__sla-num--due">{{ sla?.due ?? 0 }}</span>
        <span class="review__sla-label">临期（≥{{ sla?.firstResponseHours ?? 0 }}h）</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num review__sla-num--over">{{ sla?.overdue ?? 0 }}</span>
        <span class="review__sla-label">积压红牌（≥{{ sla?.backlogHours ?? 0 }}h）</span>
      </div>
      <div class="review__sla-sep" />
      <div class="review__sla-item">
        <span class="review__sla-num">{{ sla?.avgHandleSeconds ?? 0 }}s</span>
        <span class="review__sla-label">单条处置目标</span>
      </div>
    </div>

    <div class="cm-card review__tabs">
      <a-space :size="8">
        <a-button
          :type="kindFilter === 'ALL' ? 'primary' : 'default'"
          shape="round"
          size="small"
          @click="kindFilter = 'ALL'"
        >
          全部 {{ rows.length }}
        </a-button>
        <a-button
          :type="kindFilter === 'appeal' ? 'primary' : 'default'"
          shape="round"
          size="small"
          @click="kindFilter = 'appeal'"
        >
          申诉复审 {{ kindCount('appeal') }}
        </a-button>
        <a-button
          :type="kindFilter === 'report' ? 'primary' : 'default'"
          shape="round"
          size="small"
          @click="kindFilter = 'report'"
        >
          举报处理 {{ kindCount('report') }}
        </a-button>
        <a-button
          :type="kindFilter === 'item' ? 'primary' : 'default'"
          shape="round"
          size="small"
          @click="kindFilter = 'item'"
        >
          商品审核 {{ kindCount('item') }}
        </a-button>
      </a-space>
    </div>

    <a-table
      :columns="columns"
      :data-source="view"
      :row-key="rowKey"
      :loading="loading"
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
          <a-tag :color="kindColor(record.kind)">{{ kindLabel(record.kind) }}</a-tag>
        </template>

        <template v-else-if="column.key === 'item'">
          <div class="review__cell">
            <span class="review__thumb">
              <img
                v-if="record.mainImage && !thumbFailed[imageKey('list', record.id)]"
                :src="record.mainImage"
                alt=""
                @error="thumbFailed[imageKey('list', record.id)] = true"
              />
            </span>
            <span class="review__title">
              {{ record.itemTitle }}
              <span class="review__muted">{{ record.categoryName }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'price'">
          <span :class="{ 'review__abnormal': priceAbnormal(record) }">¥{{ record.itemPrice }}</span>
          <span class="review__muted">/ 均价 ¥{{ record.categoryAvgPrice }}</span>
        </template>

        <template v-else-if="column.key === 'credit'">
          <span :class="{ 'review__low': (record.sellerCredit ?? 0) < 60 }">
            {{ record.sellerCredit }}
          </span>
        </template>

        <template v-else-if="column.key === 'risk'">
          {{ record.reportCount }} / {{ record.riskScore }}
        </template>

        <template v-else-if="column.key === 'wait'">
          <a-tag :color="levelMeta(levelOf(record)).color">
            {{ levelMeta(levelOf(record)).label }}
          </a-tag>
          <span class="review__muted">{{ humanizeWait(record.waitHours) }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-button type="link" size="small" @click="openAt(view.indexOf(record))">处置</a-button>
        </template>
      </template>
    </a-table>

    <!-- 处置抽屉：不跳页，处置完自动进下一条 -->
    <a-drawer v-model:open="drawerOpen" :width="580" title="审核处置">
      <template v-if="current">
        <div class="review__head-row">
          <a-tag :color="kindColor(current.kind)">{{ kindLabel(current.kind) }}</a-tag>
          <span class="review__muted">权重 {{ current.weight }} · {{ weightDetail(current) }}</span>
        </div>

        <!-- 换人复审提示（F-A4） -->
        <p v-if="current.kind === 'appeal'" class="review__notice">
          原处置人：<b>{{ current.handledByName || current.handledBy || '—' }}</b>（{{ current.handledResult || '—' }}）。
          规则要求原处置人不得复审自己的单，后端会拦（3002）；单人演示时此处仅作提示。
        </p>
        <p v-if="current.reason" class="review__notice">发起方理由：{{ current.reason }}</p>
        <!-- 凭证是原始 JSON（结构由 C 端表单决定，后端原样透传），这里只做展示不做解析 -->
        <p v-if="current.evidence" class="review__notice">凭证：{{ current.evidence }}</p>

        <!-- 判读四项：图片 / 标题描述 / 价格 / 分类成色 -->
        <div class="review__gallery">
          <img
            v-for="img in visibleDrawerImages"
            :key="img.id"
            :src="img.url"
            alt=""
            @error="thumbFailed[imageKey('drawer', img.id)] = true"
          />
          <span v-if="visibleDrawerImages.length === 0" class="review__muted">
            （商品图加载失败或暂无图片 · 审核要点：是否网图、是否含联系方式或二维码）
          </span>
        </div>

        <h2 class="review__title-lg">{{ current.itemTitle }}</h2>

        <a-descriptions :column="2" bordered size="small" class="review__desc">
          <a-descriptions-item label="售价">
            ¥{{ current.itemPrice }}
            <span v-if="priceAbnormal(current)" class="review__abnormal">异常</span>
          </a-descriptions-item>
          <a-descriptions-item label="同类目均价">¥{{ current.categoryAvgPrice }}</a-descriptions-item>
          <a-descriptions-item label="分类">{{ current.categoryName ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="商品状态">
            {{ ITEM_STATUS_LABELS[current.itemStatus] }}
          </a-descriptions-item>
          <a-descriptions-item label="卖家">{{ current.sellerNick ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="卖家信用">
            <span :class="{ 'review__low': (current.sellerCredit ?? 0) < 60 }">
              {{ current.sellerCredit }}
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="举报次数（实时）">{{ current.reportCount }}</a-descriptions-item>
          <a-descriptions-item label="风险分（快照）">{{ current.riskScore }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="review__sub">商品描述</h3>
        <p class="review__text">{{ current.description ?? '—' }}</p>

        <h3 class="review__sub">处置</h3>
        <a-form layout="vertical">
          <a-form-item label="原因（驳回 / 强制下架 / 举报成立必填）">
            <a-select v-model:value="form.reason" placeholder="选择预设原因">
              <a-select-option v-for="r in OFFLINE_REASONS" :key="r" :value="r">{{ r }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="补充说明">
            <a-textarea
              v-model:value="form.note"
              :rows="2"
              placeholder="处置依据（选填，会记入单据备注）"
            />
          </a-form-item>
        </a-form>

        <!-- 三个来源的处置动作不同，按钮按 kind 渲染，权限也各挂各的 -->
        <div class="review__actions">
          <a-space wrap>
            <template v-if="current.kind === 'item'">
              <a-button
                v-perm="PERM_CODES.REVIEW_JUDGE"
                type="primary"
                shape="round"
                :loading="submitting"
                @click="onPass"
              >
                通过
              </a-button>
              <a-button
                v-perm="PERM_CODES.REVIEW_JUDGE"
                shape="round"
                :loading="submitting"
                @click="onReject"
              >
                驳回
              </a-button>
              <a-button
                v-perm="PERM_CODES.REVIEW_FORCE_OFF"
                danger
                shape="round"
                :loading="submitting"
                @click="onForceOff"
              >
                强制下架
              </a-button>
            </template>

            <template v-else-if="current.kind === 'report'">
              <a-button
                v-perm="PERM_CODES.REVIEW_JUDGE"
                type="primary"
                shape="round"
                :loading="submitting"
                @click="onReportUpheld"
              >
                举报成立（下架商品）
              </a-button>
              <a-button
                v-perm="PERM_CODES.REVIEW_JUDGE"
                shape="round"
                :loading="submitting"
                @click="onReportDismissed"
              >
                举报不成立
              </a-button>
            </template>

            <template v-else>
              <a-button
                v-perm="PERM_CODES.REVIEW_APPEAL"
                type="primary"
                shape="round"
                :loading="submitting"
                @click="onAppealRevoke"
              >
                撤销原判（恢复商品）
              </a-button>
              <a-button
                v-perm="PERM_CODES.REVIEW_APPEAL"
                shape="round"
                :loading="submitting"
                @click="onAppealKeep"
              >
                维持原判
              </a-button>
            </template>

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
  background: var(--cm-color-surface-muted);
  overflow: hidden;
}

/* 演示数据的图片是外链，加载失败时 thumbFailed 会把 img 摘掉，露出上面的底色块 */
.review__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  word-break: break-all;
}

.review__gallery {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--cm-space-2);
  height: 200px;
  padding: 0 var(--cm-space-3);
  border-radius: var(--cm-radius-lg);
  background: var(--cm-color-surface-muted);
  overflow-x: auto;
  margin-bottom: var(--cm-space-4);
}

.review__gallery img {
  height: 176px;
  flex: none;
  object-fit: cover;
  border-radius: var(--cm-radius-md);
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
