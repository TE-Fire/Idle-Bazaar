<script setup lang="ts">
/**
 * M6 我的（design/02 §六，P0）
 *
 * <p>「我发布的 / 我买到的 / 我卖出的」三项入口合并到「我的交易」页（顶部三 tab，
 * 「我发布的」默认不出已售、已售以订单形态归到「我卖出的」，避免同商品两边重复）。
 * 收藏与学号认证仍为占位，点了明确提示「待建」。
 * 未登录时点头像区进登录页；已登录进资料编辑页（昵称 / 头像，notes/20 D3）。
 */

import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getSession, clearSession } from '@/utils/session'
import { userAuthApi } from '@/api/user'

const nick = ref('')
const avatar = ref('')

interface OrderEntry {
  key: string
  label: string
}
interface ServiceEntry {
  key: string
  label: string
}

/** 订单快览：三栏平铺，点进对应列表（闲鱼式紧凑布局） */
const orderEntries: OrderEntry[] = [
  { key: 'published', label: '我发布的' },
  { key: 'bought', label: '我买到的' },
  { key: 'sold', label: '我卖出的' }
]

/** 我的服务：分组平铺（关注 / 粉丝收进头部，不单列） */
const serviceEntries: ServiceEntry[] = [
  { key: 'fav', label: '我的收藏' },
  { key: 'verify', label: '学号认证' }
]

/** onShow：从登录页 / 编辑页返回时要刷新昵称与头像 */
onShow(() => {
  const s = getSession()
  nick.value = s.nickname
  avatar.value = s.avatarUrl
})

function open(e: { key: string; label: string }): void {
  if (e.key === 'published') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=0' })
    return
  }
  if (e.key === 'bought') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=1' })
    return
  }
  if (e.key === 'sold') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=2' })
    return
  }
  uni.showToast({ title: `「${e.label}」待建`, icon: 'none' })
}

/** 头像区：未登录进登录页，已登录进资料编辑（notes/20 D3/D4） */
function onHead(): void {
  if (!nick.value) {
    goLogin()
    return
  }
  void uni.navigateTo({ url: '/pages/profile-edit/index' })
}

function goLogin(): void {
  void uni.navigateTo({ url: '/pages/login/index' })
}

/** 关注 / 粉丝：收进头部，点开关注列表（默认落在关注 tab） */
function goFollows(): void {
  void uni.navigateTo({ url: '/pages/follows/index' })
}

async function logout(): Promise<void> {
  try {
    await userAuthApi.logout()
  } finally {
    clearSession()
    nick.value = ''
    uni.showToast({ title: '已退出', icon: 'none' })
  }
}
</script>

<template>
  <view class="page">
    <!-- 紧凑头部：头像 + 昵称 + 编辑入口（闲鱼式一行） -->
    <view class="head" hover-class="entry--active" @click="onHead">
      <view class="head__avatar">
        <image v-if="avatar" class="head__img" :src="avatar" mode="aspectFill" />
        <text v-else class="head__t">{{ nick ? nick.slice(0, 1) : '我' }}</text>
      </view>
      <view class="head__meta">
        <text class="head__nick">{{ nick || '点击登录' }}</text>
        <text class="head__sub">{{ nick ? '点击编辑资料' : '登录后体验完整功能' }}</text>
        <text v-if="nick" class="head__follow" @click.stop="goFollows">关注 / 粉丝</text>
      </view>
      <text class="head__edit">›</text>
    </view>

    <!-- 订单快览：三栏等分，分隔线仅栏目之间 -->
    <view class="quick">
      <view
        v-for="e in orderEntries"
        :key="e.key"
        class="quick__item"
        hover-class="entry--active"
        @click="open(e)"
      >
        <text class="quick__label">{{ e.label }}</text>
      </view>
    </view>

    <!-- 我的服务：分组平铺，行高压实 -->
    <view class="group">
      <text class="group__title">我的服务</text>
      <view
        v-for="e in serviceEntries"
        :key="e.key"
        class="svc"
        hover-class="entry--active"
        @click="open(e)"
      >
        <text class="svc__label">{{ e.label }}</text>
        <text class="svc__arrow">›</text>
      </view>
    </view>

    <view v-if="nick" class="logout" hover-class="entry--active" @click="logout">
      <text class="logout__text">退出登录</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  background: $cm-color-bg;
  min-height: 100vh;
}

/* ── 紧凑头部：头像 96rpx + 昵称 + 编辑箭头 ── */
.head {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
}

.head__avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: $cm-color-brand-bg;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.head__t {
  font-size: 40rpx;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.head__img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.head__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.head__nick {
  font-size: 34rpx;
  font-weight: 700;
  color: $cm-color-text-1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.head__sub {
  font-size: 22rpx;
  color: $cm-color-text-3;
}

/* 关注 / 粉丝：头部内紧凑链接，不单列成行 */
.head__follow {
  margin-top: 2rpx;
  font-size: 22rpx;
  color: $cm-color-brand;
}

.head__edit {
  font-size: 40rpx;
  color: $cm-color-text-3;
  flex-shrink: 0;
}

/* ── 订单快览：三栏等分，仅栏间细分隔线 ── */
.quick {
  display: flex;
  background: $cm-color-surface;
  margin-top: 1rpx;
}

.quick__item {
  flex: 1;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;

  & + &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 28rpx;
    bottom: 28rpx;
    width: 1rpx;
    background: $cm-color-border;
  }
}

.quick__label {
  font-size: 26rpx;
  color: $cm-color-text-1;
}

/* ── 我的服务：分组标题 + 压实行 ── */
.group {
  margin-top: $cm-space-2;
  background: $cm-color-surface;
}

.group__title {
  display: block;
  padding: $cm-space-2 $cm-space-4 0;
  font-size: 22rpx;
  font-weight: 600;
  color: $cm-color-text-3;
}

.svc {
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 $cm-space-4;
  border-top: 1rpx solid $cm-color-border;

  &:first-of-type {
    margin-top: $cm-space-1;
  }
}

.svc__label {
  flex: 1;
  font-size: 28rpx;
  color: $cm-color-text-1;
}

.svc__arrow {
  font-size: 32rpx;
  color: $cm-color-text-3;
}

/* ── 退出：底部整行弱提示 ── */
.logout {
  margin-top: $cm-space-2;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $cm-color-surface;
}

.logout__text {
  font-size: 28rpx;
  color: $cm-color-err;
}

.entry--active {
  background: $cm-color-bg;
}
</style>
