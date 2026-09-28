<script setup lang="ts">
/**
 * M1 首页信息流（design/02 §4.1，本端最重要的一页）
 *
 * <p>原则 1「逛得下去」：用户第 3 秒内要看到至少 4 件商品，不能先看到搜索框和分类。
 * 所以瀑布流一上来就有数据（骨架期是 mock），金刚区只占一屏的头部，不做开屏弹窗。
 *
 * <p>瀑布流双列：左右两列**不等高是允许的**，但同一列内必须对齐——所以按索引奇偶
 * 静态分列（而不是按高度动态插入），否则上拉加载时已有卡片会跳列。
 */

import { computed, ref } from 'vue'
import { onReachBottom, onPullDownRefresh } from '@dcloudio/uni-app'
import { CATEGORIES, type Category } from '@campus/shared'
import { mockItems } from '@campus/mock'
import GoodsCard from '@/components/GoodsCard.vue'

/** 频道 chips：原型给的兴趣频道，骨架期只做展示 */
const CHANNELS = ['猜你喜欢', '教材专区', '数码好价', '毕业清仓', '同城面交', '女生闲置']

/** 金刚区分类入口：图标**不得**各用一色（design/02 原则 5），统一单色 + 橙点缀 */
const categories = CATEGORIES.filter((c) => c !== '首页') as Category[]

const channel = ref(CHANNELS[0])
const pageSize = 8
const count = ref(pageSize)

/** 双列分列：奇偶切分，保证上拉加载时不重排 */
const leftColumn = computed(() => mockItems.slice(0, count.value).filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => mockItems.slice(0, count.value).filter((_, i) => i % 2 === 1))
const hasMore = computed(() => count.value < mockItems.length)

onReachBottom(() => {
  if (!hasMore.value) return
  count.value = Math.min(count.value + pageSize, mockItems.length)
})

onPullDownRefresh(() => {
  count.value = pageSize
  setTimeout(() => uni.stopPullDownRefresh(), 400)
})

function openSearch(): void {
  uni.showToast({ title: '搜索页待建（M7）', icon: 'none' })
}

function goPublish(): void {
  // 发布是 tabBar 页，必须用 switchTab
  uni.switchTab({ url: '/pages/publish/index' })
}
</script>

<template>
  <view class="page">
    <!-- 吸顶搜索栏 -->
    <view class="searchbar">
      <view class="searchbar__box" @click="openSearch">
        <text class="searchbar__ph">搜闲置、教材、数码…</text>
      </view>
      <text class="searchbar__msg">消息</text>
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
    <view class="falls">
      <view class="col">
        <GoodsCard v-for="it in leftColumn" :key="it.id" :item="it" />
      </view>
      <view class="col">
        <GoodsCard v-for="it in rightColumn" :key="it.id" :item="it" />
      </view>
    </view>

    <view class="loadmore">
      <text>{{ hasMore ? '正在加载…' : '没有更多了' }}</text>
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
  padding: $cm-space-2 $cm-space-3;
  background: $cm-color-surface;
  border-bottom: 1rpx solid $cm-color-border;
}

.searchbar__box {
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  padding: 0 $cm-space-4;
  background: $cm-color-bg;
  border-radius: 32rpx;
}

.searchbar__ph {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.searchbar__msg {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

/* ── 金刚区 ─────────────────────────────────────────────── */

.kingkong {
  background: $cm-color-surface;
  padding: $cm-space-3 0;
  white-space: nowrap;
}

.kingkong__in {
  display: inline-flex;
  flex-wrap: wrap;
  width: 750rpx;
  padding: 0 $cm-space-3;
}

.kk {
  width: 20%;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: $cm-space-3;
}

/* 图标单色（灰底 + 深字），只有选中/主行动才用橙 */
.kk__icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: $cm-radius-lg;
  background: $cm-color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.kk__char {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-text-2;
}

.kk__label {
  margin-top: $cm-space-1;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

/* ── chips ──────────────────────────────────────────────── */

.chips {
  padding: $cm-space-2 0;
  background: $cm-color-surface;
  white-space: nowrap;
}

.chips__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 0 $cm-space-3;
}

.chip {
  padding: 0 $cm-space-3;
  height: 48rpx;
  line-height: 48rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border-radius: 24rpx;
}

.chip--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border: 1rpx solid $cm-color-brand;
}

/* ── 瀑布流 ─────────────────────────────────────────────── */

.falls {
  display: flex;
  gap: $cm-space-2;
  padding: $cm-space-3;
  align-items: flex-start;
}

.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.loadmore {
  padding: $cm-space-4 0;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 悬浮发布按钮 ───────────────────────────────────────── */

.fab {
  position: fixed;
  right: $cm-space-4;
  /* 20pt + 安全区（design/02 §5.5） */
  bottom: calc(40rpx + env(safe-area-inset-bottom));
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
