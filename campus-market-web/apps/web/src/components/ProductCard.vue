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
 *
 * <p>2026-09-30 按闲鱼卡片改版：
 * - 卡片**去描边**，白卡直接落在灰底上（描边是旧版"表格感"的来源）；
 * - 标题行内嵌交易标签 chips（包邮 / 可议价），闲鱼把卖点前置到标题第一眼；
 * - 价格改成「¥ 小号 + 整数大号 + 小数小号」的三段式，右侧跟"N 人想要"；
 * - 底部卖家行右侧放信用标签（极好=黄 chip / 已认证=绿 chip）。
 */

import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatRelativeTime, type GoodsItem } from '@campus/shared'
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

/**
 * 主图优先用真实 URL（item.images[0]），发布页实时预览（previewSrc）次之，都没有才落 tints 占位。
 * 第二张图只给 hover 交叉淡入用，**没有就不渲染**——见模板里的说明。
 */
const mainImg = computed(() => props.previewSrc || props.item.images?.[0] || '')
const secondImg = computed(() => props.item.images?.[1] || '')

/** 占位图渐变：真实数据没有图时（骨架期 / 外链失效）兜一个成色色块 */
const bg = (tints: [string, string]): string => `linear-gradient(135deg, ${tints[0]}, ${tints[1]})`

const selected = computed(() => compare.has(props.item.id))

/** 对比篮已有别的分类时，本卡禁用「＋对比」——引导用户只挑同类 */
const mismatch = computed(
  () => compare.count > 0 && !selected.value && props.item.categoryId !== compare.lockedCategoryId
)
const timeText = computed(() => formatRelativeTime(props.item.publishedAt))

/**
 * 闲鱼式三段价格：¥ 与小数缩小、整数放大。
 * 不直接用 formatPrice——那个是"一整串"的口径，详情页还在用。
 */
const priceInt = computed(() => Math.floor(props.item.price))
const priceDec = computed(() => {
  const dec = Math.round((props.item.price - Math.floor(props.item.price)) * 100)
  return dec > 0 ? String(dec).padStart(2, '0') : ''
})

function goDetail(): void {
  void router.push({ name: 'item', params: { id: props.item.id } })
}

/**
 * 「＋对比」必须 stopPropagation（design/01 §七：卡片整体可点，但按钮要独立）。
 * 满了 / 跨分类都给明确提示，不静默失败。
 */
function onCompare(e: MouseEvent): void {
  e.stopPropagation()
  const r = compare.toggle(props.item)
  if (r === 'full') ElMessage.warning({ message: '最多对比 4 件', duration: 2000 })
  else if (r === 'mismatch')
    ElMessage.warning({
      message: `对比仅限同类商品（${compare.lockedCategoryName}），已帮你锁定分类`,
      duration: 2200
    })
}
</script>

