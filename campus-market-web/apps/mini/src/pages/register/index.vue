<script setup lang="ts">
/**
 * 注册（QQ 邮箱验证码 + 手机号 + 密码）
 *
 * <p>只验邮箱验证码：短信在演示期是假码，验它没有安全增益；手机号只做格式与唯一性校验。
 *
 * <p>前端校验与后端对齐（昵称 2–16、密码 8–32 不含空格、QQ 系邮箱），
 * 目的是让用户在提交前就发现问题；后端仍会重校验——前端校验是体验，不是防线。
 *
 * <p>注册成功即登录（后端直接下发令牌），不必再输一次验证码。
 */

import { computed, ref } from 'vue'
import { MAIL_CODE_SCENE, userAuthApi } from '@/api/user'
import { saveSession } from '@/utils/session'
import { rememberRegisteredUser } from '@/utils/test-accounts'
import { accountKind, MAIL_RE, PHONE_RE, useCodeSender } from '@/utils/verify-code'
import CmNav from '@/components/CmNav.vue'

const phone = ref('')
const email = ref('')
const mailCode = ref('')
const nickname = ref('')
const password = ref('')
const confirm = ref('')
const agreed = ref(false)
const submitting = ref(false)

const { cooldown, counting, send } = useCodeSender(MAIL_CODE_SCENE.register)

const NICK_MIN = 2
const NICK_MAX = 16

const canGetCode = computed(() => accountKind(email.value) === 'mail' && !counting.value)

const nickBad = computed(
  () => nickname.value.length > 0 && (nickname.value.length < NICK_MIN || nickname.value.length > NICK_MAX)
)
const pwdBad = computed(() => password.value.length > 0 && !isPwdOk(password.value))
const confirmBad = computed(() => confirm.value.length > 0 && confirm.value !== password.value)

function isPwdOk(v: string): boolean {
  return v.length >= 8 && v.length <= 32 && !/\s/.test(v)
}

const canSubmit = computed(
  () =>
    agreed.value &&
    PHONE_RE.test(phone.value.trim()) &&
    MAIL_RE.test(email.value.trim()) &&
    /^\d{6}$/.test(mailCode.value) &&
    nickname.value.trim().length >= NICK_MIN &&
    nickname.value.trim().length <= NICK_MAX &&
    isPwdOk(password.value) &&
    confirm.value === password.value
)

function onSend(): void {
  if (!accountKind(email.value)) {
    uni.showToast({ title: '请先填写 QQ 邮箱', icon: 'none' })
    return
  }
  void send(email.value)
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!agreed.value) {
    uni.showToast({ title: '请先同意用户协议与隐私政策', icon: 'none' })
    return
  }
  if (!canSubmit.value) {
    uni.showToast({ title: '请检查填写的信息', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const vo = await userAuthApi.register({
      phone: phone.value.trim(),
      email: email.value.trim(),
      nickname: nickname.value.trim(),
      password: password.value,
      mailCode: mailCode.value
    })
    saveSession({
      userId: vo.userId,
      nickname: vo.nickname ?? '',
      avatarUrl: vo.avatarUrl ?? '',
      tokenName: vo.tokenName ?? '',
      tokenValue: vo.tokenValue ?? '',
      authStatus: vo.authStatus
    })
    // 顺手记进本机测试清单：注册完想切回新号看一眼，不必再手输手机号等验证码
    rememberRegisteredUser(vo)
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/index/index' }), 800)
  } catch {
    // request 层已按错误码提示（邮箱已注册 / 手机号已注册 / 验证码错误等）
  } finally {
    submitting.value = false
  }
}

function goLogin(): void {
  uni.navigateBack()
}
</script>

<template>
  <view class="page cm-page">
    <!-- 页内 hero 已有大标题，导航栏只负责返回 -->
    <CmNav />

    <view class="hero">
      <text class="hero__t">注册账号</text>
      <text class="hero__s">用 QQ 邮箱验证，手机号用于登录与联系</text>
    </view>

    <view class="card cm-card">
      <view class="field">
        <text class="field__k">手机号</text>
        <input v-model="phone" class="input" type="number" maxlength="11" placeholder="11 位手机号" />
      </view>

      <view class="field">
        <text class="field__k">QQ 邮箱</text>
        <input v-model="email" class="input" maxlength="64" placeholder="@qq.com 或 @foxmail.com" />
      </view>

      <view class="field">
        <text class="field__k">邮箱验证码</text>
        <view class="row">
          <input v-model="mailCode" class="input" type="number" maxlength="6" placeholder="6 位验证码" />
          <text class="code-btn" :class="{ 'code-btn--off': !canGetCode }" @click="onSend">
            {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
          </text>
        </view>
      </view>

      <view class="field">
        <text class="field__k">昵称</text>
        <input v-model="nickname" class="input" maxlength="16" placeholder="2–16 个字符" />
      </view>

      <view class="field">
        <text class="field__k">密码</text>
        <input v-model="password" class="input" password maxlength="32" placeholder="8–32 位，不含空格" />
      </view>

      <view class="field">
        <text class="field__k">确认密码</text>
        <input v-model="confirm" class="input" password maxlength="32" placeholder="再输一次密码" />
      </view>

      <text v-if="nickBad" class="hint">昵称需 2–16 个字符</text>
      <text v-else-if="pwdBad" class="hint">密码需 8–32 位，且不能包含空格</text>
      <text v-else-if="confirmBad" class="hint">两次输入的密码不一致</text>

      <view class="submit cm-btn cm-btn--primary" hover-class="cm-btn--on" @click="submit">
        <text>{{ submitting ? '提交中…' : '注册' }}</text>
      </view>

      <view class="agree" @click="agreed = !agreed">
        <view class="agree__box" :class="{ 'agree__box--on': agreed }">
          <text v-if="agreed" class="agree__tick">✓</text>
        </view>
        <text class="agree__t">已阅读并同意《用户协议》与《隐私政策》</text>
      </view>

      <view class="links">
        <text class="link" @click="goLogin">已有账号，去登录</text>
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

/* 校验提示：只提示不拦截，用户继续改即可 */
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

.agree {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  margin-top: $cm-space-4;
}

.agree__box {
  width: 34rpx;
  height: 34rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid $cm-color-border-strong;
  border-radius: 8rpx;
  background: $cm-color-surface;
}

.agree__box--on {
  background: $cm-color-brand;
  border-color: $cm-color-brand;
}

.agree__tick {
  font-size: 22rpx;
  line-height: 1;
  color: $cm-color-text-1;
}

.agree__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
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
