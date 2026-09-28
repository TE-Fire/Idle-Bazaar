<script setup lang="ts">
/**
 * M2 商品详情（design/02 §六）
 *
 * <p>「半熟人信任可见」（原则 4）：学号认证、同校、面交点三项必须一眼看到——
 * 所以这三样放在参数区的最前面，不折叠。
 *
 * <p>页面栈最多 10 层（design/02 §二）：详情页 → 卖家主页 → 商品详情 这类链路
 * 要用 `redirectTo` 而不是 `navigateTo` 截断，否则来回点几次就跳不动了。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { CONDITION_LABELS, formatPrice, formatRelativeTime } from '@campus/shared'
import { findItem } from '@campus/mock'
import ConditionTag from '@/components/ConditionTag.vue'

const id = ref('')
const item = computed(() => findItem(id.value))

onLoad((query) => {
  id.value = (query as { id?: string })?.id ?? ''
})

const gradient = computed(() =>
  item.value ? `linear-gradient(135deg, ${item.value.tints[0]}, ${item.value.tints[1]})` : ''
)

/** 骨架期图集：4 张占位，第二张反向渐变（与 Web 端 hover 换图同源思路） */
const shots = computed(() => (item.value ? [0, 1, 2, 3] : []))

function shotGradient(i: number): string {
  if (!item.value) return ''
  const [a, b] = i % 2 === 1 ? [item.value.tints[1], item.value.tints[0]] : item.value.tints
  return `linear-gradient(135deg, ${a}, ${b})`
}

function onWant(): void {
  uni.showToast({ title: '已通知卖家', icon: 'none', duration: 1500 })
}

function onChat(): void {
  // 会话页还没建，骨架期只给反馈；将来用 redirectTo 截断页面栈
  uni.showToast({ title: '会话页待建（M5）', icon: 'none' })
}
</script>

<template>
  <view v-if="item" class="page">
    <swiper class="swiper" indicator-dots circular>
      <swiper-item v-for="i in shots" :key="i">
        <view class="shot" :style="{ backgroundImage: shotGradient(i) }" />
      </swiper-item>
    </swiper>

    <view class="box">
      <text class="price">{{ formatPrice(item.price) }}</text>
      <text v-if="item.originalPrice" class="origin">原价 {{ formatPrice(item.originalPrice) }}</text>
      <text class="title">{{ item.title }}</text>
      <text class="want">{{ item.wantCount }} 人想要 · {{ formatRelativeTime(item.publishedAt) }}</text>
    </view>

    <!-- 校园四项：成色 / 校区 / 面交点 / 认证，前三项必须一眼可见 -->
    <view class="box">
      <view class="row">
        <text class="row__k">成色</text>
        <ConditionTag :grade="item.condition" />
        <text class="row__v">{{ CONDITION_LABELS[item.condition] }}</text>
      </view>
      <view class="row"><text class="row__k">校区</text><text class="row__v">{{ item.campus }}</text></view>
      <view class="row"><text class="row__k">面交点</text><text class="row__v">{{ item.meetPoint }}</text></view>
      <view class="row">
        <text class="row__k">交易</text>
        <text class="row__v">{{ item.negotiable ? '可议价' : '一口价' }} · {{ item.delivery ? '包配送' : '自提' }}</text>
      </view>
    </view>

    <view class="box seller">
      <view class="avatar" :style="{ backgroundImage: gradient }" />
      <view class="seller__meta">
        <text class="seller__nick">{{ item.seller.nick }}</text>
        <text class="seller__sub">信用{{ item.seller.credit }}<text v-if="item.seller.verified" class="ok"> · 学号已认证</text></text>
      </view>
    </view>

    <view class="box">
      <text class="desc__t">商品描述</text>
      <text class="desc">{{ item.description }}</text>
    </view>

    <!-- 底部固定操作条：50pt + 安全区，主行动占 60% -->
    <view class="actionbar">
      <view class="actionbar__minor" @click="onChat">聊一聊</view>
      <view class="actionbar__main" hover-class="actionbar__main--active" @click="onWant">我想要</view>
    </view>
  </view>

  <view v-else class="empty">
    <text>商品不存在或已下架</text>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.swiper {
  height: 560rpx;
}

.shot {
  width: 100%;
  height: 100%;
  background-size: cover;
}

.box {
  margin: $cm-space-2 $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.price {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.1;
  color: $cm-color-price;
}

.origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
}

.title {
  font-size: $cm-font-size-md;
  font-weight: 600;
  line-height: 1.4;
  color: $cm-color-text-1;
}

.want {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.row {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  font-size: $cm-font-size-sm;
}

.row__k {
  width: 96rpx;
  color: $cm-color-text-3;
}

.row__v {
  color: $cm-color-text-1;
}

.seller {
  flex-direction: row;
  align-items: center;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background-size: cover;
}

.seller__meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-left: $cm-space-3;
}

.seller__nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
}

.seller__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.ok {
  color: $cm-color-ok;
}

.desc__t {
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
}

.desc {
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-2;
}

.actionbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: calc(100rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding-left: $cm-space-3;
  padding-right: $cm-space-3;
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
}

.actionbar__minor {
  width: 40%;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
  border: 1rpx solid $cm-color-border-strong;
  border-radius: $cm-radius-md;
}

.actionbar__main {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: #fff;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
  transition: transform 120ms ease-out;
}

.actionbar__main--active {
  transform: scale(0.97);
}

.empty {
  padding: 200rpx 0;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>
