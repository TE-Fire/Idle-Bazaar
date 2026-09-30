<script setup lang="ts">
/**
 * 认证外壳（A1 登录 / A0 注册 / A10 找回，design/03 第五章）
 *
 * <p>版式：左侧 banner 三屏自动轮播（纯 CSS 渐变 + 内联 SVG 几何插画，不引图片资源），
 * 右侧是**同一张白卡片**，登录 / 注册 / 找回三种表单在卡片内切换——
 * 不再是三个独立页面各铺一整屏，来回切不用整页跳转。<1024px 时隐藏 banner。
 *
 * <p>路由用 `/login/:mode?` 一条记录：登录 ↔ 注册 ↔ 找回是**同一路由内的参数变化**，
 * 组件实例复用，只有卡片内容做过场动画；旧的 /register、/forgot 路径在路由表里重定向兼容。
 *
 * <p>表单本体在 `components/auth/` 下三个子组件里，本组件只管壳。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import LoginForm from '@/components/auth/LoginForm.vue'
import RegisterForm from '@/components/auth/RegisterForm.vue'
import ForgotForm from '@/components/auth/ForgotForm.vue'

const route = useRoute()

type AuthMode = 'login' | 'register' | 'forgot'

const FORMS = {
  login: LoginForm,
  register: RegisterForm,
  forgot: ForgotForm
} as const

/** /login/:mode? 的参数即模式；裸 /login 归登录 */
const mode = computed<AuthMode>(() => {
  const m = route.params.mode
  return m === 'register' || m === 'forgot' ? m : 'login'
})

const SLIDE_COUNT = 3
const SLIDE_INTERVAL = 6000

// 轮播：外壳常驻，切表单不打断（这是不拆三个页面的最大收益）
const current = ref(0)
let timer: number | undefined

const stopAuto = (): void => {
  if (timer !== undefined) window.clearInterval(timer)
}

const startAuto = (): void => {
  stopAuto()
  timer = window.setInterval(() => {
    current.value = (current.value + 1) % SLIDE_COUNT
  }, SLIDE_INTERVAL)
}

/** 手动切屏后重置计时，否则刚点完 1 秒就自动跳走了 */
const goSlide = (index: number): void => {
  current.value = index
  startAuto()
}

onMounted(startAuto)
onUnmounted(stopAuto)
</script>

<template>
  <div class="login">
    <!-- 左侧 banner：三屏渐变 + 几何插画，自动轮播 -->
    <aside class="login__banner">
      <div class="slide slide--blue" :class="{ 'is-active': current === 0 }">
        <svg class="slide__art" viewBox="0 0 320 320" aria-hidden="true">
          <circle cx="160" cy="160" r="118" fill="none" stroke="rgba(255,255,255,.28)" stroke-width="1.5" />
          <circle cx="160" cy="160" r="78" fill="none" stroke="rgba(255,255,255,.22)" stroke-width="1.5" />
          <rect x="96" y="112" width="128" height="96" rx="16" fill="rgba(255,255,255,.16)" />
          <rect x="112" y="132" width="96" height="10" rx="5" fill="rgba(255,255,255,.5)" />
          <rect x="112" y="152" width="64" height="10" rx="5" fill="rgba(255,255,255,.34)" />
          <rect x="112" y="172" width="80" height="10" rx="5" fill="rgba(255,255,255,.34)" />
        </svg>
        <div class="slide__text">
          <p class="slide__eyebrow">校园二手交易平台</p>
          <h2 class="slide__title">把闲置流转起来</h2>
          <p class="slide__desc">发布、审核、成交，一条链路管到底。同校交易，见面即验货。</p>
        </div>
      </div>

      <div class="slide slide--indigo" :class="{ 'is-active': current === 1 }">
        <svg class="slide__art" viewBox="0 0 320 320" aria-hidden="true">
          <rect x="52" y="60" width="96" height="96" rx="20" fill="rgba(255,255,255,.16)" />
          <rect x="172" y="60" width="96" height="96" rx="20" fill="rgba(255,255,255,.10)" />
          <rect x="52" y="172" width="96" height="96" rx="20" fill="rgba(255,255,255,.10)" />
          <rect x="172" y="172" width="96" height="96" rx="20" fill="rgba(255,255,255,.16)" />
          <circle cx="100" cy="108" r="18" fill="rgba(255,255,255,.42)" />
          <circle cx="220" cy="220" r="18" fill="rgba(255,255,255,.42)" />
        </svg>
        <div class="slide__text">
          <p class="slide__eyebrow">审核与风控</p>
          <h2 class="slide__title">违规内容，先拦后放</h2>
          <p class="slide__desc">商品先审后发、违规下架可申诉，处置过程全程留痕可追溯。</p>
        </div>
      </div>

      <div class="slide slide--warm" :class="{ 'is-active': current === 2 }">
        <svg class="slide__art" viewBox="0 0 320 320" aria-hidden="true">
          <path
            d="M40 210 L100 150 L150 180 L210 96 L280 140"
            fill="none"
            stroke="rgba(255,255,255,.55)"
            stroke-width="3"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle cx="100" cy="150" r="8" fill="rgba(255,255,255,.85)" />
          <circle cx="150" cy="180" r="8" fill="rgba(255,255,255,.85)" />
          <circle cx="210" cy="96" r="8" fill="rgba(255,255,255,.85)" />
          <rect x="40" y="240" width="240" height="2" rx="1" fill="rgba(255,255,255,.3)" />
          <rect x="40" y="180" width="240" height="2" rx="1" fill="rgba(255,255,255,.18)" />
          <rect x="40" y="120" width="240" height="2" rx="1" fill="rgba(255,255,255,.18)" />
        </svg>
        <div class="slide__text">
          <p class="slide__eyebrow">数据看板</p>
          <h2 class="slide__title">运营状况，一眼看清</h2>
          <p class="slide__desc">成交趋势、品类分布、校区热度，指标按角色分权限下发。</p>
        </div>
      </div>

      <div class="login__dots">
        <button
          v-for="i in SLIDE_COUNT"
          :key="i"
          type="button"
          class="dot"
          :class="{ 'is-active': current === i - 1 }"
          :aria-label="`切换到第 ${i} 屏`"
          @click="goSlide(i - 1)"
        />
      </div>
    </aside>

    <!-- 右侧卡片：三种表单原地切换 -->
    <main class="login__main">
      <div class="auth__panel">
        <Transition name="swap" mode="out-in">
          <component :is="FORMS[mode]" :key="mode" />
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: flex;
  background: var(--cm-color-bg);
}

