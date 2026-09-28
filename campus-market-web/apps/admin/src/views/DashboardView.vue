<script setup lang="ts">
/**
 * A2 数据看板 —— 骨架占位
 *
 * <p>按 tech/02 第十章的落地顺序，看板（4 KPI + 四张校园图）是第 3 步，
 * 且依赖后端统计接口与 ECharts。这里先占位，等接口就绪后整页替换。
 */

import { useUserStore } from '@/stores/user'

const user = useUserStore()
</script>

<template>
  <a-alert
    type="info"
    show-icon
    message="A2 数据看板尚未实现"
    :description="`骨架阶段占位页。当前登录 ${user.realName || user.adminId || '—'}，持有 ${user.permCodes.length} 个权限点。看板在 F2 工序实现（KPI×4 + 四张校园图）。`"
  />
</template>
