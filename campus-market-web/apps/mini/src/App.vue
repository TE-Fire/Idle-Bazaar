<script setup lang="ts">
/**
 * 小程序根组件
 *
 * <p>页面栈最多 10 层（design/02 §二）——详情页 → 卖家主页 → 商品详情 这条链
 * 必须用 `redirectTo` 截断，否则用户来回点几次就再也跳不动了。
 * 该约定落在 `pages/item/detail.vue` 的跳转实现里。
 *
 * DevLoginSwitcher 全局挂载（dev 才渲染）：本地联调用的「测试账号」折叠下拉。
 */
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'

const isDev = import.meta.env.DEV
</script>

<template>
  <DevLoginSwitcher v-if="isDev" />
</template>

<style lang="scss">
/* 全局基础样式放这里（uni.scss 只放变量），页面样式各自 scoped。
   必须用 scss：$cm-* 变量由 uni.scss 注入，纯 CSS 块拿不到。
   跨页面复用的规则（卡片 / 按钮 / 区块标题 / 动效）统一在 styles/theme.scss。 */
@import './styles/theme.scss';
</style>
