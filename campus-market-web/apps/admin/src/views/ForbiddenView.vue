<script setup lang="ts">
/**
 * 403：菜单级权限不足（路由守卫命中 meta.perm 但未持有）
 *
 * <p>与按钮级不同：按钮是无痕移除，页面级必须给明确反馈，
 * 否则用户只看到「点了没反应」。
 */

import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <a-result status="403" title="403" sub-title="当前角色没有访问该页面的权限">
    <template #extra>
      <a-button type="primary" @click="router.push('/dashboard')">返回数据看板</a-button>
    </template>
  </a-result>
</template>
