<script setup lang="ts">
/**
 * 瀑布流商品卡（design/02 §5.1）
 *
 * <p>与 Web 端的网格卡是**三套模板**里的两套（还有管理端的表格行），逻辑共用、
 * 模板各自实现。本端要点：图片占比 ≥ 60%，文字只留标题 + 价格 + 成色，其余折叠。
 *
 * <p>触屏没有 hover（design/02 §二），所以按压反馈用 `:active` 缩放 0.97，120ms。
 */

import { computed } from 'vue'
import { formatPrice, formatRelativeTime, type GoodsItem } from '@campus/shared'
import ConditionTag from './ConditionTag.vue'

const props = defineProps<{ item: GoodsItem }>()

const gradient = computed(() => `linear-gradient(135deg, ${props.item.tints[0]}, ${props.item.tints[1]})`)
const timeText = computed(() => formatRelativeTime(props.item.publishedAt))

function open(): void {
  uni.navigateTo({ url: `/pages/item/detail?id=${props.item.id}` })
}
</script>

<template>
  <view class="card" hover-class="card--active" @click="open">
    <view class="pic" :style="{ backgroundImage: gradient }">
      <text v-if="item.badge" class="badge" :class="{ 'badge--hot': item.badge === '急出' }">{{ item.badge }}</text>
    </view>

    <view class="body">
      <text class="title">{{ item.title }}</text>
      <view class="price-row">
        <text class="price">{{ formatPrice(item.price) }}</text>
        <text v-if="item.originalPrice" class="origin">{{ formatPrice(item.originalPrice) }}</text>
      </view>
      <view class="meta">
        <ConditionTag :grade="item.condition" />
        <text class="campus">{{ item.campus }}</text>
      </view>
      <view class="seller">
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
  border-radius: $cm-radius-lg;
  overflow: hidden;
  /* 按压反馈 120ms（design/02 §七） */
  transition: transform 120ms ease-out;
}

.card--active {
  transform: scale(0.97);
}

/* 图片占卡片 ~62%（design/02 原则 2） */
.pic {
  position: relative;
  aspect-ratio: 4 / 3;
  background-size: cover;
}

.badge {
  position: absolute;
  left: $cm-space-2;
  top: $cm-space-2;
  font-size: $cm-font-size-xs;
  font-weight: 700;
  color: #fff;
  padding: 0 10rpx;
  border-radius: $cm-radius-sm;
  background: rgba(31, 31, 38, 0.72);
}

.badge--hot {
  background: $cm-color-price;
}

.body {
  padding: $cm-space-3;
  display: flex;
  flex-direction: column;
  gap: $cm-space-1;
}

.title {
  font-size: $cm-font-size-base;
  line-height: 1.35;
  color: $cm-color-text-1;
  /* 两行截断：小程序里 -webkit-line-clamp 可用 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
}

/* 价格用 700 —— 小程序禁用 800（PingFang 无此字重会合成，发虚） */
.price {
  font-size: $cm-font-size-lg;
  font-weight: 700;
  line-height: 1.1;
  color: $cm-color-price;
}

.origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
}

.meta {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.campus {
  font-size: $cm-font-size-xs;
}

.seller {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.nick {
  color: $cm-color-text-2;
}

.verified {
  color: $cm-color-ok;
}

.time {
  margin-left: auto;
}
</style>
