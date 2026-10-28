<script setup lang="ts">
/**
 * 本地联调用「测试账号」切换器（折叠下拉，H5 / 小程序通用）
 *
 * <p>在 App.vue 里全局挂载，所有页面右上角出现一个可折叠的演示账号面板。
 * 点击账号即走 send-code → 取 devCode → login 全自动登录，免去手填验证码，
 * 方便在多个演示账号间快速切换测登录态相关流程。生产构建不挂载，不影响线上。
 */

import { ref } from 'vue'
import { userAuthApi } from '@/api/user'
import { saveSession, getSession, clearSession } from '@/utils/session'
import { DEMO_USERS, authLabel, type DemoUser } from '@campus/shared'

/** 浮层停靠侧：默认右下；首页右下有发布 FAB，传 left 错开 */
const props = withDefaults(
  defineProps<{ position?: 'right' | 'left' }>(),
  { position: 'right' }
)

const open = ref(false)
const loadingPhone = ref('')
const current = ref(getSession().nickname || '')

async function quickLogin(u: DemoUser): Promise<void> {
  loadingPhone.value = u.phone
  try {
    const sms = await userAuthApi.sendCode(u.phone)
    if (!sms.devCode) {
      uni.showToast({ title: 'devCode 未回显，请确认 echo=true', icon: 'none' })
      return
    }
    const vo = await userAuthApi.login(u.phone, sms.devCode)
    saveSession({
      userId: vo.userId ?? '',
      nickname: vo.nickname ?? '',
      avatarUrl: vo.avatarUrl ?? '',
      tokenName: vo.tokenName ?? '',
      tokenValue: vo.tokenValue ?? ''
    })
    current.value = vo.nickname ?? ''
    open.value = false
    uni.showToast({ title: `已切换为 ${u.nickname}`, icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.msg || e?.message || '登录失败', icon: 'none' })
  } finally {
    loadingPhone.value = ''
  }
}

function logout(): void {
  clearSession()
  current.value = ''
  open.value = false
  uni.showToast({ title: '已退出', icon: 'none' })
}
</script>

<template>
  <view class="dev-sw" :class="{ 'dev-sw--left': position === 'left' }">
    <view v-if="!open" class="dev-sw__fab" @click="open = true">
      <text class="dev-sw__dot" />
      <text>测试账号 · {{ current || '未登录' }}</text>
      <text class="dev-sw__caret">▲</text>
    </view>

    <view v-else class="dev-sw__panel">
      <view class="dev-sw__head">
        <text>切换登录账号（演示）</text>
        <text class="dev-sw__close" @click="open = false">▼</text>
      </view>

      <scroll-view class="dev-sw__list" scroll-y>
        <view
          v-for="u in DEMO_USERS"
          :key="u.id"
          class="dev-sw__item"
          :class="{ 'is-current': current && current === u.nickname }"
          @click="quickLogin(u)"
        >
          <text class="dev-sw__avatar">{{ u.nickname.slice(0, 1) }}</text>
          <view class="dev-sw__meta">
            <text class="dev-sw__name">
              {{ u.nickname }}
              <text v-if="u.banned" class="dev-sw__ban">封禁</text>
            </text>
            <text class="dev-sw__sub">{{ u.phone }} · {{ authLabel(u.authStatus) }} · 信用{{ u.creditScore }}</text>
          </view>
          <text v-if="loadingPhone === u.phone" class="dev-sw__spin">登录中…</text>
        </view>
      </scroll-view>

      <view class="dev-sw__foot">
        <text>当前：{{ current || '未登录' }}</text>
        <text v-if="current" class="dev-sw__logout" @click="logout">退出</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss">
.dev-sw {
  position: fixed;
  right: 16rpx;
  bottom: 16rpx;
  z-index: 9999;
  font-size: 22rpx;
}

/* 停靠左侧：避开首页右下角的发布 FAB */
.dev-sw--left {
  right: auto;
  left: 16rpx;
}

.dev-sw__fab {
  display: flex;
  align-items: center;
  padding: 10rpx 20rpx;
  color: #1a1a1a;
  background: $cm-color-brand;
  border-radius: 999rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.18);
}

.dev-sw__dot {
  width: 12rpx;
  height: 12rpx;
  margin-right: 8rpx;
  border-radius: 50%;
  background: #1a1a1a;
  opacity: 0.7;
}

.dev-sw__caret {
  margin-left: 8rpx;
  font-size: 18rpx;
}

.dev-sw__panel {
  width: 520rpx;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
  border-radius: 16rpx;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.dev-sw__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  font-weight: 700;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-bottom: 1rpx solid $cm-color-border;
}

.dev-sw__close {
  color: $cm-color-text-2;
}

.dev-sw__list {
  flex: 1;
  padding: 8rpx;
}

.dev-sw__item {
  display: flex;
  align-items: center;
  padding: 12rpx 16rpx;
  border-radius: 12rpx;

  &:active {
    background: $cm-color-bg;
  }

  &.is-current {
    background: $cm-color-brand-bg;
  }
}

.dev-sw__avatar {
  flex: none;
  width: 56rpx;
  height: 56rpx;
  margin-right: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #1a1a1a;
  background: linear-gradient(135deg, #ffe37a, $cm-color-brand);
  border-radius: 50%;
}

.dev-sw__meta {
  flex: 1;
  min-width: 0;
}

.dev-sw__name {
  font-weight: 600;
  color: $cm-color-text-1;
}

.dev-sw__ban {
  margin-left: 8rpx;
  padding: 0 8rpx;
  font-size: 18rpx;
  color: #fff;
  background: #e54d42;
  border-radius: 6rpx;
}

.dev-sw__sub {
  display: block;
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.dev-sw__spin {
  flex: none;
  font-size: 20rpx;
  color: $cm-color-text-2;
}

.dev-sw__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 24rpx;
  font-size: 20rpx;
  color: $cm-color-text-3;
  border-top: 1rpx solid $cm-color-border;
}

.dev-sw__logout {
  padding: 6rpx 20rpx;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border: 1rpx solid $cm-color-border;
  border-radius: 999rpx;
}
</style>
