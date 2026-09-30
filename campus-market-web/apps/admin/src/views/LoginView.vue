<script setup lang="ts">
/**
 * A1 登录页（原型 prototypes/admin/login.html，design/03 第五章）
 *
 * <p>两种登录方式并列（密码 / 邮箱验证码），但**共用同一个**图形验证码输入框：
 * 验证码是一次性的，每个 Tab 各放一个 CaptchaField 会同时占着两张图，
 * 切回来那张已经作废。所以验证码放在 Tab 外面，切换方式时手动换一张即可。
 *
 * <p>图形验证码：登录 / 注册 / 找回三页共用 `CaptchaField` 组件。
 * 后端 `campus.captcha.enabled=false` 时接口会返回 `disabled=true`，
 * 组件收到后通知这里把自己隐藏——本地联调不必每次都输四位验证码。
 *
 * <p>⚠️ 登录失败后必须**换一张**验证码：后端是「校验通过即删」的一次性码，
 * 密码输错时那张图已经作废，不刷新的话第二次提交必然卡在验证码上。
 *
 * <p>演示账号四个角色同密码（后端 AdminAccountInitializer.DEMO_PASSWORD），
 * 一键填充是为了答辩时快速切换角色验证权限隔离。
 */

import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons-vue'
import { DEMO_PASSWORD } from '@campus/shared'
import { authApi } from '@campus/api'
import CaptchaField from '@/components/CaptchaField.vue'
import { useCountdown } from '@/composables/useCountdown'
import { isQqMail } from '@/utils/mail'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

type LoginTab = 'password' | 'mail'
const tab = ref<LoginTab>('password')

const formRef = ref<FormInstance>()
const loading = ref(false)
const sending = ref(false)
const captchaOn = ref(true)
const captchaRef = ref<InstanceType<typeof CaptchaField> | null>(null)
const { left: countdown, start: startCountdown } = useCountdown(60)

const form = reactive({
  username: 'super',
  password: DEMO_PASSWORD,
  email: '',
  mailCode: '',
  captchaKey: '',
  captchaCode: ''
})

/** 四个角色一键切换，用于验证「审核员不能封禁用户」这类互斥 */
const DEMO_ACCOUNTS = [
  { username: 'super', label: '超级管理员' },
  { username: 'reviewer', label: '审核员' },
  { username: 'operator', label: '运营' },
  { username: 'readonly', label: '只读' }
]

/** 只有邮箱验证码这一路有格式约束；账号/密码沿用后端 1000 的提示，前端不再重复拦 */
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
  captchaCode: [{ required: true, message: '请输入图形验证码', trigger: 'blur' }]
}

const sendLabel = computed(() => (countdown.value > 0 ? `${countdown.value}s 后重发` : '获取验证码'))

/** 切换登录方式：图形验证码一次性，换方式就得换一张 */
const onTabChange = (key: string | number): void => {
  tab.value = key as LoginTab
  void captchaRef.value?.refresh()
}

/** 发邮箱验证码：只校验邮箱与图形验证码，不牵动账号密码字段 */
const onSendCode = async (): Promise<void> => {
  try {
    await formRef.value?.validateFields(['email', 'captchaCode'])
  } catch {
    return
  }
  sending.value = true
  try {
    await authApi.sendLoginCode({
      email: form.email.trim(),
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    message.success('验证码已发送至邮箱，10 分钟内有效')
    startCountdown()
    // 图形验证码一次性：发码用掉后必须换一张给登录提交用
    await captchaRef.value?.refresh()
  } catch {
    // 失败提示由请求层统一 toast
    await captchaRef.value?.refresh()
  } finally {
    sending.value = false
  }
}

const onSubmit = async (): Promise<void> => {
  if (tab.value === 'mail') {
    try {
      await formRef.value?.validateFields(['email', 'mailCode'])
    } catch {
      return
    }
  }
  loading.value = true
  try {
    if (tab.value === 'mail') {
      await user.loginByMailCode({
        email: form.email.trim(),
        mailCode: form.mailCode.trim(),
        captchaKey: form.captchaKey,
        captchaCode: form.captchaCode
      })
    } else {
      await user.login({
        username: form.username,
        password: form.password,
        captchaKey: form.captchaKey,
        captchaCode: form.captchaCode
      })
    }
    const redirect = (route.query.redirect as string) || '/dashboard'
    message.success(`欢迎回来，${user.realName}`)
    void router.push(redirect)
  } catch {
    // 失败提示由请求层统一 toast（含 traceId），这里不再重复弹
    void captchaRef.value?.refresh()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="cm-card login__card">
      <div class="login__brand">
        <div class="login__title">校园二手交易平台</div>
        <div class="login__sub">管理后台 · 请登录后继续</div>
      </div>

      <a-form ref="formRef" :model="form" :rules="rules" size="large" @finish="onSubmit">
        <a-tabs :active-key="tab" centered @change="onTabChange">
          <a-tab-pane key="password" tab="账号密码">
            <a-form-item name="username">
              <a-input v-model:value="form.username" placeholder="账号" allow-clear>
                <template #prefix><UserOutlined /></template>
              </a-input>
            </a-form-item>

            <a-form-item name="password">
              <a-input-password
                v-model:value="form.password"
                placeholder="密码"
                @press-enter="onSubmit"
              >
                <template #prefix><LockOutlined /></template>
              </a-input-password>
            </a-form-item>
          </a-tab-pane>

          <a-tab-pane key="mail" tab="邮箱验证码">
            <a-form-item name="email">
              <a-input v-model:value="form.email" placeholder="注册用的 QQ 邮箱" allow-clear>
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
          </a-tab-pane>
        </a-tabs>

        <a-form-item v-if="captchaOn" name="captchaCode">
          <CaptchaField
            ref="captchaRef"
            v-model:code="form.captchaCode"
            v-model:key="form.captchaKey"
            @disabled="captchaOn = false"
            @submit="onSubmit"
          />
        </a-form-item>

        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="loading">登 录</a-button>
        </a-form-item>
      </a-form>

      <div class="login__links">
        <router-link to="/register">注册新账号</router-link>
        <a-divider type="vertical" />
        <router-link to="/forgot">忘记密码</router-link>
      </div>

      <template v-if="tab === 'password'">
        <a-divider plain>演示账号（密码统一 {{ DEMO_PASSWORD }}）</a-divider>
        <a-space wrap>
          <a-button
            v-for="acc in DEMO_ACCOUNTS"
            :key="acc.username"
            size="small"
            @click="form.username = acc.username"
          >
            {{ acc.label }}
          </a-button>
        </a-space>
      </template>
    </div>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cm-color-bg);
}

.login__card {
  width: 380px;
  padding: var(--cm-space-6);
}

.login__brand {
  text-align: center;
  margin-bottom: var(--cm-space-6);
}

.login__title {
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.login__sub {
  margin-top: var(--cm-space-1);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.login__links {
  text-align: center;
  font-size: var(--cm-font-size-sm);
  margin-bottom: var(--cm-space-4);
}
</style>
