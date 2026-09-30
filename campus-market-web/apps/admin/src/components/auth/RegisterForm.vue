<script setup lang="ts">
/**
 * 注册表单（A0 表单部分，外壳在 LoginView；business/03 F-A0）
 *
 * <p>注册成功后是 **status=2 待分配角色**，登录会被后端 4006 拦下——
 * 所以提交成功的反馈不是「去登录」，而是明确告知「待超级管理员分配角色后可登录」。
 * 否则用户注册完立刻登录失败，会以为注册没成功。
 *
 * <p>为什么没有「选择角色」下拉：角色只能由超管在 A9 分配（已决 2026-09-29），
 * 页面上给一个角色选项，等于把权限模型的口子开在前端。
 *
 * <p>获取邮箱验证码时要先过图形验证码；发完码图形验证码即作废，
 * 必须换一张给注册提交用——这两步是**两个不同的验证码**，别复用同一个 key。
 */
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons-vue'
import { authApi, MAIL_CODE_SCENE } from '@campus/api'
import CaptchaField from '@/components/CaptchaField.vue'
import { useCountdown } from '@/composables/useCountdown'
import { isQqMail } from '@/utils/mail'

const router = useRouter()
const { left: countdown, start: startCountdown } = useCountdown(60)

const formRef = ref<FormInstance>()
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)
const captchaOn = ref(true)
const submitting = ref(false)
const sending = ref(false)
const done = ref(false)

const form = reactive({
  email: '',
  mailCode: '',
  password: '',
  confirm: '',
  realName: '',
  captchaKey: '',
  captchaCode: ''
})

const rules: Record<string, Rule[]> = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string) =>
        isQqMail(value ?? '')
          ? Promise.resolve()
          : Promise.reject(new Error('仅支持 QQ 邮箱（@qq.com / @foxmail.com）')),
      trigger: 'blur'
    }
  ],
  mailCode: [
    { required: true, message: '请输入邮箱验证码', trigger: 'blur' },
    { len: 6, message: '验证码为 6 位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 32, message: '密码长度需 8–32 位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r: unknown, value: string) =>
        value === form.password ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ],
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
}

const sendLabel = computed(() => (countdown.value > 0 ? `${countdown.value}s 后重发` : '获取验证码'))

/** 发邮箱验证码：只校验邮箱与图形验证码，不牵动密码字段 */
const onSendCode = async (): Promise<void> => {
  try {
    await formRef.value?.validateFields(['email', 'captchaCode'])
  } catch {
    return
  }
  sending.value = true
  try {
    await authApi.sendCode({
      type: MAIL_CODE_SCENE.register,
      email: form.email.trim(),
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    message.success('验证码已发送至邮箱，10 分钟内有效')
    startCountdown()
    // 图形验证码一次性：发码用掉后必须换一张给注册提交用
    await captchaRef.value?.refresh()
  } catch {
    // 失败提示由请求层统一 toast
    await captchaRef.value?.refresh()
  } finally {
    sending.value = false
  }
}

const onSubmit = async (): Promise<void> => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await authApi.register({
      email: form.email.trim(),
      mailCode: form.mailCode.trim(),
      password: form.password,
      realName: form.realName.trim() || undefined,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    done.value = true
  } catch {
    await captchaRef.value?.refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div>
    <div v-if="!done" class="auth__logo">校</div>

    <header v-if="!done" class="auth__brand">
      <h1 class="auth__title">创建账号</h1>
      <p class="auth__sub">仅支持 QQ 邮箱 · 注册后需管理员分配角色</p>
    </header>

    <!-- 成功态：明确告知下一步，避免用户注册完立刻去登录然后撞 4006 -->
    <a-result
      v-if="done"
      status="success"
      title="注册成功"
      sub-title="账号已创建，待超级管理员分配角色后即可登录"
    >
      <template #extra>
        <a-button type="primary" shape="round" @click="router.push('/login')">返回登录</a-button>
      </template>
    </a-result>

    <a-form v-else ref="formRef" :model="form" :rules="rules" size="large" @finish="onSubmit">
      <a-form-item name="email">
        <a-input v-model:value="form.email" placeholder="QQ 邮箱" allow-clear>
          <template #prefix><MailOutlined /></template>
        </a-input>
      </a-form-item>

      <a-form-item name="mailCode">
        <a-input v-model:value="form.mailCode" placeholder="邮箱验证码" :maxlength="6">
          <template #addonAfter>
            <a-button
              type="link"
              size="small"
              :disabled="countdown > 0"
              :loading="sending"
              @click="onSendCode"
            >
              {{ sendLabel }}
            </a-button>
          </template>
        </a-input>
      </a-form-item>

      <a-form-item name="password">
        <a-input-password v-model:value="form.password" placeholder="密码（8–32 位）">
          <template #prefix><LockOutlined /></template>
        </a-input-password>
      </a-form-item>

      <a-form-item name="confirm">
        <a-input-password v-model:value="form.confirm" placeholder="确认密码" @press-enter="onSubmit">
          <template #prefix><LockOutlined /></template>
        </a-input-password>
      </a-form-item>

      <a-form-item name="realName">
        <a-input v-model:value="form.realName" placeholder="姓名（选填）" allow-clear>
          <template #prefix><UserOutlined /></template>
        </a-input>
      </a-form-item>

      <a-form-item v-if="captchaOn" name="captchaCode">
        <CaptchaField
          ref="captchaRef"
          v-model:code="form.captchaCode"
          v-model:captcha-key="form.captchaKey"
          @disabled="captchaOn = false"
          @submit="onSubmit"
        />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit" block :loading="submitting">注 册</a-button>
      </a-form-item>
    </a-form>

    <div v-if="!done" class="auth__links">
      <router-link to="/login">已有账号？返回登录</router-link>
    </div>
  </div>
</template>

<style scoped>
/* 注册页字段多，紧凑一点 */
.auth__brand {
  margin-bottom: var(--cm-space-6);
}
</style>
