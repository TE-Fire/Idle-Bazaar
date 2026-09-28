<script setup lang="ts">
/**
 * A1 登录页（原型 prototypes/admin/login.html，design/03 第五章）
 *
 * <p>图形验证码：后端 `campus.captcha.enabled` 在 dev 下是 false，下发接口也还没写，
 * 所以这里**先不画验证码输入**。等后端补上 `/admin/auth/captcha` 再按原型加回来，
 * 入参字段 captchaKey / captchaCode 已在 @campus/api 里预留。
 *
 * <p>演示账号四个角色同密码（后端 AdminAccountInitializer.DEMO_PASSWORD），
 * 一键填充是为了答辩时快速切换角色验证权限隔离。
 */

import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { LockOutlined, UserOutlined } from '@ant-design/icons-vue'
import { DEMO_PASSWORD } from '@campus/shared'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const form = reactive({ username: 'super', password: DEMO_PASSWORD })
const loading = ref(false)

/** 四个角色一键切换，用于验证「审核员不能封禁用户」这类互斥 */
const DEMO_ACCOUNTS = [
  { username: 'super', label: '超级管理员' },
  { username: 'reviewer', label: '审核员' },
  { username: 'operator', label: '运营' },
  { username: 'readonly', label: '只读' }
]

const onSubmit = async (): Promise<void> => {
  loading.value = true
  try {
    await user.login(form)
    const redirect = (route.query.redirect as string) || '/dashboard'
    message.success(`欢迎回来，${user.realName}`)
    void router.push(redirect)
  } catch {
    // 失败提示由请求层统一 toast（含 traceId），这里不再重复弹
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

      <a-form :model="form" size="large" @finish="onSubmit">
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

        <a-form-item>
          <a-button type="primary" html-type="submit" block :loading="loading">登 录</a-button>
        </a-form-item>
      </a-form>

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
</style>
