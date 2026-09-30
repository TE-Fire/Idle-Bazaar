<script setup lang="ts">
/**
 * 登录表单（A1 表单部分，外壳在 LoginView）
 *
 * <p>两种登录方式并列（密码 / 邮箱验证码），但**共用同一个**图形验证码输入框：
 * 验证码是一次性的，每个 Tab 各放一个 CaptchaField 会同时占着两张图，
 * 切回来那张已经作废。所以验证码放在 Tab 外面，切换方式时手动换一张即可。
 *
 * <p>⚠️ 登录失败后必须**换一张**验证码：后端是「校验通过即删」的一次性码，
 * 密码输错时那张图已经作废，不刷新的话第二次提交必然卡在验证码上。
 */
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { FormInstance, Rule } from 'ant-design-vue/es/form'
import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons-vue'
import { DEMO_PASSWORD } from '@campus/shared'
import { authApi, LOGIN_TYPE, MAIL_CODE_SCENE } from '@campus/api'
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

/** 切换登录方式（iOS 分段控件）：图形验证码一次性，换方式就得换一张 */
const switchTab = (next: LoginTab): void => {
  if (tab.value === next) return
  tab.value = next
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
    await authApi.sendCode({
      type: MAIL_CODE_SCENE.login,
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
    // 两种登录方式走同一个端点，只差 type 与「凭据是什么」
    await user.login(
      tab.value === 'mail'
        ? {
            type: LOGIN_TYPE.byEmail,
            usernameOrEmail: form.email.trim(),
            passwordOrCode: form.mailCode.trim(),
            captchaKey: form.captchaKey,
            captchaCode: form.captchaCode
          }
        : {
            type: LOGIN_TYPE.byUsername,
            usernameOrEmail: form.username,
            passwordOrCode: form.password,
            captchaKey: form.captchaKey,
            captchaCode: form.captchaCode
          }
    )
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
  <div>
    <div class="auth__logo">校</div>

    <header class="auth__brand">
      <h1 class="auth__title">欢迎回来</h1>
      <p class="auth__sub">登录校园二手交易平台管理后台</p>
    </header>

    <!-- iOS 分段控件：激活项是白色浮起的小胶囊 -->
    <div class="seg" role="tablist">
      <button
        type="button"
        class="seg__item"
        :class="{ 'is-active': tab === 'password' }"
        role="tab"
        :aria-selected="tab === 'password'"
        @click="switchTab('password')"
      >
        账号密码
      </button>
      <button
        type="button"
        class="seg__item"
        :class="{ 'is-active': tab === 'mail' }"
        role="tab"
        :aria-selected="tab === 'mail'"
        @click="switchTab('mail')"
      >
        邮箱验证码
      </button>
    </div>

    <a-form ref="formRef" :model="form" :rules="rules" size="large" @finish="onSubmit">
      <template v-if="tab === 'password'">
        <a-form-item name="username">
          <a-input v-model:value="form.username" placeholder="账号" allow-clear>
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>

        <a-form-item name="password">
          <a-input-password v-model:value="form.password" placeholder="密码" @press-enter="onSubmit">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
      </template>

      <template v-else>
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
      </template>

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
        <a-button type="primary" html-type="submit" block :loading="loading">登 录</a-button>
      </a-form-item>
    </a-form>

    <div class="auth__links">
      <router-link to="/login/register">注册新账号</router-link>
      <span class="auth__links-dot" />
      <router-link to="/login/forgot">忘记密码</router-link>
    </div>

    <template v-if="tab === 'password'">
      <a-divider plain class="login__divider">演示账号（密码统一 {{ DEMO_PASSWORD }}）</a-divider>
      <a-space wrap>
        <a-button
          v-for="acc in DEMO_ACCOUNTS"
          :key="acc.username"
          size="small"
          shape="round"
          @click="form.username = acc.username"
        >
          {{ acc.label }}
        </a-button>
      </a-space>
    </template>
  </div>
</template>

<style scoped>
/* iOS 分段控件：灰底胶囊槽 + 白色浮起激活块 */
.seg {
  display: flex;
  padding: 3px;
  margin-bottom: var(--cm-space-6);
  border-radius: var(--cm-radius-pill);
  background: var(--cm-color-gray-100);
}

.seg__item {
  flex: 1;
  height: 36px;
  border: none;
  border-radius: var(--cm-radius-pill);
  background: transparent;
  font-size: var(--cm-font-size-base);
  font-family: inherit;
  color: var(--cm-color-text-2);
  cursor: pointer;
  transition: all var(--cm-motion-duration-fast) var(--cm-motion-ease);
}

.seg__item.is-active {
  background: var(--cm-color-surface);
  color: var(--cm-color-text-1);
  font-weight: 500;
  box-shadow: var(--cm-shadow-1);
}

/* 分隔链接的小圆点 */
.auth__links-dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--cm-color-text-3);
}

/* 演示账号分隔线：弱化成浅灰细线 */
.login__divider {
  margin: var(--cm-space-6) 0 var(--cm-space-4);
  border-color: var(--cm-color-border);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
