<script setup lang="ts">
/**
 * 本地联调用「测试账号」切换器（折叠下拉，H5 / 小程序通用）
 *
 * <p>在 App.vue 里全局挂载，所有页面右上角出现一个可折叠的演示账号面板。
 * 点击账号即走 send-code → 取 devCode → login 全自动登录，免去手填验证码，
 * 方便在多个演示账号间快速切换测登录态相关流程。生产构建不挂载，不影响线上。
 */

import { computed, ref } from 'vue'
import { MAIL_CODE_SCENE, USER_LOGIN_TYPE, userAuthApi, type DevAccountVO } from '@/api/user'
import { saveSession, getSession, clearSession } from '@/utils/session'
import { DEMO_USERS, authLabel, type TestAccount } from '@campus/shared'
import { forgetTestAccount, listTestAccounts } from '@/utils/test-accounts'

/** 浮层停靠侧：默认右下；首页右下有发布 FAB，传 left 错开 */
const props = withDefaults(
  defineProps<{ position?: 'right' | 'left' }>(),
  { position: 'right' }
)

const open = ref(false)
const loadingPhone = ref('')
const current = ref(getSession().nickname || '')
const currentId = ref(getSession().userId || '')

/** 刚注册的账号：打开面板时重读，注册完不必重启小程序就能看到 */
const recent = ref<TestAccount[]>(listTestAccounts())

/**
 * 服务端账号：本机 storage 只记得「这台手机上注册过」的账号，
 * Web 端注册的收不到——打开面板时向服务端要一份最新的（验证码回显关闭时为空）。
 */
const remote = ref<DevAccountVO[]>([])
const remoteLoading = ref(false)

const demoPhones = computed(() => new Set(DEMO_USERS.map((u) => u.phone)))

/** 服务端列表剔掉演示账号（下面有独立分组），并只留有手机号的（切换靠验证码登录） */
const serverAccounts = computed(() =>
  remote.value.filter((u) => u.phone && !demoPhones.value.has(u.phone))
)

/** 本机记录里、服务端列表没覆盖到的（例如服务端未开启回显时退回本机记忆） */
const localOnly = computed(() => {
  const seen = new Set(remote.value.map((u) => u.id))
  return recent.value.filter((u) => !seen.has(u.id))
})

async function loadRemote(): Promise<void> {
  remoteLoading.value = true
  try {
    remote.value = await userAuthApi.devAccounts()
  } catch {
    // 老后端没有这个端点：退回本机记录 + 演示账号，不影响切换
    remote.value = []
  } finally {
    remoteLoading.value = false
  }
}

function toggle(): void {
  if (open.value) {
    open.value = false
    return
  }
  recent.value = listTestAccounts()
  open.value = true
  void loadRemote()
}

/** 两组的条目形态一致，只取昵称与手机号：演示账号与刚注册账号共用一条切换路径 */
/** 三组条目的 id 类型不同（演示账号是数字常量、服务端与本地记录是雪花字符串），这里统一放宽 */
async function quickLogin(u: { id?: string | number; nickname: string; phone: string }): Promise<void> {
  loadingPhone.value = u.phone
  try {
    const sms = await userAuthApi.sendCode(u.phone, MAIL_CODE_SCENE.login)
    if (!sms.devCode) {
      uni.showToast({ title: 'devCode 未回显，请确认 echo=true', icon: 'none' })
      return
    }
    const vo = await userAuthApi.login(u.phone, USER_LOGIN_TYPE.code, sms.devCode)
    saveSession({
      userId: vo.userId ?? '',
      nickname: vo.nickname ?? '',
      avatarUrl: vo.avatarUrl ?? '',
      tokenName: vo.tokenName ?? '',
      tokenValue: vo.tokenValue ?? ''
    })
    current.value = vo.nickname ?? ''
    currentId.value = vo.userId ?? ''
    open.value = false
    uni.showToast({ title: `已切换为 ${u.nickname}`, icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.msg || e?.message || '登录失败', icon: 'none' })
  } finally {
    loadingPhone.value = ''
  }
}

function forget(id: string): void {
  forgetTestAccount(id)
  recent.value = listTestAccounts()
}

