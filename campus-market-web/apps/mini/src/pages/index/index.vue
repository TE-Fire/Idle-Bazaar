<script setup lang="ts">
/**
 * M1 首页信息流（design/02 §4.1，本端最重要的一页）
 *
 * <p>原则 1「逛得下去」：用户第 3 秒内要看到至少 4 件商品，不能先看到搜索框和分类。
 * 所以瀑布流一上来就有数据，金刚区只占一屏的头部，不做开屏弹窗。
 *
 * <p>瀑布流双列：左右两列**不等高是允许的**，但同一列内必须对齐——所以按索引奇偶
 * 静态分列（而不是按高度动态插入），否则上拉加载时已有卡片会跳列。
 *
 * <p>数据来自 `/api/item/list`（免登录，在售按发布时间倒序）。服务端分页 + 上拉加载；
 * 后端列表只支持 keyword/categoryId/campusId，价格与成色筛选未参数化之前不做（web 端同边界）。
 */

import { computed, ref } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { CATEGORIES, type Category, type GoodsItem } from '@campus/shared'
import { userItemApi } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import GoodsCard from '@/components/GoodsCard.vue'

/** 频道 chips：原型给的兴趣频道，前端本地过滤标题（服务端无此维度） */
const CHANNELS = ['猜你喜欢', '教材专区', '数码好价', '毕业清仓', '同城面交', '女生闲置']

/** 金刚区分类入口：图标**不得**各用一色（design/02 原则 5），统一单色 + 橙点缀 */
const categories = CATEGORIES.filter((c) => c !== '首页') as Category[]

