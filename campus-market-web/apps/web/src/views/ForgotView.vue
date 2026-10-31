<script setup lang="ts">
/**
 * 找回密码（手机号短信 / QQ 邮箱邮件，二选一由填的账号决定）
 *
 * <p>为什么允许两种渠道：注册只验了邮箱，但用户平时登录用手机号——
 * 手机号通常是记得的，邮箱可能是随手注册的小号。两条路都通，才不至于有人卡死。
 *
 * <p>改密成功后后端会踢掉该账号所有会话（notes/25），所以这里不能"改完继续用"，
 * 必须回登录页重登；账号带到 query 里预填，省一次输入。
 *
 * <p>「新密码不能与旧密码相同」这一步发生在校验验证码之前（后端保证），
 * 输重复密码不会白白消耗掉一条验证码。
 */

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MAIL_CODE_SCENE, userAuthApi } from '@campus/api'
import { accountKind, useVerifyCode } from '@/composables/useVerifyCode'
import AuthShell from '@/components/AuthShell.vue'

const router = useRouter()

const account = ref('')
const code = ref('')
const password = ref('')
const confirm = ref('')
const submitting = ref(false)

const { cooldown, counting, send: sendCode } = useVerifyCode(MAIL_CODE_SCENE.reset)

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
    ElMessage.warning({ message: '请输入手机号或 QQ 邮箱', duration: 2000 })
    return
  }
  void sendCode(account.value)
}

async function submit(): Promise<void> {
  if (!canSubmit.value || submitting.value) {
    ElMessage.warning({ message: '请检查账号、验证码与新密码', duration: 2000 })
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
    // http 层已按错误码提示（未注册 / 验证码错 / 新旧密码相同等）
    return
  } finally {
    submitting.value = false
  }
  ElMessage.success({ message: '密码已重置，请用新密码登录', duration: 2000 })
  void router.push({ name: 'login', query: { account: account.value.trim() } })
}
</script>

<template>
  <AuthShell title="找回密码" sub="填注册时的手机号或 QQ 邮箱，收验证码后设新密码">
    <form @submit.prevent="submit">
      <label class="cm-auth-field">
        <span class="cm-auth-field__key">账号</span>
        <input
          v-model="account"
          class="cm-auth-field__input"
          maxlength="64"
          placeholder="手机号或 QQ 邮箱"
          autocomplete="username"
        />
      </label>

      <label class="cm-auth-field">
        <span class="cm-auth-field__key">验证码</span>
        <input
          v-model="code"
          class="cm-auth-field__input"
          maxlength="6"
          inputmode="numeric"
          placeholder="6 位验证码"
        />
        <button type="button" class="cm-auth-field__act" :disabled="!canGetCode" @click="onSend">
          {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
        </button>
      </label>

      <label class="cm-auth-field" :class="{ 'cm-auth-field--bad': pwdBad }">
        <span class="cm-auth-field__key">新密码</span>
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

      <p v-if="pwdBad" class="hint">密码需 8–32 位，且不能包含空格</p>
      <p v-else-if="confirmBad" class="hint">两次输入的密码不一致</p>

      <button type="submit" class="cm-auth-submit" :disabled="!canSubmit || submitting">
        {{ submitting ? '提交中…' : '重置密码' }}
      </button>

      <div class="cm-auth-links">
        <RouterLink :to="{ name: 'login' }">返回登录</RouterLink>
        <span class="cm-auth-links__sep">·</span>
        <RouterLink :to="{ name: 'register' }">注册新账号</RouterLink>
      </div>
    </form>
  </AuthShell>
</template>

<style scoped lang="scss">
.cm-auth-field--bad {
  box-shadow: inset 0 0 0 1px var(--cm-color-err);
}

.hint {
  margin: calc(var(--cm-space-2) * -1) 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-err);
}
</style>
