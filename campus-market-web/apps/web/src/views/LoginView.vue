<script setup lang="ts">
/**
 * 登录（验证码登录 / 密码登录）
 *
 * <p>为什么单独一页、不走 MainLayout：闲鱼的登录是**全屏独立页**，没有导航干扰。
 * 路由挂在顶层（`/login`），不走 MainLayout 那条 children，天然没有顶栏与悬浮工具栏。
 *
 * <p>两种方式：验证码登录（手机号走短信 / QQ 邮箱走邮件）、密码登录。密码只对新注册
 * 用户存在——老账号没设过密码，走「找回密码」设一个即可，所以密码登录失败时
 * 错误提示要能指向那条路（4038 的文案已写清楚）。
 *
 * <p>演示期验证码随发码响应回显，上线前关掉 `campus.user.*.echo` 后只进日志。
 * 登录成功按 redirect 回跳，没有则回首页。
 */

import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MAIL_CODE_SCENE, USER_LOGIN_TYPE } from '@campus/api'
import { useUserStore } from '@/stores/user'
import { accountKind, useVerifyCode } from '@/composables/useVerifyCode'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

/** 方式：1 验证码 2 密码，值与后端约定一致 */
const mode = ref<number>(USER_LOGIN_TYPE.code)

/** 从「找回密码」跳过来时带 ?account=，预填省一次输入 */
const account = ref(typeof route.query.account === 'string' ? route.query.account : '')
const code = ref('')
const password = ref('')
const agreed = ref(false)

const { cooldown, counting, send: sendCode } = useVerifyCode(MAIL_CODE_SCENE.login)

const kind = computed(() => accountKind(account.value))

const canGetCode = computed(() => Boolean(kind.value) && !counting.value)

const canSubmit = computed(() => {
  if (!agreed.value || !kind.value) return false
  return mode.value === USER_LOGIN_TYPE.code
    ? /^\d{6}$/.test(code.value)
    : password.value.length >= 8
})

/** 切换方式时清掉上一种方式的凭证，避免「验证码框里的值被当密码提交」这类串台 */
function switchMode(next: number): void {
  if (mode.value === next) return
  mode.value = next
  code.value = ''
  password.value = ''
}

function onSend(): void {
  if (!kind.value) {
    ElMessage.warning({ message: '请输入手机号或 QQ 邮箱', duration: 2000 })
    return
  }
  void sendCode(account.value)
}

async function submit(): Promise<void> {
  if (!agreed.value) {
    ElMessage.warning({ message: '请先阅读并同意用户协议与隐私政策', duration: 2000 })
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning({
      message:
        mode.value === USER_LOGIN_TYPE.code
          ? '请填写账号与 6 位验证码'
          : '请填写账号与至少 8 位的密码',
      duration: 2000
    })
    return
  }
  const credential = mode.value === USER_LOGIN_TYPE.code ? code.value : password.value
  await session.login(account.value.trim(), mode.value as 1 | 2, credential)
  ElMessage.success({ message: '登录成功', duration: 1500 })
  const redirect = router.currentRoute.value.query.redirect
  void router.push(typeof redirect === 'string' ? redirect : '/')
}
</script>

<template>
  <div class="login">
    <div class="login__box">
      <!-- 吉祥物：压在卡片左上角（闲鱼的鱼形立牌位），骨架期用渐变方块 + 「校」字 -->
      <div class="mascot">校</div>

      <div class="login__cols">
        <!-- ── 左：表单 ────────────────────────────── -->
        <section class="form" @keyup.enter="submit">
          <div class="tabs">
            <button
              type="button"
              class="tab"
              :class="{ 'tab--on': mode === 1 }"
              @click="switchMode(1)"
            >
              验证码登录
            </button>
            <button
              type="button"
              class="tab"
              :class="{ 'tab--on': mode === 2 }"
              @click="switchMode(2)"
            >
              密码登录
            </button>
          </div>

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

          <label v-if="mode === 1" class="cm-auth-field">
            <span class="cm-auth-field__key">验证码</span>
            <input
              v-model="code"
              class="cm-auth-field__input"
              maxlength="6"
              inputmode="numeric"
              placeholder="6 位数字"
            />
            <button type="button" class="cm-auth-field__act" :disabled="!canGetCode" @click="onSend">
              {{ counting ? `${cooldown}s 后重发` : '获取验证码' }}
            </button>
          </label>

          <label v-else class="cm-auth-field">
            <span class="cm-auth-field__key">密码</span>
            <input
              v-model="password"
              class="cm-auth-field__input"
              type="password"
              maxlength="32"
              placeholder="8–32 位，不含空格"
              autocomplete="current-password"
            />
          </label>

          <button type="button" class="cm-auth-submit" :disabled="!canSubmit" @click="submit">登录</button>

          <label class="cm-auth-agree">
            <input v-model="agreed" type="checkbox" />
            <span>已阅读并同意<a href="javascript:void(0)">《用户协议》</a>与<a href="javascript:void(0)">《隐私政策》</a></span>
          </label>

          <div class="cm-auth-links">
            <RouterLink :to="{ name: 'register' }">注册账号</RouterLink>
            <span class="cm-auth-links__sep">·</span>
            <RouterLink :to="{ name: 'forgot' }">忘记密码</RouterLink>
            <span class="cm-auth-links__sep">·</span>
            <RouterLink :to="{ name: 'home' }">先去逛逛</RouterLink>
          </div>
        </section>

        <!-- ── 右：扫码（竖线分隔，与闲鱼一致）────────── -->
        <aside class="qr">
          <div class="qr__code" />
          <p class="qr__txt">扫码登录</p>
          <p class="qr__sub">用校园二手 App 扫一扫<br />更安全、不用输密码</p>
        </aside>
      </div>
    </div>

    <p class="foot">校园二手交易平台 · 演示环境（验证码为演示回显，非真实短信/邮件）</p>
  </div>
