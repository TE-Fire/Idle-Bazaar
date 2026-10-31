<script setup lang="ts">
/**
 * 登录（验证码登录 / 密码登录）
 *
 * <p>验证码登录的账号可以是手机号（短信）或 QQ 邮箱（邮件），渠道由后端按账号形态判定。
 * 密码只对新注册用户存在——老账号没设过密码，走「找回密码」设一个，所以 4038 的
 * 提示要能指向那条路（后端文案已写明）。
 *
 * <p>演示期验证码随发码响应回显，上线前关掉 campus.user.*.echo 后只进日志。
 * 登录成功按 redirect 回跳，tab 页必须 reLaunch（redirectTo 对 tab 页静默失败）。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { MAIL_CODE_SCENE, USER_LOGIN_TYPE, userAuthApi } from '@/api/user'
import { saveSession } from '@/utils/session'
import { goTab, TAB_URL } from '@/utils/tab-nav'
import { accountKind, useCodeSender } from '@/utils/verify-code'
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'

/** dev 构建才挂载测试账号切换器 */
const isDev = import.meta.env.DEV

const mode = ref<number>(USER_LOGIN_TYPE.code)
const account = ref('')
const code = ref('')
const password = ref('')

const { cooldown, counting, send } = useCodeSender(MAIL_CODE_SCENE.login)

/** 登录成功后回跳的页面路径（从哪跳来的） */
const redirect = ref('')

const kind = computed(() => accountKind(account.value))
const canGetCode = computed(() => Boolean(kind.value) && !counting.value)
const canSubmit = computed(() => {
  if (!kind.value) return false
  return mode.value === USER_LOGIN_TYPE.code
    ? /^\d{6}$/.test(code.value)
    : password.value.length >= 8
})

onLoad((query) => {
  if (query?.redirect) redirect.value = query.redirect
  // 找回密码跳回来时带 ?account=，预填省一次输入
  if (query?.account) account.value = query.account
})

/** 返回：有上一页则 navigateBack，冷启动（无栈）则回首页 */
function goBack(): void {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/index/index' })
  }
}

/** 切换方式时清掉另一种方式的凭证，避免串台提交 */
function switchMode(next: number): void {
  if (mode.value === next) return
  mode.value = next
  code.value = ''
  password.value = ''
}

function onSend(): void {
  if (!kind.value) {
    uni.showToast({ title: '请输入手机号或 QQ 邮箱', icon: 'none' })
    return
  }
  void send(account.value)
}

function goRegister(): void {
  uni.navigateTo({ url: '/pages/register/index' })
}

function goForgot(): void {
  uni.navigateTo({ url: '/pages/forgot/index' })
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    uni.showToast({
      title: mode.value === USER_LOGIN_TYPE.code ? '请填写账号与 6 位验证码' : '请填写账号与至少 8 位密码',
      icon: 'none'
    })
    return
  }
  const credential = mode.value === USER_LOGIN_TYPE.code ? code.value : password.value
  try {
    const vo = await userAuthApi.login(account.value.trim(), mode.value as 1 | 2, credential)
    saveSession({
      userId: vo.userId,
      nickname: vo.nickname ?? '',
      avatarUrl: vo.avatarUrl ?? '',
      tokenName: vo.tokenName ?? '',
      tokenValue: vo.tokenValue ?? '',
      authStatus: vo.authStatus
    })
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      if (redirect.value) {
        const isTab = Object.values<string>(TAB_URL).includes(redirect.value)
        void (isTab ? uni.reLaunch({ url: redirect.value }) : uni.redirectTo({ url: redirect.value }))
      } else {
        goTab('home')
      }
    }, 800)
  } catch {
    // request 层已按错误码提示
  }
}

</script>

<template>
  <view class="page cm-page">
    <!-- 品牌头：渐变满铺到左右边，表单卡压在它下沿 -->
    <view class="brand">
      <view class="brand__glow" />
      <view class="brand__glow brand__glow--b" />

      <view class="topbar">
        <view class="back" hover-class="back--on" @click="goBack">‹</view>
      </view>

      <view class="brand__row">
        <view class="brand__mark">校</view>
        <view class="brand__meta">
          <text class="brand__t">校园二手</text>
          <text class="brand__s">同学们都在用的闲置交易</text>
        </view>
      </view>
    </view>

    <view class="card cm-card">
      <text class="card__t">登录</text>
      <text class="card__s">支持手机号与 QQ 邮箱</text>

      <view class="tabs">
        <view class="tab" :class="{ 'tab--on': mode === 1 }" @click="switchMode(1)">验证码登录</view>
        <view class="tab" :class="{ 'tab--on': mode === 2 }" @click="switchMode(2)">密码登录</view>
      </view>

      <view class="field">
        <text class="field__k">账号</text>
        <input v-model="account" class="input" maxlength="64" placeholder="手机号或 QQ 邮箱" />
      </view>

      <view v-if="mode === 1" class="field">
        <text class="field__k">验证码</text>
        <view class="row">
          <input v-model="code" class="input" type="number" maxlength="6" placeholder="6 位验证码" />
          <text class="code-btn" :class="{ 'code-btn--off': !canGetCode }" @click="onSend">
            {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
          </text>
        </view>
      </view>

      <view v-else class="field">
        <text class="field__k">密码</text>
        <input v-model="password" class="input" password maxlength="32" placeholder="8–32 位密码" />
      </view>

      <view class="submit cm-btn cm-btn--primary" hover-class="cm-btn--on" @click="submit">
        <text>登录</text>
      </view>

      <view class="links">
        <text class="link" @click="goRegister">注册账号</text>
        <text class="links__sep">·</text>
        <text class="link" @click="goForgot">忘记密码</text>
      </view>

      <text class="foot">演示环境：验证码为演示回显，非真实短信/邮件</text>
    </view>

    <DevLoginSwitcher v-if="isDev" />
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
}

