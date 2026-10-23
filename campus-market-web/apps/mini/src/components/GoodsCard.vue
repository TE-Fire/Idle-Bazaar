<script setup lang="ts">
/**
 * 瀑布流商品卡（design/02 §5.1，2026-10-24 按年轻化审美重设计）
 *
 * <p>与 Web 端的网格卡是**三套模板**里的两套（还有管理端的表格行），逻辑共用、
 * 模板各自实现。本端要点：图片占比 ≥ 60%，文字只留标题 + 价格 + 成色，其余折叠。
 *
 * <p>重设计原则（克制，不堆装饰）：方图 1:1（闲鱼口径，比 4:3 更整）；
 * 卡片圆角统一 28rpx；阴影不用（小程序语义层无 shadow 令牌，tech/02 §五），
 * 分层靠灰底 + 白卡；价格三段式（¥ 小号、整数大号、小数小号）与 Web 端同构；
 * 卖家行加渐变小圆点替代纯文字，弱化信息密度。
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
  border-radius: $cm-radius-xl;
  overflow: hidden;
  /* 按压反馈 120ms（design/02 §七） */
  transition: transform 120ms ease-out;
}

.card--active {
  transform: scale(0.97);
}

/* 方图 1:1（闲鱼口径）：图片占比 ~62% 由卡片整体比例保证 */
.pic {
  position: relative;
  aspect-ratio: 1 / 1;
  background-size: cover;
  background-position: center;
}

.badge {
  position: absolute;
  left: $cm-space-3;
  top: $cm-space-3;
  font-size: $cm-font-size-xs;
  font-weight: 700;
  color: #fff;
  padding: 2rpx 12rpx;
  border-radius: $cm-radius-pill;
  background: rgba(31, 31, 38, 0.72);
}

.badge--hot {
  background: $cm-color-price;
}

.body {
  padding: $cm-space-3;
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.title {
  font-size: $cm-font-size-base;
  line-height: 1.4;
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
  font-weight: 700;
  color: $cm-color-price;
}

.price {
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1;
  color: $cm-color-price;
}

.dec {
  font-size: $cm-font-size-sm;
  font-weight: 700;
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
}

/* 渐变小圆点：替代纯文字昵称，弱化信息密度 */
.dot {
  width: 32rpx;
  height: 32rpx;
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
