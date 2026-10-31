<script setup lang="ts">
/**
 * 注册（QQ 邮箱验证码 + 手机号 + 密码）
 *
 * <p>为什么只验邮箱验证码：短信在演示期是假码（不真实下发），验证它没有任何
 * 安全增益；手机号只做格式与唯一性校验。学生其实都有 QQ 邮箱，收码没有额外门槛。
 *
 * <p>前端校验全部与后端对齐（2–16 昵称、8–32 密码不含空格、QQ 系邮箱），
 * 目的是让用户在提交前就看到问题；后端仍会重校验——前端校验只是体验，不是防线。
 *
 * <p>注册成功即登录（后端直接下发令牌）：刚验完邮箱又要再输一次验证码纯属折腾。
 */

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MAIL_CODE_SCENE } from '@campus/api'
import type { UserLoginVO } from '@campus/api'
import { useUserStore } from '@/stores/user'
import { accountKind, MAIL_RE, PHONE_RE, useVerifyCode } from '@/composables/useVerifyCode'
import { rememberRegisteredUser } from '@/utils/test-accounts'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()
const session = useUserStore()

const phone = ref('')
const email = ref('')
const mailCode = ref('')
const nickname = ref('')
const password = ref('')
const confirm = ref('')
const agreed = ref(false)

const { cooldown, counting, send: sendCode } = useVerifyCode(MAIL_CODE_SCENE.register)

/** 与后端 UserAccountService 的 NICK_MIN/NICK_MAX、PWD_MIN/PWD_MAX 一致 */
const NICK_MIN = 2
const NICK_MAX = 16
const PWD_MIN = 8
const PWD_MAX = 32

const canGetCode = computed(() => accountKind(email.value) === 'mail' && !counting.value)

const nickBad = computed(
  () => nickname.value.length > 0 && (nickname.value.length < NICK_MIN || nickname.value.length > NICK_MAX)
)
const pwdBad = computed(() => password.value.length > 0 && !isPwdOk(password.value))
const confirmBad = computed(() => confirm.value.length > 0 && confirm.value !== password.value)

function isPwdOk(v: string): boolean {
  return v.length >= PWD_MIN && v.length <= PWD_MAX && !/\s/.test(v)
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
    ElMessage.warning({ message: '请先填写 QQ 邮箱（@qq.com / @foxmail.com）', duration: 2000 })
    return
  }
  void sendCode(email.value)
}

async function submit(): Promise<void> {
  if (!agreed.value) {
    ElMessage.warning({ message: '请先阅读并同意用户协议与隐私政策', duration: 2000 })
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning({ message: '请检查填写的信息是否完整正确', duration: 2000 })
    return
  }
  let vo: UserLoginVO
  try {
    vo = await session.register({
      phone: phone.value.trim(),
      email: email.value.trim(),
      nickname: nickname.value.trim(),
      password: password.value,
      mailCode: mailCode.value
    })
  } catch {
    // http 层已按错误码提示（邮箱已注册 / 手机号已注册 / 验证码错误等）
    return
  }
  // 顺手记进本机测试清单：注册完想切回新号看一眼，不必再手输手机号等验证码
  rememberRegisteredUser(vo)
  ElMessage.success({ message: '注册成功，已自动登录', duration: 1800 })
  const redirect = router.currentRoute.value.query.redirect
  void router.push(typeof redirect === 'string' ? redirect : '/')
}
</script>

<template>
  <AuthShell title="注册账号" sub="用 QQ 邮箱验证，手机号用于登录与联系">
    <form @submit.prevent="submit">
      <label class="cm-auth-field">
        <span class="cm-auth-field__key">手机号</span>
        <input
          v-model="phone"
          class="cm-auth-field__input"
          maxlength="11"
          inputmode="numeric"
          placeholder="手机号"
          autocomplete="tel"
        />
      </label>

      <label class="cm-auth-field">
        <span class="cm-auth-field__key">邮箱</span>
        <input
          v-model="email"
          class="cm-auth-field__input"
          maxlength="64"
          placeholder="@qq.com / @foxmail.com"
          autocomplete="email"
        />
      </label>

      <label class="cm-auth-field">
        <span class="cm-auth-field__key">验证码</span>
        <input
          v-model="mailCode"
          class="cm-auth-field__input"
          maxlength="6"
          inputmode="numeric"
          placeholder="6 位数字"
        />
        <button type="button" class="cm-auth-field__act" :disabled="!canGetCode" @click="onSend">
          {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
        </button>
      </label>

      <label class="cm-auth-field" :class="{ 'cm-auth-field--bad': nickBad }">
        <span class="cm-auth-field__key">昵称</span>
        <input
          v-model="nickname"
          class="cm-auth-field__input"
          maxlength="16"
          placeholder="2–16 个字符"
        />
      </label>

      <label class="cm-auth-field" :class="{ 'cm-auth-field--bad': pwdBad }">
        <span class="cm-auth-field__key">密码</span>
        <input
          v-model="password"
          class="cm-auth-field__input"
          type="password"
          maxlength="32"
          placeholder="8–32 位，不含空格"
          autocomplete="new-password"
        />
      </label>

      <label class="cm-auth-field" :class="{ 'cm-auth-field--bad': confirmBad }">
        <span class="cm-auth-field__key">确认密码</span>
        <input
          v-model="confirm"
          class="cm-auth-field__input"
          type="password"
          maxlength="32"
          placeholder="再输一次"
          autocomplete="new-password"
        />
      </label>

      <p v-if="nickBad" class="hint">昵称需 2–16 个字符</p>
      <p v-else-if="pwdBad" class="hint">密码需 8–32 位，且不能包含空格</p>
      <p v-else-if="confirmBad" class="hint">两次输入的密码不一致</p>

      <button type="submit" class="cm-auth-submit" :disabled="!canSubmit">注册</button>

      <label class="cm-auth-agree">
        <input v-model="agreed" type="checkbox" />
        <span>已阅读并同意<a href="javascript:void(0)">《用户协议》</a>与<a href="javascript:void(0)">《隐私政策》</a></span>
      </label>

      <div class="cm-auth-links">
        <span>已有账号</span>
        <RouterLink :to="{ name: 'login' }">去登录</RouterLink>
        <span class="cm-auth-links__sep">·</span>
        <RouterLink :to="{ name: 'forgot' }">忘记密码</RouterLink>
      </div>
    </form>
  </AuthShell>
</template>

<style scoped lang="scss">
/* 校验不通过时的字段描边：只提示，不阻止继续输入 */
.cm-auth-field--bad {
  box-shadow: inset 0 0 0 1px var(--cm-color-err);
}

.hint {
  margin: calc(var(--cm-space-2) * -1) 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-err);
}
</style>
