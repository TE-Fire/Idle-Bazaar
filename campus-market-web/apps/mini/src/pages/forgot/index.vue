<script setup lang="ts">
/**
 * 找回密码（手机号短信 / QQ 邮箱邮件，渠道由填的账号决定）
 *
 * <p>两种渠道都通：注册只验了邮箱，但用户平时用手机号登录——手机号通常记得，
 * 邮箱可能是随手注册的小号，只留一条路会把人卡死。
 *
 * <p>改密成功后后端踢掉该账号所有会话，所以这里不能「改完继续用」，必须回登录页重登；
 * 账号带到 query 里预填，省一次输入。
 */

import { computed, ref } from 'vue'
import { MAIL_CODE_SCENE, userAuthApi } from '@/api/user'
import { accountKind, useCodeSender } from '@/utils/verify-code'

const account = ref('')
const code = ref('')
const password = ref('')
const confirm = ref('')
const submitting = ref(false)

const { cooldown, counting, send } = useCodeSender(MAIL_CODE_SCENE.reset)

const kind = computed(() => accountKind(account.value))
const canGetCode = computed(() => Boolean(kind.value) && !counting.value)

const pwdBad = computed(() => password.value.length > 0 && !isPwdOk(password.value))
const confirmBad = computed(() => confirm.value.length > 0 && confirm.value !== password.value)

function isPwdOk(v: string): boolean {
  return v.length >= 8 && v.length <= 32 && !/\s/.test(v)
}

const canSubmit = computed(
  () => Boolean(kind.value) && /^\d{6}$/.test(code.value) && isPwdOk(password.value) && confirm.value === password.value
)

function onSend(): void {
  if (!kind.value) {
    uni.showToast({ title: '请输入手机号或 QQ 邮箱', icon: 'none' })
    return
  }
  void send(account.value)
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!canSubmit.value) {
    uni.showToast({ title: '请检查账号、验证码与新密码', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await userAuthApi.resetPassword({
      account: account.value.trim(),
      code: code.value,
      password: password.value
    })
  } catch {
    // request 层已按错误码提示（未注册 / 验证码错 / 新旧密码相同等）
    return
  } finally {
    submitting.value = false
  }
  uni.showToast({ title: '密码已重置，请重新登录', icon: 'none' })
  // 旧会话已被后端踢掉，回登录页重登；账号带上预填
  setTimeout(() => {
    uni.reLaunch({ url: `/pages/login/index?account=${account.value.trim()}` })
  }, 900)
}

function goLogin(): void {
  uni.navigateBack()
}
</script>

<template>
  <view class="page cm-page">
    <view class="hero">
      <text class="hero__t">找回密码</text>
      <text class="hero__s">填注册时的手机号或 QQ 邮箱，收验证码后设新密码</text>
    </view>

    <view class="card cm-card">
      <view class="field">
        <text class="field__k">账号</text>
        <input v-model="account" class="input" maxlength="64" placeholder="手机号或 QQ 邮箱" />
      </view>

      <view class="field">
        <text class="field__k">验证码</text>
        <view class="row">
          <input v-model="code" class="input" type="number" maxlength="6" placeholder="6 位验证码" />
          <text class="code-btn" :class="{ 'code-btn--off': !canGetCode }" @click="onSend">
            {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
          </text>
        </view>
      </view>

      <view class="field">
        <text class="field__k">新密码</text>
        <input v-model="password" class="input" password maxlength="32" placeholder="8–32 位，不含空格" />
      </view>

      <view class="field">
        <text class="field__k">确认新密码</text>
        <input v-model="confirm" class="input" password maxlength="32" placeholder="再输一次新密码" />
      </view>

      <text v-if="pwdBad" class="hint">密码需 8–32 位，且不能包含空格</text>
      <text v-else-if="confirmBad" class="hint">两次输入的密码不一致</text>

      <view class="submit cm-btn cm-btn--primary" hover-class="cm-btn--on" @click="submit">
        <text>{{ submitting ? '提交中…' : '重置密码' }}</text>
      </view>

      <view class="links">
        <text class="link" @click="goLogin">返回登录</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: $cm-space-8;
}

.hero {
  padding: $cm-space-6 $cm-space-5 $cm-space-5;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.hero__t {
  font-size: $cm-font-size-2xl;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

.hero__s {
  font-size: $cm-font-size-xs;
  line-height: 1.6;
  color: $cm-color-text-3;
}

.card {
  margin: 0 $cm-space-4;
  padding: $cm-space-5;
  border-radius: $cm-radius-3xl;
  box-shadow: $cm-shadow-md;
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
}

.code-btn--off {
  color: $cm-color-text-3;
  background: $cm-color-gray-100;
}

.hint {
  display: block;
  margin: -#{$cm-space-1} 0 $cm-space-3;
  font-size: $cm-font-size-xs;
  color: $cm-color-err;
}

.submit {
  margin-top: $cm-space-4;
  height: 92rpx;
  font-size: $cm-font-size-md;
}

.links {
  display: flex;
  justify-content: center;
  margin-top: $cm-space-4;
}

.link {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand-text;
}
</style>