const channel = ref(CHANNELS[0])
const pageSize = 10
const page = ref(1)
const total = ref(0)
const items = ref<GoodsItem[]>([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)

const hasMore = computed(() => items.value.length < total.value)

/** 频道是前端本地过滤（标题关键词），命中为空时回落全量提示 */
const filtered = computed(() => {
  if (channel.value === CHANNELS[0] || !CHANNELS.includes(channel.value)) return items.value
  const kw = channel.value.slice(0, 2)
  return items.value.filter((it) => `${it.title}${it.description}`.includes(kw))
})

/** 双列分列：奇偶切分，保证上拉加载时不重排 */
const leftColumn = computed(() => filtered.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => filtered.value.filter((_, i) => i % 2 === 1))

async function fetchPage(reset: boolean): Promise<void> {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  loadError.value = false
  const p = reset ? 1 : page.value + 1
  try {
    const res = await userItemApi.list({ pageNum: p, pageSize })
    const mapped = res.list.map(toGoodsItem)
    if (reset) items.value = mapped
    else items.value.push(...mapped)
    page.value = p
    total.value = res.total
    loaded.value = true
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onReachBottom(() => {
  void fetchPage(false)
})

onPullDownRefresh(() => {
  void fetchPage(true).then(() => uni.stopPullDownRefresh())
})

function openSearch(): void {
  uni.showToast({ title: '搜索页待建（M7）', icon: 'none' })
}

/** 顶栏「消息」进会话列表（tabBar 页用 switchTab） */
function openChat(): void {
  uni.switchTab({ url: '/pages/chat/list' })
}

function goPublish(): void {
  // 发布是 tabBar 页，必须用 switchTab
  uni.switchTab({ url: '/pages/publish/index' })
}

void fetchPage(true)
</script>

<template>
  <view class="page">
    <!-- 吸顶搜索栏 -->
    <view class="searchbar">
      <view class="searchbar__box" hover-class="searchbar__box--active" @click="openSearch">
        <view class="searchbar__icon" />
        <text class="searchbar__ph">搜闲置、教材、数码…</text>
      </view>
      <view class="searchbar__msg" hover-class="searchbar__msg--active" @click="openChat">
        <text>消息</text>
      </view>
    </view>

    <!-- 金刚区：两行 × 5，横向可滑 -->
    <scroll-view class="kingkong" scroll-x>
      <view class="kingkong__in">
        <view v-for="c in categories" :key="c" class="kk">
          <view class="kk__icon"><text class="kk__char">{{ c[0] }}</text></view>
          <text class="kk__label">{{ c }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 兴趣频道 chips：横向滑动，不吸顶 -->
    <scroll-view class="chips" scroll-x>
      <view class="chips__in">
        <text
          v-for="ch in CHANNELS"
          :key="ch"
          class="chip"
          :class="{ 'chip--on': channel === ch }"
          @click="channel = ch"
        >
          {{ ch }}
        </text>
      </view>
    </scroll-view>

    <!-- 双列瀑布流 -->
    <view v-if="!loaded && loading" class="falls">
      <view class="col">
        <view v-for="n in 3" :key="'ls' + n" class="skel" />
      </view>
      <view class="col">
        <view v-for="n in 3" :key="'rs' + n" class="skel" />
      </view>
    </view>

    <view v-else-if="loadError" class="state">
      <text class="state__t">加载失败，拉一下试试</text>
    </view>

    <view v-else-if="!filtered.length" class="state">
      <text class="state__t">{{ channel === CHANNELS[0] ? '暂时没有在售商品' : '这个频道还没有商品' }}</text>
    </view>

    <view v-else class="falls">
      <view class="col">
        <GoodsCard v-for="it in leftColumn" :key="it.id" :item="it" />
      </view>
      <view class="col">
        <GoodsCard v-for="it in rightColumn" :key="it.id" :item="it" />
      </view>
    </view>

    <view class="loadmore">
      <text>{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</text>
    </view>

    <!-- 悬浮发布按钮：56pt=112rpx，右下，避开安全区 -->
    <view class="fab" hover-class="fab--active" @click="goPublish">⊕</view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: 120rpx;
}

/* ── 吸顶搜索 ───────────────────────────────────────────── */

.searchbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-2 $cm-space-4;
  background: $cm-color-surface;
}

.searchbar__box {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
  background: $cm-color-bg;
  border-radius: $cm-radius-pill;
  transition: transform 120ms ease-out;
}

.searchbar__box--active {
  transform: scale(0.99);
}

/* 放大镜图标：纯 CSS 两笔画（圆 + 斜柄），不引图标库 */
.searchbar__icon {
  width: 26rpx;
  height: 26rpx;
  border: 3rpx solid $cm-color-text-3;
  border-radius: 50%;
  position: relative;
  flex: none;
}

.searchbar__icon::after {
  content: '';
  position: absolute;
  right: -10rpx;
  bottom: -6rpx;
  width: 12rpx;
  height: 3rpx;
  background: $cm-color-text-3;
  border-radius: 2rpx;
  transform: rotate(45deg);
}

.searchbar__ph {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.searchbar__msg {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  padding: $cm-space-1 $cm-space-2;
}

.searchbar__msg--active {
  opacity: 0.6;
}

/* ── 金刚区 ─────────────────────────────────────────────── */

.kingkong {
  background: $cm-color-surface;
  padding: $cm-space-4 0 $cm-space-5;
  white-space: nowrap;
}

.kingkong__in {
  display: inline-flex;
  flex-wrap: wrap;
  width: 750rpx;
  padding: 0 $cm-space-4;
}

.kk {
  width: 20%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $cm-space-2;
  margin-bottom: $cm-space-3;
}

/* 图标单色（灰底 + 深字），只有选中/主行动才用橙 */
.kk__icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: $cm-radius-lg;
  background: $cm-color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 120ms ease-out;
}

.kk:active .kk__icon {
  transform: scale(0.94);
}

.kk__char {
  font-size: $cm-font-size-lg;
  font-weight: 600;
  color: $cm-color-text-2;
}

.kk__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

/* ── chips ──────────────────────────────────────────────── */

.chips {
  padding: $cm-space-3 0;
  background: $cm-color-bg;
  white-space: nowrap;
}

.chips__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.chip {
  padding: 0 $cm-space-4;
  height: 56rpx;
  line-height: 56rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
}

.chip--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border: 1rpx solid $cm-color-brand;
  font-weight: 600;
}

/* ── 瀑布流 ─────────────────────────────────────────────── */

.falls {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3 $cm-space-4;
  align-items: flex-start;
}

.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

/* 骨架卡：图片区 + 两行文字的占位块 */
.skel {
  height: 480rpx;
  border-radius: $cm-radius-xl;
  background: linear-gradient(90deg, $cm-color-bg 25%, #fff 50%, $cm-color-bg 75%);
  background-size: 200% 100%;
  animation: skel 1.2s ease-in-out infinite;
}

@keyframes skel {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}

.state {
  padding: 120rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.loadmore {
  padding: $cm-space-5 0;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 悬浮发布按钮 ───────────────────────────────────────── */

.fab {
  position: fixed;
  right: $cm-space-5;
  /* 20pt + 安全区（design/02 §5.5） */
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: $cm-color-brand;
  color: #fff;
  font-size: 56rpx;
  line-height: 108rpx;
  text-align: center;
  /* 不用阴影：小程序语义层没有 shadow 令牌（tech/02 §5「小程序不用阴影」），
     悬浮感靠圆形 + 主色 + 与内容拉开间距来做 */
  transition: transform 120ms ease-out;
}

.fab--active {
  transform: scale(0.97);
}
</style>