</template>

<style scoped lang="scss">
.login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cm-space-6);
  /* 灰底 + 左上角一抹极淡的黄，给整页一点纵深（闲鱼的登录页也是浅灰底） */
  background:
    radial-gradient(circle at 18% 22%, rgba(255, 209, 0, 0.18), transparent 42%),
    var(--cm-color-bg);
}

.login__box {
  position: relative;
  width: 720px;
  max-width: calc(100vw - 32px);
  padding: var(--cm-space-10) var(--cm-space-8) var(--cm-space-8);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: 0 8px 40px rgba(20, 20, 30, 0.1);
}

/* 吉祥物立牌：一半压在卡片外，是闲鱼登录页的记忆点 */
.mascot {
  position: absolute;
  left: var(--cm-space-8);
  top: -36px;
  width: 72px;
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  font-weight: 900;
  color: var(--cm-color-text-1);
  background: linear-gradient(135deg, #ffe37a, var(--cm-color-brand));
  border: 3px solid var(--cm-color-surface);
  border-radius: var(--cm-radius-xl);
  box-shadow: var(--cm-shadow-2);
}

.login__cols {
  display: flex;
  align-items: stretch;
  gap: var(--cm-space-8);
}

/* ── 左：表单 ─────────────────────────────────────────── */

.form {
  flex: 1;
  min-width: 0;
}

/* 方式切换：下划线式 tab，不与输入框抢视觉重量 */
.tabs {
  display: flex;
  gap: var(--cm-space-6);
  margin-bottom: var(--cm-space-4);
}

.tab {
  padding: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-2);
  }
}

.tab--on {
  color: var(--cm-color-text-1);
  border-bottom-color: var(--cm-color-brand);
}

/* ── 右：扫码区 ───────────────────────────────────────── */

.qr {
  flex: none;
  width: 200px;
  padding-left: var(--cm-space-8);
  border-left: 1px solid var(--cm-color-border);
  text-align: center;
}

/* 二维码占位：用两层 repeating-linear-gradient 画出棋盘点阵，不引第三方库 */
.qr__code {
  width: 132px;
  height: 132px;
  margin: 0 auto var(--cm-space-3);
  border-radius: var(--cm-radius-lg);
  background-color: var(--cm-color-surface);
  background-image:
    repeating-linear-gradient(0deg, var(--cm-color-text-1) 0 8px, transparent 8px 16px),
    repeating-linear-gradient(90deg, var(--cm-color-text-1) 0 8px, transparent 8px 16px);
  background-size: 50% 50%;
  background-position: 0 0, 100% 0;
  background-repeat: repeat;
  opacity: 0.85;
}

.qr__txt {
  margin: 0;
  font-size: var(--cm-font-size-base);
  font-weight: 700;
}

.qr__sub {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.7;
  color: var(--cm-color-text-3);
}

.foot {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

@media (max-width: 767px) {
  .login__cols {
    flex-direction: column;
    gap: var(--cm-space-6);
  }

  .qr {
    width: 100%;
    padding-left: 0;
    padding-top: var(--cm-space-6);
    border-left: none;
    border-top: 1px solid var(--cm-color-border);
  }
}
</style>