function logout(): void {
  clearSession()
  current.value = ''
  currentId.value = ''
  open.value = false
  uni.showToast({ title: '已退出', icon: 'none' })
}
</script>

<template>
  <view class="dev-sw" :class="{ 'dev-sw--left': position === 'left' }">
    <view v-if="!open" class="dev-sw__fab" @click="toggle">
      <text class="dev-sw__dot" />
      <text>测试账号 · {{ current || '未登录' }}</text>
      <text class="dev-sw__caret">▲</text>
    </view>

    <view v-else class="dev-sw__panel">
      <view class="dev-sw__head">
        <text>切换登录账号（测试）</text>
        <text class="dev-sw__close" @click="open = false">▼</text>
      </view>

      <scroll-view class="dev-sw__list" scroll-y>
        <view v-if="remoteLoading" class="dev-sw__group">服务端账号加载中…</view>

        <template v-if="serverAccounts.length">
          <view class="dev-sw__group">服务端账号 · 含 Web 端注册</view>
          <view
            v-for="u in serverAccounts"
            :key="u.id"
            class="dev-sw__item"
            :class="{ 'is-current': currentId && currentId === u.id }"
            @click="quickLogin({ id: u.id, nickname: u.nickname || u.phone || '未命名', phone: u.phone! })"
          >
            <text class="dev-sw__avatar">{{ (u.nickname || u.phone || '?').slice(0, 1) }}</text>
            <view class="dev-sw__meta">
              <text class="dev-sw__name">{{ u.nickname || u.phone }}</text>
              <text class="dev-sw__sub">{{ u.phone }} · 信用{{ u.creditScore ?? 100 }}</text>
            </view>
            <text v-if="loadingPhone === u.phone" class="dev-sw__spin">登录中…</text>
          </view>
        </template>

        <template v-if="localOnly.length">
          <view class="dev-sw__group">本机记录</view>
          <view
            v-for="u in localOnly"
            :key="u.id"
            class="dev-sw__item"
            :class="{ 'is-current': currentId && currentId === u.id }"
            @click="quickLogin(u)"
          >
            <text class="dev-sw__avatar">{{ u.nickname.slice(0, 1) }}</text>
            <view class="dev-sw__meta">
              <text class="dev-sw__name">
                {{ u.nickname }}
                <text class="dev-sw__new">新</text>
              </text>
              <text class="dev-sw__sub">{{ u.phone }} · 信用{{ u.creditScore }}</text>
            </view>
            <text v-if="loadingPhone === u.phone" class="dev-sw__spin">登录中…</text>
            <text v-else class="dev-sw__del" @click.stop="forget(u.id)">×</text>
          </view>
        </template>

        <view class="dev-sw__group">演示账号</view>
        <view
          v-for="u in DEMO_USERS"
          :key="u.id"
          class="dev-sw__item"
          :class="{ 'is-current': currentId && currentId === String(u.id) }"
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
  /* 抬起到底部导航之上：自绘 dock 高 116rpx，压住会点不到 */
  bottom: calc(#{$cm-layout-tabbar-h} + 24rpx + env(safe-area-inset-bottom));
  z-index: 9999;
  font-size: 22rpx;
}

/* 停靠左侧：避开右下角的悬浮元素 */
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

/* 分组小标题：刚注册 与 演示账号 靠它分隔 */
.dev-sw__group {
  padding: 12rpx 16rpx 4rpx;
  font-size: 18rpx;
  font-weight: 600;
  color: $cm-color-text-3;
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

/* 「新」标：品牌黄底深字，与封禁的红色区分开 */
.dev-sw__new {
  margin-left: 8rpx;
  padding: 0 8rpx;
  font-size: 18rpx;
  color: #1a1a1a;
  background: $cm-color-brand;
  border-radius: 6rpx;
}

.dev-sw__del {
  flex: none;
  padding: 0 10rpx;
  font-size: 30rpx;
  line-height: 1;
  color: $cm-color-text-3;
}

.dev-sw__sub {
  display: block;
  font-size: 20rpx;
  color: $cm-color-text-3;
  /* 一行放不下就截断：别让它折成两行把条目撑高 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
