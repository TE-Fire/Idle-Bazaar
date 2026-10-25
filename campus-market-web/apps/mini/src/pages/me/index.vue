<script setup lang="ts">
/**
 * M6 我的（design/02 §六，P0）
 *
 * <p>「我发布的」已打通到我的商品页（含草稿，学生端商品接口就绪）；
 * 卖出 / 买到 / 收藏依赖订单与用户接口，仍为占位，明确标「待建」。
 * 未登录时点头像区进登录页；已登录进资料编辑页（昵称 / 头像，notes/20 D3）。
 */

import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getSession, clearSession } from '@/utils/session'
import { userAuthApi } from '@/api/user'

const nick = ref('')
const avatar = ref('')

interface Entry {
  key: string
  label: string
  hint: string
}

const entries: Entry[] = [
  { key: 'published', label: '我发布的', hint: '草稿 / 在售 / 审核中' },
  { key: 'bought', label: '我买到的', hint: '待付款 / 待面交 / 退款' },
  { key: 'sold', label: '我卖出的', hint: '待面交 / 核销 / 仲裁' },
  { key: 'fav', label: '我的收藏', hint: '降价提醒' },
  { key: 'verify', label: '学号认证', hint: '认证后可提升信任' }
]

/** onShow：从登录页 / 编辑页返回时要刷新昵称与头像 */
onShow(() => {
  const s = getSession()
  nick.value = s.nickname
  avatar.value = s.avatarUrl
})

function open(e: Entry): void {
  if (e.key === 'published') {
    void uni.navigateTo({ url: '/pages/my-items/index' })
    return
  }
  if (e.key === 'bought' || e.key === 'sold') {
    const role = e.key === 'bought' ? 'buyer' : 'seller'
    void uni.navigateTo({ url: `/pages/order/list?role=${role}` })
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
    <view class="head" hover-class="entry--active" @click="onHead">
      <view class="head__avatar">
        <image v-if="avatar" class="head__img" :src="avatar" mode="aspectFill" />
        <text v-else class="head__t">{{ nick ? nick.slice(0, 1) : '我' }}</text>
      </view>
      <view class="head__meta">
        <text class="head__nick">{{ nick || '点击登录' }}</text>
        <text class="head__sub">{{ nick ? '已登录 · 点头像编辑资料' : '未登录' }}</text>
      </view>
      <text v-if="nick" class="head__logout" @click.stop="logout">退出</text>
    </view>

    <view class="list">
      <view v-for="e in entries" :key="e.key" class="entry" hover-class="entry--active" @click="open(e)">
        <text class="entry__label">{{ e.label }}</text>
        <text class="entry__hint">{{ e.hint }}</text>
        <text class="entry__arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  background: $cm-color-bg;
  min-height: 100vh;
}

.head {
  display: flex;
  align-items: center;
  gap: $cm-space-4;
  padding: $cm-space-8 $cm-space-5 $cm-space-6;
  background: $cm-color-surface;
}

.head__avatar {
  width: 144rpx;
  height: 144rpx;
  border-radius: 50%;
  background: $cm-color-brand-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.head__t {
  font-size: 56rpx;
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
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.head__nick {
  font-size: $cm-font-size-lg;
  font-weight: 700;
  color: $cm-color-text-1;
}

.head__sub {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.head__logout {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  padding: $cm-space-2 $cm-space-3;
}

.list {
  margin: $cm-space-4 $cm-space-4 0;
  border-radius: $cm-radius-xl;
  background: $cm-color-surface;
  overflow: hidden;
}

.entry {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  height: 120rpx;
  padding: 0 $cm-space-5;
  border-bottom: 1rpx solid $cm-color-border;
  transition: transform 120ms ease-out;
}

.entry:last-child {
  border-bottom: none;
}

.entry--active {
  transform: scale(0.99);
}

.entry__label {
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.entry__hint {
  margin-left: auto;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.entry__arrow {
  font-size: $cm-font-size-lg;
  color: $cm-color-text-3;
}
</style>
