<script setup lang="ts">
/**
 * 本地联调用「测试账号」切换器（折叠下拉）
 *
 * <p>仅 dev 构建出现（App.vue 里用 import.meta.env.DEV 控制挂载）。点击账号即走
 * send-code → 取 devCode → login 全自动登录，免去手填验证码；便于在多个演示账号间
 * 快速切换测社交 / 订单等需登录态的流程。生产构建不挂载，不影响线上。
 */

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { MAIL_CODE_SCENE, USER_LOGIN_TYPE, userAuthApi } from '@campus/api'
import { useUserStore } from '@/stores/user'
import { DEMO_USERS, authLabel, type TestAccount } from '@campus/shared'
import { forgetTestAccount, listTestAccounts } from '@/utils/test-accounts'

const session = useUserStore()
const open = ref(false)
const loadingPhone = ref('')
const error = ref('')

/** 刚注册的账号：打开面板时重读，注册后不必刷新页面就能看到 */
const recent = ref<TestAccount[]>(listTestAccounts())

const current = computed(() =>
  session.isLogin ? session.nickname || '已登录' : '未登录'
)

function toggle(): void {
  if (open.value) {
    open.value = false
    return
  }
  recent.value = listTestAccounts()
  error.value = ''
  open.value = true
}

function isCurrent(id: string): boolean {
  return session.isLogin && session.userId === id
}

/** 两组的条目形态一致，只取昵称与手机号：演示账号与刚注册账号共用一条切换路径 */
async function quickLogin(u: { nickname: string; phone: string }): Promise<void> {
  loadingPhone.value = u.phone
  error.value = ''
  try {
    const sms = await userAuthApi.sendCode(u.phone, MAIL_CODE_SCENE.login)
    if (!sms.devCode) {
      error.value = 'devCode 未回显，请确认 campus.user.sms.echo=true'
      return
    }
    await session.login(u.phone, USER_LOGIN_TYPE.code, sms.devCode)
    open.value = false
    ElMessage.success({ message: `已切换为 ${u.nickname}`, duration: 1500 })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loadingPhone.value = ''
  }
}

function forget(id: string): void {
  forgetTestAccount(id)
  recent.value = listTestAccounts()
}

function logout(): void {
  void session.logout()
  open.value = false
}
</script>

<template>
  <div class="dev-sw">
    <button v-if="!open" type="button" class="dev-sw__fab" @click="toggle">
      <span class="dev-sw__dot" />
      测试账号 · {{ current }}
      <span class="dev-sw__caret">▴</span>
    </button>

    <div v-else class="dev-sw__panel">
      <header class="dev-sw__head">
        <span>切换登录账号（测试）</span>
        <button type="button" class="dev-sw__close" @click="open = false">▾</button>
      </header>

      <ul class="dev-sw__list">
        <template v-if="recent.length">
          <li class="dev-sw__group">刚注册 · 本机</li>
          <li v-for="u in recent" :key="u.id">
            <button
              type="button"
              class="dev-sw__item"
              :class="{ 'is-current': isCurrent(u.id) }"
              :disabled="loadingPhone === u.phone"
              @click="quickLogin(u)"
            >
              <span class="dev-sw__avatar">{{ u.nickname.slice(0, 1) }}</span>
              <span class="dev-sw__meta">
                <span class="dev-sw__name">
                  {{ u.nickname }}
                  <em class="dev-sw__new">新</em>
                </span>
                <span class="dev-sw__sub">{{ u.phone }} · 信用 {{ u.creditScore }}</span>
              </span>
              <span v-if="loadingPhone === u.phone" class="dev-sw__spin">登录中…</span>
              <span v-else class="dev-sw__del" title="从清单移除" @click.stop="forget(u.id)">×</span>
            </button>
          </li>
        </template>

        <li class="dev-sw__group">演示账号</li>
        <li v-for="u in DEMO_USERS" :key="u.id">
          <button
            type="button"
            class="dev-sw__item"
            :class="{ 'is-current': isCurrent(String(u.id)) }"
            :disabled="loadingPhone === u.phone"
            @click="quickLogin(u)"
          >
            <span class="dev-sw__avatar">{{ u.nickname.slice(0, 1) }}</span>
            <span class="dev-sw__meta">
              <span class="dev-sw__name">
                {{ u.nickname }}
                <em v-if="u.banned" class="dev-sw__ban">封禁</em>
              </span>
              <span class="dev-sw__sub">
                {{ u.phone }} · {{ authLabel(u.authStatus) }} · 信用 {{ u.creditScore }}
              </span>
            </span>
            <span v-if="loadingPhone === u.phone" class="dev-sw__spin">登录中…</span>
          </button>
        </li>
      </ul>

      <p v-if="error" class="dev-sw__err">{{ error }}</p>

      <footer class="dev-sw__foot">
        <span>当前：{{ current }}</span>
        <button v-if="session.isLogin" type="button" class="dev-sw__logout" @click="logout">
          退出
        </button>
      </footer>
    </div>
  </div>