<template>
  <article class="card" :class="`card--${mode}`" @click="goDetail">
    <div class="card__pic">
      <div
        class="pic pic--a"
        :style="mainImg ? { backgroundImage: `url(${mainImg})` } : { background: bg(item.tints) }"
      />
      <!--
        交叉淡入只在真有第二张图时挂载。**不能给它兜占位色**：
        单图商品（真实数据绝大多数如此）hover 时那张纯色块会淡入到 100%
        并把主图整个盖住，看起来就是「悬停后图没了」。
      -->
      <div v-if="!previewSrc && secondImg" class="pic pic--b" :style="{ backgroundImage: `url(${secondImg})` }" />

      <!-- 角标：急出用价格色底，其余半透黑底（design/01 §4.1） -->
      <span v-if="item.badge === '急出'" class="card__badge card__badge--hot">急出</span>

      <button
        class="card__cmp"
        :class="{ 'card__cmp--on': selected, 'card__cmp--lock': mismatch }"
        type="button"
        :disabled="mismatch"
        :title="mismatch ? `对比仅限同类（${compare.lockedCategoryName}）` : ''"
        :aria-label="selected ? '取消对比' : '加入对比'"
        @click="onCompare"
      >
        {{ selected ? '✓ 已加入' : mismatch ? '仅限同类' : '＋对比' }}
      </button>
    </div>

    <div class="card__body">
      <h3 class="card__title">
        <span v-if="item.delivery" class="cm-tag cm-tag--red">包邮</span>
        <span v-if="item.deliveryType === 2" class="cm-tag cm-tag--blue">自提</span>
        <span v-if="item.negotiable" class="cm-tag cm-tag--yellow">可议价</span>
        <span class="card__title-text cm-clamp-2">{{ item.title }}</span>
      </h3>

      <div class="card__price">
        <span class="yen">¥</span>
        <strong>{{ priceInt }}</strong>
        <span v-if="priceDec" class="dec">.{{ priceDec }}</span>
        <s v-if="item.originalPrice">{{ item.originalPrice }}元</s>
        <!-- wantCount 为 0 时隐藏（真实数据不下发收藏数），不显示「0 人想要」 -->
        <span v-if="item.wantCount > 0" class="want">{{ item.wantCount }}人想要</span>
      </div>

      <div class="card__meta">
        <ConditionBadge :grade="item.condition" />
        <span class="meta-txt">{{ item.campus }} · {{ item.meetPoint }}</span>
        <span class="meta-time">{{ timeText }}</span>
      </div>

      <footer class="card__seller">
        <span class="avatar" :style="{ background: bg(item.seller.tints) }" />
        <span class="nick">{{ item.seller.nick }}</span>
        <span v-if="item.seller.credit === '极好'" class="cm-tag cm-tag--yellow credit">信用极好</span>
        <span v-else-if="item.seller.verified" class="cm-tag cm-tag--green credit">学号已认证</span>
      </footer>
    </div>
  </article>
</template>

<style scoped lang="scss">
.card {
  background: var(--cm-color-surface);
  /* 闲鱼口径：白卡无描边，靠灰底分层 + hover 才浮阴影 */
  border-radius: var(--cm-radius-xl);
  overflow: hidden;
  cursor: pointer;
  /* design/01 §六：180ms ease-out，位移 3px（禁止超过 4px） */
  transition: transform 180ms ease-out, box-shadow 180ms ease-out;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: var(--cm-shadow-2);
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
  border-radius: var(--cm-radius-pill);
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

/* 对比篮已锁定其他分类：置灰禁用，文字换成「仅限同类」 */
.card__cmp--lock {
  opacity: 1;
  color: var(--cm-color-text-3);
  background: var(--cm-color-bg);
  border-color: var(--cm-color-border);
  cursor: not-allowed;
}

/* ── 文字区 ─────────────────────────────────────────────── */

.card__body {
  padding: var(--cm-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

/* 标题行：chips 前置 + 文本两行截断（chips 占位时文本实际是一到两行） */
.card__title {
  margin: 0;
  min-height: 40px;
  font-size: var(--cm-font-size-base);
  font-weight: 400;
  line-height: 1.45;
  color: var(--cm-color-text-1);
}

.card__title .cm-tag {
  margin-right: 4px;
}

/* chips 是 inline-flex，clamp 只作用于文本段 */
.card__title-text {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  vertical-align: bottom;
}

/* ── 三段式价格 ─────────────────────────────────────────── */

.card__price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.yen,
.dec {
  font-size: var(--cm-font-size-sm);
  font-weight: 800;
  color: var(--cm-color-price);
}

.card__price strong {
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
  line-height: 1.1;
  color: var(--cm-color-price);
}

.card__price s {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  font-weight: 400;
  color: var(--cm-color-text-3);
}

.want {
  margin-left: auto;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 元信息 / 卖家行 ────────────────────────────────────── */

.card__meta {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.meta-txt {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.meta-time {
  margin-left: auto;
  flex: none;
}

.card__seller {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  flex: none;
}

.nick {
  color: var(--cm-color-text-2);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.credit {
  margin-left: auto;
  flex: none;
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
  min-height: 0;
  font-size: var(--cm-font-size-md);
}
</style>
