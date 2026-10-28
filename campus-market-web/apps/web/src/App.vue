<script setup lang="ts">
/**
 * 根组件：只挂 RouterView。
 * 顶栏与分类条放在 MainLayout 里——发布页也要它们，但骨架期三页共用一套导航。
 *
 * DevLoginSwitcher 仅 dev 构建挂载：本地联调用的「测试账号」折叠下拉。
 */
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'

const isDev = import.meta.env.DEV
</script>

<template>
  <RouterView />
  <DevLoginSwitcher v-if="isDev" />
</template>