</template>

<style scoped lang="scss">
.dev-sw {
  position: fixed;
  right: var(--cm-space-4);
  bottom: var(--cm-space-4);
  z-index: 9999;
  font-size: 12px;
}

.dev-sw__fab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #1a1a1a;
  background: var(--cm-color-brand);
  border: none;
  border-radius: var(--cm-radius-pill);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.18);
  cursor: pointer;

  &:hover {
    background: var(--cm-color-brand-dark);
  }
}

.dev-sw__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1a1a1a;
  opacity: 0.7;
}

.dev-sw__caret {
  font-size: 10px;
}

.dev-sw__panel {
  width: 260px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.dev-sw__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-bg);
  border-bottom: 1px solid var(--cm-color-border);
}

.dev-sw__close {
  border: none;
  background: none;
  color: var(--cm-color-text-2);
  cursor: pointer;
  font-size: 14px;
}

.dev-sw__list {
  margin: 0;
  padding: 4px;
  list-style: none;
  overflow-y: auto;
}

/* 分组小标题：刚注册 与 演示账号 之间靠它分隔，不用分割线（两行之间已有留白） */
.dev-sw__group {
  padding: 8px 8px 4px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.4px;
  color: var(--cm-color-text-3);
}

.dev-sw__item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 7px 8px;
  text-align: left;
  background: none;
  border: none;
  border-radius: var(--cm-radius-md);
  cursor: pointer;

  &:hover:not(:disabled) {
    background: var(--cm-color-bg);
  }

  &:disabled {
    opacity: 0.6;
    cursor: progress;
  }

  &.is-current {
    background: var(--cm-color-brand-bg);
  }
}

.dev-sw__avatar {
  flex: none;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: #1a1a1a;
  background: linear-gradient(135deg, #ffe37a, var(--cm-color-brand));
  border-radius: 50%;
}

.dev-sw__meta {
  flex: 1;
  min-width: 0;
}

.dev-sw__name {
  display: block;
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.dev-sw__ban {
  margin-left: 4px;
  padding: 0 4px;
  font-size: 10px;
  font-style: normal;
  color: #fff;
  background: #e54d42;
  border-radius: 4px;
}

/* 「新」标：品牌黄底深字，与封禁的红色区分开 */
.dev-sw__new {
  margin-left: 4px;
  padding: 0 4px;
  font-size: 10px;
  font-style: normal;
  color: #1a1a1a;
  background: var(--cm-color-brand);
  border-radius: 4px;
}

.dev-sw__del {
  flex: none;
  width: 20px;
  height: 20px;
  line-height: 18px;
  text-align: center;
  font-size: 14px;
  color: var(--cm-color-text-3);
  border-radius: 50%;

  &:hover {
    color: #e54d42;
    background: #fef0ef;
  }
}

.dev-sw__sub {
  display: block;
  font-size: 11px;
  color: var(--cm-color-text-3);
  /* 一行放不下就截断：手机号与信用分比这行字重要，别让它折成两行把条目撑高 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dev-sw__spin {
  flex: none;
  font-size: 11px;
  color: var(--cm-color-text-2);
}

.dev-sw__err {
  margin: 0;
  padding: 6px 12px;
  font-size: 11px;
  color: #e54d42;
  background: #fef0ef;
}

.dev-sw__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 11px;
  color: var(--cm-color-text-3);
  border-top: 1px solid var(--cm-color-border);
}

.dev-sw__logout {
  padding: 3px 10px;
  font-size: 11px;
  color: var(--cm-color-text-1);
  background: var(--cm-color-bg);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-pill);
  cursor: pointer;
}
</style>