/* ── 品牌头 ─────────────────────────────────────────────── */
.brand {
  position: relative;
  /* 自定义导航（pages.json 已置 navigationStyle: custom）：自己让出状态栏高度，
     H5 下该变量为 0，小程序下由 uni 注入真实值 */
  padding: calc(var(--status-bar-height, 0px) + #{$cm-space-2}) $cm-space-4 $cm-space-8;
  background-image: linear-gradient(140deg, #FFA05A 0%, $cm-color-brand 58%, $cm-color-brand-2 100%);
  overflow: hidden;
}

.brand__glow {
  position: absolute;
  right: -100rpx;
  top: -140rpx;
  width: 340rpx;
  height: 340rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0));
}

.brand__glow--b {
  right: auto;
  left: -80rpx;
  top: auto;
  bottom: -160rpx;
  width: 280rpx;
  height: 280rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0));
}

/* 顶部返回条：uni 默认导航栏在 H5/小程序下无浏览器返回键心智，这里补一个明确的「返回」 */
.topbar {
  position: relative;
  display: flex;
  align-items: center;
  height: 64rpx;
}

.back {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 4rpx;
  font-size: 40rpx;
  line-height: 1;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  border: 1rpx solid rgba(255, 255, 255, 0.4);
  transition: transform 140ms ease-out;
}

.back--on {
  transform: scale(0.92);
}

.brand__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  margin-top: $cm-space-4;
}

.brand__mark {
  width: 96rpx;
  height: 96rpx;
  border-radius: $cm-radius-2xl;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-brand;
  background: $cm-color-surface;
  box-shadow: 0 8rpx 20rpx rgba(120, 40, 0, 0.18);
}

.brand__meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.brand__t {
  font-size: $cm-font-size-xl;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
  letter-spacing: 1rpx;
}

.brand__s {
  font-size: $cm-font-size-xs;
  color: rgba(255, 255, 255, 0.86);
}

/* ── 表单卡 ─────────────────────────────────────────────── */
.card {
  position: relative;
  margin: -$cm-space-6 $cm-space-4 0;
  padding: $cm-space-5;
  border-radius: $cm-radius-3xl;
  box-shadow: $cm-shadow-md;
}

.card__t {
  display: block;
  font-size: $cm-font-size-lg;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

.card__s {
  display: block;
  margin-top: 6rpx;
  margin-bottom: $cm-space-4;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* 方式切换：下划线式 tab，不与输入框抢视觉重量 */
.tabs {
  display: flex;
  gap: $cm-space-6;
  margin-bottom: $cm-space-4;
}

.tab {
  padding-bottom: $cm-space-2;
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-3;
  border-bottom: 4rpx solid transparent;
}

.tab--on {
  color: $cm-color-text-1;
  border-bottom-color: $cm-color-brand;
}

.field {
  margin-bottom: $cm-space-3;
}

.field__k {
  display: block;
  margin-bottom: $cm-space-2;
  font-size: $cm-font-size-xs;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-2;
}

.input {
  height: 88rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-xl;
  border: 1rpx solid transparent;
  transition: border-color 180ms ease-out, background 180ms ease-out;
}

.row {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.row .input {
  flex: 1;
  min-width: 0;
}

.code-btn {
  flex: none;
  height: 88rpx;
  line-height: 88rpx;
  padding: 0 $cm-space-4;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: $cm-radius-xl;
  transition: opacity 180ms ease-out, transform 140ms ease-out;
}

.code-btn--off {
  color: $cm-color-text-3;
  background: $cm-color-gray-100;
}

.submit {
  margin-top: $cm-space-5;
  height: 92rpx;
  font-size: $cm-font-size-md;
}

.links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $cm-space-2;
  margin-top: $cm-space-4;
}

.link {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand-text;
}

.links__sep {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.foot {
  display: block;
  margin-top: $cm-space-3;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
