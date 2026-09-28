<script setup lang="ts">
/**
 * 商品卡片（本端最重要组件，design/01 §4.1）
 *
 * <p>网格卡与列表卡做在一个组件里而不是拆两个：两者结构完全相同（图 / 标题 /
 * 价格 / 元信息 / 卖家），只是排列方向不同，拆开会让「改一处漏一处」。
 * 列表视图是桌面独占的第二种视图（business/01 §五）。
 *
 * <p>hover 行为全靠 CSS：浮起 3px（180ms）+ 第二张图交叉淡入（250ms）+ 「＋对比」浮出。
 * 不要在 JS 里管 hover 状态——那样每一张卡都要挂监听，60 张卡时会有明显开销。
 */

import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatPrice, formatRelativeTime, type GoodsItem } from '@campus/shared'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'
import ConditionBadge from './ConditionBadge.vue'

const props = withDefaults(
  defineProps<{
    item: GoodsItem
    /** grid = 网格视图（默认）；list = 列表视图，横向铺开 */
    mode?: 'grid' | 'list'
    /** 发布页实时预览用：直接给一张本地图 URL，覆盖占位渐变 */
    previewSrc?: string
  }>(),
  { mode: 'grid' }
)

const router = useRouter()
const compare = useCompareStore()

/** 占位图渐变：第二张把两色反过来并压暗一档，hover 切换时才看得出变化 */
const bg = (tints: [string, string], flip = false): string => {
  const [a, b] = flip ? [tints[1], tints[0]] : tints
  return `linear-gradient(135deg, ${a}, ${b})`
}

const selected = computed(() => compare.has(props.item.id))
const priceText = computed(() => formatPrice(props.item.price))
const timeText = computed(() => formatRelativeTime(props.item.publishedAt))

function goDetail(): void {
  void router.push({ name: 'item', params: { id: props.item.id } })
}

/**
 * 「＋对比」必须 stopPropagation（design/01 §七：卡片整体可点，但按钮要独立）。
 * 满了给提示，不静默失败。
 */
function onCompare(e: MouseEvent): void {
  e.stopPropagation()
  const ok = compare.toggle(props.item)
  if (!ok) ElMessage.warning({ message: `最多对比 4 件`, duration: 2000 })
}
</script>

<template>
  <article class="card" :class="`card--${mode}`" @click="goDetail">
    <div class="card__pic">
      <div
        class="pic pic--a"
        :style="previewSrc ? { backgroundImage: `url(${previewSrc})` } : { background: bg(item.tints) }"
      />
      <div v-if="!previewSrc" class="pic pic--b" :style="{ background: bg(item.tints, true) }" />

      <!-- 角标：急出用价格色底，其余半透黑底（design/01 §4.1） -->
      <span v-if="item.badge" class="card__badge" :class="{ 'card__badge--hot': item.badge === '急出' }">
        {{ item.badge }}
      </span>

      <button
        class="card__cmp"
        :class="{ 'card__cmp--on': selected }"
        type="button"
        :aria-label="selected ? '取消对比' : '加入对比'"
        @click="onCompare"
      >
        {{ selected ? '✓ 已加入' : '＋对比' }}
      </button>
    </div>

    <div class="card__body">
      <h3 class="card__title cm-clamp-2">{{ item.title }}</h3>

      <div class="card__price">
        <strong>{{ priceText }}</strong>
        <s v-if="item.originalPrice">{{ formatPrice(item.originalPrice) }}</s>
      </div>

      <div class="card__meta">
        <ConditionBadge :grade="item.condition" />
        <span>{{ item.campus }} · {{ item.meetPoint }}</span>
      </div>

      <footer class="card__seller">
        <span class="avatar" :style="{ background: bg(item.seller.tints) }" />
        <span class="nick">{{ item.seller.nick }}</span>
        <span v-if="item.seller.verified" class="verified">学号已认证</span>
        <span class="time">{{ timeText }}</span>
      </footer>
    </div>
  </article>
</template>

<style scoped lang="scss">
.card {
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  overflow: hidden;
  cursor: pointer;
  /* design/01 §六：180ms ease-out，位移 3px（禁止超过 4px） */
  transition: transform 180ms ease-out, box-shadow 180ms ease-out, border-color 180ms ease-out;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: var(--cm-shadow-2);
  border-color: var(--cm-color-border-strong);
}

/* ── 图片区 4:3 ─────────────────────────────────────────── */

.card__pic {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
}

.pic {
  position: absolute;
  inset: 0;
  background-size: cover;
}

/* 第二张图交叉淡入 250ms，不做滑动（design/01 §六） */
.pic--b {
  opacity: 0;
  transition: opacity 250ms ease;
}

.card:hover .pic--b {
  opacity: 1;
}

.card__badge {
  position: absolute;
  left: var(--cm-space-2);
  top: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  font-weight: 700;
  color: #fff;
  padding: 1px 6px;
  border-radius: var(--cm-radius-sm);
  background: rgba(31, 31, 38, 0.72);
}

.card__badge--hot {
  background: var(--cm-color-price);
}

/* 「＋对比」默认透明，hover 卡片才浮出；hover 自身转黄底 */
.card__cmp {
  position: absolute;
  right: var(--cm-space-2);
  top: var(--cm-space-2);
  height: 24px;
  padding: 0 var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  font-weight: 600;
  color: var(--cm-color-text-1);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border-strong);
  border-radius: var(--cm-radius-sm);
  cursor: pointer;
  opacity: 0;
  transition: opacity 180ms ease-out, background-color 180ms ease-out;
}

.card:hover .card__cmp {
  opacity: 1;
}

.card__cmp:hover {
  background: var(--cm-color-brand);
}

.card__cmp--on {
  opacity: 1;
  background: var(--cm-color-brand-bg);
  border-color: var(--cm-color-brand-border);
}

/* ── 文字区 ─────────────────────────────────────────────── */

.card__body {
  padding: var(--cm-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.card__title {
  margin: 0;
  font-size: var(--cm-font-size-base);
  font-weight: 400;
  line-height: 1.45;
  /* design/01 §4.1：标题固定高 40px，两行截断 */
  height: 40px;
  color: var(--cm-color-text-1);
}

.card__price {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
}

.card__price strong {
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
  line-height: 1.1;
  color: var(--cm-color-price);
}

.card__price s {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__meta {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__seller {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  padding-top: var(--cm-space-2);
  border-top: 1px solid var(--cm-color-border);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.avatar {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  flex: none;
}

.nick {
  color: var(--cm-color-text-2);
}

.verified {
  color: var(--cm-color-ok);
}

.time {
  margin-left: auto;
}

/* ── 列表视图：图在左，文字在右 ─────────────────────────── */

.card--list {
  display: flex;
  align-items: stretch;
}

.card--list .card__pic {
  width: 200px;
  aspect-ratio: auto;
  flex: none;
}

.card--list .card__body {
  flex: 1;
  padding: var(--cm-space-4);
  gap: var(--cm-space-3);
}

.card--list .card__title {
  height: auto;
  font-size: var(--cm-font-size-md);
}
</style>
