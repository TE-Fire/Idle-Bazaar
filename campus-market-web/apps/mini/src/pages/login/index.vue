<script setup lang="ts">
/**
 * 登录（手机号 + 短信验证码）
 *
 * <p>学生端口径只有短信一种方式（app_user 没有密码体系）。演示期验证码随发码
 * 响应回显，上线前关掉 campus.user.sms.echo 后只进日志。登录成功按 redirect 回跳。
 */

import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { userAuthApi } from '@/api/user'
import { saveSession } from '@/utils/session'
import { goTab, TAB_URL } from '@/utils/tab-nav'
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'

/** dev 构建才挂载测试账号切换器 */
const isDev = import.meta.env.DEV

const phone = ref('')
const code = ref('')
const cooldown = ref(0)
let timer: number | null = null

/** 登录成功后回跳的页面路径（从哪跳来的） */
const redirect = ref('')

const PHONE_RE = /^1[3-9]\d{9}$/
const canGetCode = (): boolean => PHONE_RE.test(phone.value) && cooldown.value === 0
const canSubmit = (): boolean => PHONE_RE.test(phone.value) && /^\d{6}$/.test(code.value)

onLoad((query) => {
  if (query?.redirect) redirect.value = query.redirect
})

/** 返回：有上一页则 navigateBack，冷启动（无栈）则回首页 */
function goBack(): void {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/index/index' })
  }
}

function startCooldown(seconds: number): void {
  cooldown.value = seconds
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function sendCode(): Promise<void> {
  if (!canGetCode()) {
    uni.showToast({ title: '请填写正确的手机号', icon: 'none' })
    return
  }
  try {
    const vo = await userAuthApi.sendCode(phone.value)
    uni.showToast({
      title: vo.devCode ? `演示验证码：${vo.devCode}` : '验证码已发送',
      icon: 'none',
      duration: 2500
    })
    startCooldown(vo.resendAfter || 60)
  } catch {
    // request 层已提示（节流 4016）；冷却不启动，可立即重试
  }
}

async function submit(): Promise<void> {
  if (!canSubmit()) {
    uni.showToast({ title: '请填写手机号与 6 位验证码', icon: 'none' })
    return
  }
  try {
    const vo = await userAuthApi.login(phone.value, code.value)
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
        // redirectTo 指向 tab 页会静默失败并停在本页，tab 页必须用 reLaunch
        const isTab = Object.values<string>(TAB_URL).includes(redirect.value)
        void (isTab ? uni.reLaunch({ url: redirect.value }) : uni.redirectTo({ url: redirect.value }))
      } else {
        goTab('home')
      }
    }, 800)
  } catch {
    // request 层已提示
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
      <text class="card__t">手机号登录</text>
      <text class="card__s">未注册的手机号将自动创建账号</text>

      <view class="field">
        <text class="field__k">手机号</text>
        <input v-model="phone" class="input" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>

      <view class="field">
        <text class="field__k">验证码</text>
        <view class="row">
          <input v-model="code" class="input" type="number" maxlength="6" placeholder="6 位验证码" />
          <text class="code-btn" :class="{ 'code-btn--off': !canGetCode() }" @click="sendCode">
            {{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}
          </text>
        </view>
      </view>

      <view class="submit cm-btn cm-btn--primary" hover-class="cm-btn--on" @click="submit">
        <text>登录</text>
      </view>

      <text class="foot">演示环境：验证码为演示回显，非真实短信</text>
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
  margin-bottom: $cm-space-5;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
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

.foot {
  display: block;
  margin-top: $cm-space-4;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