/* ── 左侧 banner ─────────────────────────────────────────── */

.login__banner {
  position: relative;
  flex: 1 1 58%;
  overflow: hidden;
  border-radius: 0 var(--cm-radius-xl) var(--cm-radius-xl) 0;
}

.slide {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cm-space-6);
  padding: var(--cm-space-12);
  opacity: 0;
  transform: scale(1.02);
  transition: opacity var(--cm-motion-duration-slow) var(--cm-motion-ease),
    transform var(--cm-motion-duration-slow) var(--cm-motion-ease);
  pointer-events: none;
}

.slide.is-active {
  opacity: 1;
  transform: none;
  pointer-events: auto;
}

.slide--blue {
  background: linear-gradient(135deg, var(--cm-banner-blue-from), var(--cm-banner-blue-to));
}

.slide--indigo {
  background: linear-gradient(135deg, var(--cm-banner-indigo-from), var(--cm-banner-indigo-to));
}

.slide--warm {
  background: linear-gradient(135deg, var(--cm-banner-warm-from), var(--cm-banner-warm-to));
}

.slide__art {
  width: 320px;
  max-width: 60%;
  flex: none;
}

.slide__text {
  color: #fff;
  text-align: center;
  max-width: 420px;
}

.slide__eyebrow {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  letter-spacing: 0.08em;
  opacity: 0.75;
}

.slide__title {
  margin: 0;
  font-size: var(--cm-font-size-xl);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.slide__desc {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-md);
  line-height: 1.6;
  opacity: 0.85;
}

.login__dots {
  position: absolute;
  left: 50%;
  bottom: var(--cm-space-8);
  transform: translateX(-50%);
  display: flex;
  gap: var(--cm-space-2);
}

.dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.45);
  cursor: pointer;
  transition: all var(--cm-motion-duration-fast) var(--cm-motion-ease);
}

.dot.is-active {
  width: 24px;
  border-radius: var(--cm-radius-pill);
  background: #fff;
}

/* ── 右侧卡片 ────────────────────────────────────────────── */

.login__main {
  flex: 1 1 42%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--cm-space-12) var(--cm-space-10);
  background: var(--cm-color-bg);
}

/* 卡片内容切换过场：轻微上移淡入 */
.swap-enter-active,
.swap-leave-active {
  transition: opacity var(--cm-motion-duration-base) var(--cm-motion-ease),
    transform var(--cm-motion-duration-base) var(--cm-motion-ease);
}

.swap-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.swap-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 1023px) {
  .login__banner {
    display: none;
  }

  .login__main {
    flex: 1 1 100%;
  }
}
</style>
