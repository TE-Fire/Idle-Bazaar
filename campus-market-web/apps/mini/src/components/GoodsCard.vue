<script setup lang="ts">
/**
 * 瀑布流商品卡（design/02 §5.1，2026-10-24 按年轻化审美重设计）
 *
 * <p>与 Web 端的网格卡是**三套模板**里的两套（还有管理端的表格行），逻辑共用、
 * 模板各自实现。本端要点：图片占比 ≥ 60%，文字只留标题 + 价格 + 成色，其余折叠。
 *
 * <p>重设计原则（克制，不堆装饰）：方图 1:1（闲鱼口径，比 4:3 更整）；
 * 卡片圆角 36rpx、软阴影一层，分层不再只靠灰底；价格三段式（¥ 小号、整数大号、
 * 小数小号）与 Web 端同构；卖家行用渐变小圆点 + 弱化字号，把视觉重心留给图和价。
 *
 * <p>触屏没有 hover（design/02 §二），所以按压反馈用 `:active` 缩放 0.97，120ms。
 */

import { computed } from 'vue'
import { formatRelativeTime, type GoodsItem } from '@campus/shared'
import ConditionTag from './ConditionTag.vue'

const props = defineProps<{ item: GoodsItem }>()

const gradient = computed(() => `linear-gradient(135deg, ${props.item.tints[0]}, ${props.item.tints[1]})`)
const cover = computed(() => props.item.images?.[0] ?? '')
const timeText = computed(() => formatRelativeTime(props.item.publishedAt))

/** 三段式价格：¥ 与小数缩小、整数放大（与 Web 端 ProductCard 同口径） */
const priceInt = computed(() => Math.floor(props.item.price))
const priceDec = computed(() => {
  const dec = Math.round((props.item.price - Math.floor(props.item.price)) * 100)
  return dec > 0 ? `.${String(dec).padStart(2, '0')}` : ''
})

function open(): void {
  uni.navigateTo({ url: `/pages/item/detail?id=${props.item.id}` })
}
</script>

<template>
  <view class="card" hover-class="card--active" @click="open">
    <view class="pic" :style="cover ? { backgroundImage: `url(${cover})` } : { backgroundImage: gradient }">
      <text v-if="item.badge" class="badge" :class="{ 'badge--hot': item.badge === '急出' }">{{ item.badge }}</text>
      <!-- 可议价压图右上角：列表里要一眼看出「这件能讲价」，而不是点进详情才知道 -->
      <text v-if="item.negotiable" class="nego">可议价</text>
    </view>

    <view class="body">
      <text class="title">{{ item.title }}</text>
      <view class="price-row">
        <text class="yen">¥</text>
        <text class="price">{{ priceInt }}</text>
        <text v-if="priceDec" class="dec">{{ priceDec }}</text>
        <text v-if="item.originalPrice" class="origin">¥{{ item.originalPrice }}</text>
      </view>
      <view class="meta">
        <ConditionTag :grade="item.condition" />
        <text class="campus">{{ item.campus }}</text>
      </view>
      <view class="seller">
        <view class="dot" :style="{ backgroundImage: `linear-gradient(135deg, ${item.seller.tints[0]}, ${item.seller.tints[1]})` }" />
        <text class="nick">{{ item.seller.nick }}</text>
        <text v-if="item.seller.verified" class="verified">已认证</text>
        <text class="time">{{ timeText }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.card {
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
  overflow: hidden;
  box-shadow: $cm-shadow-sm;
  /* 按压反馈 120ms（design/02 §七） */
  transition: transform 140ms ease-out, box-shadow 140ms ease-out;
}

.card--active {
  transform: scale(0.97);
  box-shadow: $cm-shadow-xs;
}

/* 方图 1:1（闲鱼口径）：图片占比 ~62% 由卡片整体比例保证 */
.pic {
  position: relative;
  aspect-ratio: 1 / 1;
  background-size: cover;
  background-position: center;
}

/* 图底一层极淡压暗：让白底商品图不糊在卡片边缘上 */
.pic::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 30%;
  background-image: linear-gradient(180deg, rgba(31, 31, 38, 0), rgba(31, 31, 38, 0.06));
}

.badge {
  position: absolute;
  z-index: 1;
  left: $cm-space-3;
  top: $cm-space-3;
  font-size: $cm-font-size-xs;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
  padding: 4rpx 14rpx;
  border-radius: $cm-radius-pill;
  background: rgba(31, 31, 38, 0.66);
}

.badge--hot {
  background-image: linear-gradient(135deg, #FF8A5B, $cm-color-price);
  box-shadow: 0 4rpx 10rpx rgba(255, 85, 0, 0.35);
}

/* 可议价：品牌色实底，与「急出」的橙红区分开，两者可同时出现（左右各一个） */
.nego {
  position: absolute;
  z-index: 1;
  right: $cm-space-3;
  top: $cm-space-3;
  padding: 4rpx 12rpx;
  font-size: 20rpx;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: 0 4rpx 10rpx rgba(255, 106, 0, 0.32);
}

.body {
  padding: $cm-space-3;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.title {
  font-size: $cm-font-size-base;
  line-height: 1.42;
  color: $cm-color-text-1;
  /* 两行截断：小程序里 -webkit-line-clamp 可用 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  min-height: 78rpx;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

/* 价格用 700 —— 小程序禁用 800（PingFang 无此字重会合成，发虚） */
.yen {
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-price;
}

.price {
  font-size: 44rpx;
  font-weight: $cm-font-weight-heavy;
  line-height: 1;
  color: $cm-color-price;
  letter-spacing: -0.5rpx;
}

.dec {
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-price;
}

.origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
  margin-left: auto;
}

.meta {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.campus {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.seller {
  display: flex;
  align-items: center;
  gap: $cm-space-1;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  padding-top: 2rpx;
  border-top: 1rpx solid $cm-color-border;
  margin-top: 2rpx;
  padding-top: $cm-space-2;
}

/* 渐变小圆点：替代纯文字昵称，弱化信息密度 */
.dot {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  flex: none;
}

.nick {
  max-width: 140rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.verified {
  color: $cm-color-ok;
  background: $cm-color-ok-bg;
  border-radius: 6rpx;
  padding: 0 8rpx;
  line-height: 28rpx;
}

.time {
  margin-left: auto;
}
</style>
