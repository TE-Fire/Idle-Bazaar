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
      tokenValue: vo.tokenValue ?? ''
    })
    uni.showToast({ title: '登录成功', icon: 'success' })
    setTimeout(() => {
      if (redirect.value) {
        uni.redirectTo({ url: redirect.value })
      } else {
        uni.switchTab({ url: '/pages/index/index' })
      }
    }, 800)
  } catch {
    // request 层已提示
  }
}
</script>

<template>
  <view class="page">
    <view class="topbar">
      <text class="topbar__back" @click="goBack">‹ 返回</text>
    </view>

    <DevLoginSwitcher v-if="isDev" />

    <view class="hero">
      <text class="hero__t">登录</text>
      <text class="hero__sub">手机号 + 短信验证码，校园二手账号通用</text>
    </view>

    <view class="sec">
      <text class="sec__t">手机号</text>
      <input v-model="phone" class="input" type="number" maxlength="11" placeholder="请输入手机号" />
    </view>

    <view class="sec">
      <text class="sec__t">验证码</text>
      <view class="row">
        <input v-model="code" class="input" type="number" maxlength="6" placeholder="6 位验证码" />
        <text class="code-btn" :class="{ 'code-btn--off': !canGetCode() }" @click="sendCode">
          {{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}
        </text>
      </view>
    </view>

    <view class="submit" hover-class="submit--active" @click="submit">
      <text class="submit__t">登录</text>
    </view>

    <text class="foot">演示环境：验证码为演示回显，非真实短信</text>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-6 $cm-space-4;
  background: $cm-color-bg;
  min-height: 100vh;
}

/* 顶部返回条：uni 默认导航栏在 H5/小程序下无浏览器返回键心智，这里补一个明确的「返回」 */
.topbar {
  display: flex;
  align-items: center;
  height: 56rpx;
  margin-bottom: $cm-space-2;
}

.topbar__back {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-text-1;
  padding: $cm-space-1 $cm-space-2;
}

.hero {
  margin-bottom: $cm-space-6;
}

.hero__t {
  display: block;
  font-size: 56rpx;
  font-weight: 800;
  color: $cm-color-text-1;
}

.hero__sub {
  display: block;
  margin-top: $cm-space-2;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.sec {
  margin-bottom: $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.sec__t {
  display: block;
  margin-bottom: $cm-space-3;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
}

.input {
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
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
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: $cm-radius-md;
}

.code-btn--off {
  color: $cm-color-text-3;
}

.submit {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  margin-top: $cm-space-4;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
  transition: transform 120ms ease-out;
}

.submit__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: #fff;
}

.submit--active {
  transform: scale(0.98);
}

.foot {
  display: block;
  margin-top: $cm-space-4;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
