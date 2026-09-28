<script setup lang="ts">
/**
 * A10 找回密码（business/03 F-A10，design/03 第五章「认证三页共用规范」）
 *
 * <p>做成**同页两步**而不是两个路由：第二步只依赖第一步的邮箱，
 * 拆成两页要么把邮箱塞进路由参数（可被篡改），要么再存一份状态；
 * 同一页里用 step 切换，刷新即回到第一步，反而是更安全的行为。
 *
 * <p>第一步的文案刻意不承诺「已发送」：后端对未注册邮箱**同样返回成功**
 * （否则这个接口就是枚举管理员邮箱的工具），所以提示写成
 * 「若该邮箱已注册，你将收到验证码」，与后端口径一致。
 *
 * <p>成功后强制跳登录：后端已把该账号所有会话踢下线，留在原页没有意义。
 */

import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { LockOutlined, MailOutlined } from '@ant-design/icons-vue'
import { authApi } from '@campus/api'
import CaptchaField from '@/components/CaptchaField.vue'
import { useCountdown } from '@/composables/useCountdown'
import { isQqMail } from '@/utils/mail'

const router = useRouter()
const { left: countdown, start: startCountdown } = useCountdown(60)

const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)
const captchaOn = ref(true)
const step = ref(0)
const submitting = ref(false)

const form = reactive({
  email: '',
  mailCode: '',
  password: '',
  confirm: '',
  captchaKey: '',
  captchaCode: ''
})

const rules: Record<string, Rule[]> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string) =>
        isQqMail(value ?? '') ? Promise.resolve() : Promise.reject(new Error('仅支持 QQ 邮箱（@qq.com / @foxmail.com）')),
      trigger: 'blur'
    }
  ],
  mailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度需 8–32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string) =>
        value === form.password ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
}

/** 第一步：发码。走到 step=1 后不再需要图形验证码（第二步只认邮箱码） */
const onSendCode = async (): Promise<void> => {
  try {
    await formRef.value?.validateFields(['email', 'captchaCode'])
  } catch {
    return
  }
  submitting.value = true
  try {
    await authApi.sendResetCode({
      email: form.email.trim(),
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    message.success('若该邮箱已注册，验证码将发送至邮箱，10 分钟内有效')
    startCountdown()
    step.value = 1
  } catch {
    await captchaRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}

/** 第二步：重置。成功后所有会话被踢下线，直接回登录页 */
const onReset = async (): Promise<void> => {
  try {
    await formRef.value?.validateFields(['mailCode', 'password', 'confirm'])
  } catch {
    return
  }
  submitting.value = true
  try {
    await authApi.resetPassword({
      email: form.email.trim(),
      mailCode: form.mailCode.trim(),
      password: form.password
    })
    message.success('密码已重置，请使用新密码登录')
    void router.push('/login')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="forgot">
    <div class="cm-card forgot__card">
      <div class="forgot__brand">
        <div class="forgot__title">找回密码</div>
        <div class="forgot__sub">通过 QQ 邮箱验证码重置管理端密码</div>
      </div>

      <a-steps :current="step" size="small" class="forgot__steps">
        <a-step title="验证邮箱" />
        <a-step title="设置新密码" />
      </a-steps>

      <a-form ref="formRef" :model="form" :rules="rules" size="large">
        <a-form-item name="email">
          <a-input
            v-model:value="form.email"
            placeholder="注册时使用的 QQ 邮箱"
            :disabled="step === 1"
            allow-clear
          >
            <template #prefix><MailOutlined /></template>
          </a-input>
        </a-form-item>

        <template v-if="step === 0">
          <a-form-item v-if="captchaOn" name="captchaCode">
            <CaptchaField
              ref="captchaRef"
              v-model:code="form.captchaCode"
              v-model:key="form.captchaKey"
              @disabled="captchaOn = false"
              @submit="onSendCode"
            />
          </a-form-item>

          <a-button type="primary" block :loading="submitting" @click="onSendCode">
            获取验证码
          </a-button>
        </template>

        <template v-else>
          <a-form-item name="mailCode">
            <a-input v-model:value="form.mailCode" placeholder="邮箱验证码" :maxlength="6">
              <template #addonAfter>
                <a-button
                  type="link"
                  size="small"
                  :disabled="countdown > 0"
                  @click="onSendCode"
                >
                  {{ countdown > 0 ? `${countdown}s 后重发` : '重发' }}
                </a-button>
              </template>
            </a-input>
          </a-form-item>

          <a-form-item name="password">
            <a-input-password v-model:value="form.password" placeholder="新密码（8–32 位）">
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>

          <a-form-item name="confirm">
            <a-input-password v-model:value="form.confirm" placeholder="确认新密码" @press-enter="onReset">
              <template #prefix><LockOutlined /></template>
            </a-input-password>
          </a-form-item>

          <a-button type="primary" block :loading="submitting" @click="onReset">
            重置密码
          </a-button>
        </template>
      </a-form>

      <div class="forgot__links">
        <router-link to="/login">返回登录</router-link>
        <a-divider type="vertical" />
        <router-link to="/register">注册新账号</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.forgot {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cm-color-bg);
}

.forgot__card {
  width: 400px;
  padding: var(--cm-space-6);
}

.forgot__brand {
  text-align: center;
  margin-bottom: var(--cm-space-5);
}

.forgot__title {
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.forgot__sub {
  margin-top: var(--cm-space-1);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.forgot__steps {
  margin-bottom: var(--cm-space-5);
}

.forgot__links {
  margin-top: var(--cm-space-4);
  text-align: center;
  font-size: var(--cm-font-size-sm);
}
</style>
