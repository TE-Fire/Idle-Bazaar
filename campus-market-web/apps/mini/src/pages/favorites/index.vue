<script setup lang="ts">
/**
 * 我的收藏（notes/24 第一节）
 *
 * <p>只展示「商品还在」的收藏：已软删与被强制下架的由后端过滤，所以列表里没有失效行。
 * 已售出 / 已下架照常展示并标状态——用户要能看到「我收的那件已经卖掉了」。
 *
 * <p>取消收藏就地移除该行，不重拉整页：重拉会让双列瀑布跳回顶部。
 */

import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { ITEM_STATUS_LABELS, formatPrice } from '@campus/shared'
import { userItemApi, type ApiItemVO } from '@/api/user'

const list = ref<ApiItemVO[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)

async function fetchPage(reset = false): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const page = await userItemApi.favorites(pageNum.value, 10)
    list.value = reset ? page.list : list.value.concat(page.list)
    total.value = page.total
  } catch {
    // request 层已提示；未登录（1001）会跳登录页
  } finally {
    loading.value = false
  }
}

function hasMore(): boolean {
  return list.value.length < total.value
}

onLoad(() => {
  void fetchPage(true)
})

onPullDownRefresh(() => {
  pageNum.value = 1
  void fetchPage(true).then(() => uni.stopPullDownRefresh())
})

onReachBottom(() => {
  if (!hasMore() || loading.value) return
  pageNum.value += 1
  void fetchPage(false)
})

function goDetail(id: string): void {
  void uni.navigateTo({ url: `/pages/item/detail?id=${id}` })
}

/** 空态「去逛逛」：首页是 tab 页，必须 switchTab 不能 navigateTo */
function goHome(): void {
  void uni.switchTab({ url: '/pages/index/index' })
}

/** 取消收藏：先确认，成功后本地移除 */
function remove(id: string): void {
  uni.showModal({
    title: '取消收藏',
    content: '这件商品会从收藏列表里移除',
    confirmText: '取消收藏',
    success: (res) => {
      if (!res.confirm) return
      userItemApi
        .unfavorite(id)
        .then(() => {
          list.value = list.value.filter((i) => i.id !== id)
          total.value = Math.max(total.value - 1, 0)
          uni.showToast({ title: '已取消收藏', icon: 'none' })
        })
        .catch(() => undefined)
    }
  })
}

function statusText(s: ApiItemVO['status']): string {
  return ITEM_STATUS_LABELS[s] ?? ''
}
</script>

<template>
  <view class="favs">
    <view v-if="list.length === 0 && !loading" class="empty">
      <text class="empty__t">还没有收藏</text>
      <text class="empty__s">看到喜欢的点一下收藏，过几天再回来比价</text>
      <view class="empty__btn" @click="goHome">去逛逛</view>
    </view>

    <view v-else class="grid">
      <view v-for="it in list" :key="it.id" class="card">
        <view class="card__ph" @click="goDetail(it.id)">
          <image v-if="it.mainImage" class="card__img" :src="it.mainImage" mode="aspectFill" />
          <view v-else class="card__ph-empty">
            <text>无图</text>
          </view>
          <view v-if="it.status !== 'ON_SALE'" class="card__mask">
            <text>{{ statusText(it.status) }}</text>
          </view>
        </view>
        <view class="card__body" @click="goDetail(it.id)">
          <text class="card__title">{{ it.title }}</text>
          <view class="card__row">
            <text class="card__price">{{ formatPrice(Number(it.price)) }}</text>
            <text class="card__campus">{{ it.campusName || '' }}</text>
          </view>
        </view>
        <view class="card__out" @click="remove(it.id)">取消收藏</view>
      </view>
    </view>

    <view v-if="list.length > 0 && !hasMore()" class="tail">没有更多了</view>
  </view>
</template>

<style lang="scss" scoped>
.favs {
  min-height: 100vh;
  padding: $cm-space-4 $cm-space-3 calc(#{$cm-space-6} + env(safe-area-inset-bottom));
  background: $cm-color-bg;
  box-sizing: border-box;
}

.empty {
  padding-top: 200rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty__t {
  font-size: $cm-font-size-lg;
  color: $cm-color-text-1;
  font-weight: $cm-font-weight-medium;
}

.empty__s {
  margin-top: 12rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.empty__btn {
  margin-top: 40rpx;
  padding: 0 48rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: $cm-radius-pill;
  background: $cm-color-brand;
  color: #1b1b21;
  font-size: $cm-font-size-base;
}

.grid {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
}

.card {
  position: relative;
  width: 48.6%;
  margin-bottom: $cm-space-3;
  border-radius: $cm-radius-lg;
  background: $cm-color-surface;
  overflow: hidden;
}

.card__ph {
  position: relative;
  width: 100%;
  height: 340rpx;
  background: #f4f4f7;
}

.card__img {
  width: 100%;
  height: 100%;
}

.card__ph-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 24rpx;
  color: $cm-color-text-3;
}

.card__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 17, 22, 0.42);
  color: #fff;
  font-size: $cm-font-size-sm;
}

.card__body {
  padding: $cm-space-2 $cm-space-3 $cm-space-3;
}

.card__title {
  display: block;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  line-height: 1.4;
  max-height: 2.8em;
  overflow: hidden;
}

.card__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10rpx;
}

.card__price {
  font-size: 34rpx;
  font-weight: $cm-font-weight-bold;
  color: #e8543f;
}

.card__campus {
  font-size: 22rpx;
  color: $cm-color-text-3;
}

.card__out {
  padding: 0 $cm-space-3 12rpx;
  font-size: 22rpx;
  color: $cm-color-text-3;
}

.tail {
  padding: 24rpx 0 40rpx;
  text-align: center;
  font-size: 24rpx;
  color: $cm-color-text-3;
}
</style>
