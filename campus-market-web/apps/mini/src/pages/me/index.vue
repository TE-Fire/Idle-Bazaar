<script setup lang="ts">
/**
 * M6 我的（design/02 §六，P0）
 *
 * <p>「我发布的」已打通到我的商品页（含草稿，学生端商品接口就绪）；
 * 卖出 / 买到 / 收藏依赖订单与用户接口，仍为占位，明确标「待建」。
 * 未登录时点头像区进登录页；登录后显示昵称与退出。
 */

import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getSession, clearSession } from '@/utils/session'
import { userAuthApi } from '@/api/user'

const nick = ref('')

interface Entry {
  key: string
  label: string
  hint: string
}

const entries: Entry[] = [
  { key: 'published', label: '我发布的', hint: '草稿 / 在售 / 审核中' },
  { key: 'sold', label: '我卖出的', hint: '已完成交易' },
  { key: 'bought', label: '我买到的', hint: '待面交 / 已完成' },
  { key: 'fav', label: '我的收藏', hint: '降价提醒' },
  { key: 'verify', label: '学号认证', hint: '认证后可提升信任' }
]

/** onShow：从登录页返回时要刷新昵称 */
onShow(() => {
  nick.value = getSession().nickname
})

function open(e: Entry): void {
  if (e.key === 'published') {
    void uni.navigateTo({ url: '/pages/my-items/index' })
    return
  }
  uni.showToast({ title: `「${e.label}」待建`, icon: 'none' })
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
    <view class="head" hover-class="entry--active" @click="nick ? undefined : goLogin()">
      <view class="head__avatar"><text class="head__t">{{ nick ? nick.slice(0, 1) : '我' }}</text></view>
      <view class="head__meta">
        <text class="head__nick">{{ nick || '点击登录' }}</text>
        <text class="head__sub">{{ nick ? '已登录' : '未登录' }}</text>
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
  padding: $cm-space-6 $cm-space-4;
  background: $cm-color-surface;
}

.head__avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $cm-color-brand-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.head__t {
  font-size: 44rpx;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.head__meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.head__nick {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-text-1;
}

.head__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.head__logout {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.list {
  margin-top: $cm-space-3;
  background: $cm-color-surface;
}

.entry {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  height: 112rpx;
  padding: 0 $cm-space-4;
  border-bottom: 1rpx solid $cm-color-border;
  transition: transform 120ms ease-out;
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
  font-size: $cm-font-size-md;
  color: $cm-color-text-3;
}
</style>
