<script setup lang="ts">
/**
 * 根组件：只挂 router-view。
 * 全局主题（主色 #1677FF）走 ConfigProvider，与令牌 CSS 变量同源。
 */
// 中文包走默认导出路径：'ant-design-vue/es/locale' 只有类型，没有具名导出
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>

<template>
  <a-config-provider :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>
