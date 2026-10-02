<script setup lang="ts">
/**
 * 登录（2026-09-30 新增，按闲鱼 Web 版登录页）
 *
 * <p>为什么单独一页、不走 MainLayout：闲鱼的登录是**全屏独立页**，没有导航干扰。
 * 路由挂在顶层（`/login`），不走 MainLayout 那条 children，天然没有顶栏与悬浮工具栏。
 *
 * <p>两种登录方式（短信验证码 / 密码）放在**同一个卡片的两个 tab**里，而不是两个页面：
 * 切换成本必须为零，否则验证码收不到时用户会直接流失。
 *
 * <p>骨架期说明：提交只做前端校验 + 反馈，不落库、不下发验证码
 * （学生端 `/api/auth/*` 未实现）。真实实现走 `POST /api/auth/login`。
 */

import { computed, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()

type Mode = 'sms' | 'pwd'
const mode = ref<Mode>('sms')

const phone = ref('')
const code = ref('')
const account = ref('')
const password = ref('')
const agreed = ref(false)

/** 获取验证码后的 60s 冷却：倒计时期间按钮不可点，避免误以为"没点上"而狂点 */
const cooldown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const PHONE_RE = /^1[3-9]\d{9}$/

const canGetCode = computed(() => PHONE_RE.test(phone.value) && cooldown.value === 0)

const canSubmit = computed(() => {
  if (!agreed.value) return false
  return mode.value === 'sms'
    ? PHONE_RE.test(phone.value) && /^\d{6}$/.test(code.value)
    : account.value.trim().length >= 3 && password.value.length >= 6
})

function sendCode(): void {
  if (!canGetCode.value) {
    ElMessage.warning({ message: '请填写正确的手机号', duration: 2000 })
    return
  }
  ElMessage.success({ message: `验证码已发送至 ${phone.value}（骨架期未真实下发）`, duration: 2000 })
  cooldown.value = 60
  timer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

function submit(): void {
  if (!agreed.value) {
    ElMessage.warning({ message: '请先阅读并同意用户协议与隐私政策', duration: 2000 })
    return
  }
  if (!canSubmit.value) {
    ElMessage.warning({
      message: mode.value === 'sms' ? '请填写手机号与 6 位验证码' : '账号至少 3 位，密码至少 6 位',
      duration: 2000
    })
    return
  }
  // 骨架期：只给反馈并回首页。真实实现走 POST /api/auth/login 落地会话
  ElMessage.success({ message: '登录成功（骨架期未落库）', duration: 1500 })
  void router.push({ name: 'home' })
}

/** 组件卸载时清掉定时器，否则倒计时会挂在 window 上继续跑 */
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="login">
    <div class="login__box">
      <!-- 吉祥物：压在卡片左上角（闲鱼的鱼形立牌位），骨架期用渐变方块 + 「校」字 -->
      <div class="mascot">校</div>

      <div class="login__cols">
        <!-- ── 左：表单 ─────────────────────────────── -->
        <section class="form" @keyup.enter="submit">
          <div class="tabs">
            <button
              type="button"
              class="tabs__item"
              :class="{ on: mode === 'sms' }"
              @click="mode = 'sms'"
            >
              短信登录
            </button>
            <button
              type="button"
              class="tabs__item"
              :class="{ on: mode === 'pwd' }"
              @click="mode = 'pwd'"
            >
              密码登录
            </button>
          </div>

          <template v-if="mode === 'sms'">
            <label class="field">
              <span class="field__pre">+86</span>
              <input v-model="phone" class="field__input" maxlength="11" placeholder="请输入手机号" />
            </label>

            <label class="field">
              <input v-model="code" class="field__input" maxlength="6" placeholder="6 位验证码" />
              <button type="button" class="field__act" :disabled="!canGetCode" @click="sendCode">
                {{ cooldown > 0 ? `${cooldown}s 后重发` : '获取验证码' }}
              </button>
            </label>
          </template>

          <template v-else>
            <label class="field">
              <input v-model="account" class="field__input" placeholder="手机号 / 校园邮箱" />
            </label>
            <label class="field">
              <input
                v-model="password"
                class="field__input"
                type="password"
                maxlength="32"
                placeholder="请输入密码"
              />
            </label>
          </template>

          <button type="button" class="submit" :disabled="!canSubmit" @click="submit">登录</button>

          <label class="agree">
            <input v-model="agreed" type="checkbox" />
            <span>已阅读并同意<a href="javascript:void(0)">《用户协议》</a>与<a href="javascript:void(0)">《隐私政策》</a></span>
          </label>

          <div class="links">
            <a href="javascript:void(0)">忘记密码</a>
            <i>·</i>
            <a href="javascript:void(0)">遇到问题</a>
            <i>·</i>
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

    <p class="foot">校园二手交易平台 · 演示环境（骨架期登录不落库）</p>
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

.tabs {
  display: flex;
  gap: var(--cm-space-6);
  margin-bottom: var(--cm-space-6);
}

.tabs__item {
  position: relative;
  padding: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-md);
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-2);
  }

  &.on {
    font-weight: 800;
    color: var(--cm-color-text-1);
  }

  /* 选中态用品牌黄下划线——主色只在这里和按钮出现（原则 1） */
  &.on::after {
    content: '';
    position: absolute;
    left: 50%;
    bottom: 0;
    transform: translateX(-50%);
    width: 24px;
    height: 3px;
    background: var(--cm-color-brand);
    border-radius: var(--cm-radius-pill);
  }
}

/* 灰填充输入框：闲鱼不用描边输入框，填充色本身界定边界 */
.field {
  display: flex;
  align-items: center;
  height: 46px;
  padding: 0 var(--cm-space-3);
  margin-bottom: var(--cm-space-3);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-lg);
}

.field__pre {
  padding-right: var(--cm-space-3);
  margin-right: var(--cm-space-2);
  border-right: 1px solid var(--cm-color-border-strong);
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-2);
}

.field__input {
  flex: 1;
  min-width: 0;
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-1);
  background: none;
  border: none;
  outline: none;

  &::placeholder {
    color: var(--cm-color-text-3);
  }
}

.field__act {
  flex: none;
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  cursor: pointer;

  &:hover:not(:disabled) {
    color: var(--cm-color-text-1);
  }

  &:disabled {
    color: var(--cm-color-text-3);
    cursor: not-allowed;
  }
}

.submit {
  width: 100%;
  height: 44px;
  margin-top: var(--cm-space-2);
  font-size: var(--cm-font-size-md);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border: none;
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover:not(:disabled) {
    background: var(--cm-color-brand-dark);
  }

  &:disabled {
    background: var(--cm-color-brand-bg);
    color: var(--cm-color-text-3);
    cursor: not-allowed;
  }
}

.agree {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-4);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);

  input {
    accent-color: var(--cm-color-brand);
  }

  a {
    color: var(--cm-color-text-2);
  }
}

.links {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-4);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);

  a:hover {
    color: var(--cm-color-text-1);
  }
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
