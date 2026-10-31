<script setup lang="ts">
/**
 * 认证页外壳（注册 / 找回密码）
 *
 * <p>与登录页同背景、同吉祥物立牌、同卡片圆角，只是没有扫码区、卡片更窄。
 * 抽成组件而不是复制两份样式：三页外观要一致，复制会各自漂移。
 * 表单字段本身用全局 `cm-auth-*` 基元（styles/index.scss §4）。
 */

defineProps<{
  title: string
  sub?: string
}>()
</script>

<template>
  <div class="auth">
    <div class="auth__box">
      <div class="mascot">校</div>
      <h1 class="auth__title">{{ title }}</h1>
      <p v-if="sub" class="auth__sub">{{ sub }}</p>
      <slot />
    </div>
    <p class="foot">校园二手交易平台 · 演示环境（验证码为演示回显，非真实短信/邮件）</p>
  </div>
</template>

<style scoped lang="scss">
.auth {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--cm-space-6);
  background:
    radial-gradient(circle at 18% 22%, rgba(255, 209, 0, 0.18), transparent 42%),
    var(--cm-color-bg);
}

.auth__box {
  position: relative;
  width: 440px;
  max-width: calc(100vw - 32px);
  padding: var(--cm-space-10) var(--cm-space-8) var(--cm-space-8);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: 0 8px 40px rgba(20, 20, 30, 0.1);
}

/* 与登录页同一个立牌位：一半压在卡片外 */
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

.auth__title {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-xl);
  font-weight: 800;
  color: var(--cm-color-text-1);
}

.auth__sub {
  margin: var(--cm-space-2) 0 var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  color: var(--cm-color-text-3);
}

.foot {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
