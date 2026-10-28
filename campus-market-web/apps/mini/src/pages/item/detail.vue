<script setup lang="ts">
/**
 * M2 商品详情（design/02 §六）
 *
 * <p>「半熟人信任可见」（原则 4）：学号认证、同校、面交点三项必须一眼看到——
 * 所以这三样放在参数区的最前面，不折叠。
 *
 * <p>数据接 `/api/item/{id}`（免登录）。底部「聊一聊」进询单会话（同步留言）；
 * 「我想要」下单（未登录先跳登录）：只插待付款订单，商品不锁定，金额按标价。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { CONDITION_LABELS, formatPrice, formatRelativeTime } from '@campus/shared'
import { userItemApi, userOrderApi, type ApiItemVO } from '@/api/user'
import { getSession } from '@/utils/session'
import ConditionTag from '@/components/ConditionTag.vue'

const id = ref('')
const item = ref<ApiItemVO | null>(null)

onLoad((query) => {
  id.value = (query as { id?: string })?.id ?? ''
  void userItemApi
    .detail(id.value)
    .then((vo) => {
      item.value = vo
    })
    .catch(() => {
      // 不存在 / 已下架：request 层已提示，落空态
    })
})

/** 图集：有真实图片用图片，否则一张渐变占位 */
const shots = computed(() => {
  const imgs = item.value?.images ?? []
  return imgs.length ? imgs.map((i) => i.url) : [null]
})

function shotGradient(): string {
  return 'linear-gradient(135deg, #ECEFF3, #D3D9E0)'
}

/** 进询单会话：把手里的商品摘要带上，会话窗顶部卡片直接用（见 chat/detail onLoad） */
function onChat(): void {
  if (!item.value) return
  const it = item.value
  const q = [
    `itemId=${it.id}`,
    `peerId=${it.sellerId}`,
    `nick=${encodeURIComponent(it.sellerNick ?? '')}`,
    `itemTitle=${encodeURIComponent(it.title)}`,
    `itemImage=${it.mainImage ? encodeURIComponent(it.mainImage) : ''}`,
    `itemPrice=${it.price ?? ''}`,
    `itemStatus=${it.status ?? ''}`
  ].join('&')
  void uni.navigateTo({ url: `/pages/chat/detail?${q}` })
}

/** 我想要：未登录先跳登录（下单要令牌），已登录直接创建待付款订单进详情 */
function onBuy(): void {
  if (!item.value) return
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (item.value.sellerId === getSession().userId) {
    uni.showToast({ title: '不能购买自己发布的商品', icon: 'none' })
    return
  }
  uni.showLoading({ title: '下单中…', mask: true })
  userOrderApi
    .create({ itemId: item.value.id, meetPointId: item.value.meetPointId ?? undefined })
    .then((orderId) => {
      uni.hideLoading()
      uni.showToast({ title: '已创建订单', icon: 'success' })
      void uni.redirectTo({ url: `/pages/order/detail?id=${orderId}&role=buyer` })
    })
    .catch(() => uni.hideLoading())
}

/** 卖家卡 → TA 的个人主页 */
function goSeller(): void {
  if (!item.value) return
  void uni.navigateTo({ url: `/pages/user/index?userId=${item.value.sellerId}` })
}
</script>

<template>
  <view v-if="item" class="page">
    <swiper class="swiper" indicator-dots circular>
      <swiper-item v-for="(s, i) in shots" :key="i">
        <image v-if="s" :src="s" mode="aspectFill" class="shot__img" />
        <view v-else class="shot" :style="{ backgroundImage: shotGradient() }" />
      </swiper-item>
    </swiper>

    <view class="box">
      <text class="price">{{ formatPrice(Number(item.price)) }}</text>
      <text v-if="item.originPrice" class="origin">原价 {{ formatPrice(Number(item.originPrice)) }}</text>
      <text class="title">{{ item.title }}</text>
      <text class="want">浏览 {{ item.viewCount }} 次 · {{ formatRelativeTime(item.publishAt) }}</text>
    </view>

    <!-- 校园四项：成色 / 校区 / 面交点 / 认证，前三项必须一眼可见 -->
    <view class="box">
      <view class="row">
        <text class="row__k">成色</text>
        <ConditionTag :grade="item.grade as 'A'" />
        <text class="row__v">{{ CONDITION_LABELS[item.grade as 'A'] }}</text>
      </view>
      <view class="row"><text class="row__k">校区</text><text class="row__v">{{ item.campusName }}</text></view>
      <view class="row"><text class="row__k">面交点</text><text class="row__v">{{ item.meetPointName ?? '待定' }}</text></view>
      <view class="row">
        <text class="row__k">交易</text>
        <text class="row__v">{{ item.negotiable ? '可议价' : '一口价' }} · {{ item.deliveryIncluded ? '包配送' : '自提' }}</text>
      </view>
    </view>

    <view class="box seller" hover-class="seller--active" @click="goSeller">
      <view class="avatar" :style="{ backgroundImage: shotGradient() }" />
      <view class="seller__meta">
        <text class="seller__nick">{{ item.sellerNick ?? '卖家' }}</text>
        <text class="seller__sub">信用 {{ item.sellerCredit ?? 100 }} 分 · 进主页</text>
      </view>
      <text class="seller__arrow">›</text>
    </view>

    <view class="box">
      <text class="desc__t">商品描述</text>
      <text class="desc">{{ item.description || '卖家没有填写描述。' }}</text>
    </view>

    <!-- 底部固定操作条：50pt + 安全区，主行动占 60% -->
    <view class="actionbar">
      <view class="actionbar__minor" @click="onChat">聊一聊</view>
      <view class="actionbar__main" hover-class="actionbar__main--active" @click="onBuy">我想要</view>
    </view>
  </view>

  <view v-else class="empty">
    <text>商品不存在或已下架</text>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}

/* 图集通铺到屏幕边（闲鱼口径）：卡片从图下面才开始 */
.swiper {
  height: 640rpx;
}

.shot {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}

.shot__img {
  width: 100%;
  height: 100%;
}

.box {
  margin: $cm-space-3 $cm-space-4;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.price {
  font-size: 56rpx;
  font-weight: 700;
  line-height: 1;
  color: $cm-color-price;
}

.origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
}

.title {
  font-size: $cm-font-size-lg;
  font-weight: 700;
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
  gap: $cm-space-4;
  font-size: $cm-font-size-sm;
  min-height: 56rpx;
}

.row__k {
  width: 112rpx;
  color: $cm-color-text-3;
  flex: none;
}

.row__v {
  color: $cm-color-text-1;
}

.seller {
  flex-direction: row;
  align-items: center;
}

.seller--active {
  transform: scale(0.99);
}

.seller__arrow {
  margin-left: auto;
  font-size: $cm-font-size-lg;
  color: $cm-color-text-3;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background-size: cover;
}

.seller__meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-left: $cm-space-4;
}

.seller__nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
}

.seller__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.desc__t {
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
}

.desc {
  font-size: $cm-font-size-sm;
  line-height: 1.7;
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
