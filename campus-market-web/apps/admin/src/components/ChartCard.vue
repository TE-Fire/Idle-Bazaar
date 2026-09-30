<script setup lang="ts">
/**
 * 图表卡（design/03 §4.2）
 *
 * 高度统一 300px（含标题区），大图 360px；
 * 结构 = 标题 + 右上角时间粒度切换（具名插槽 extra）+ 图表 + 图例。
 *
 * loading 时用等高骨架块占位，避免图渲染完再撑开导致跳版。
 */

interface Props {
  title: string
  /** 含标题区的总高度，规范默认 300 */
  height?: number
  loading?: boolean
}

withDefaults(defineProps<Props>(), { height: 300, loading: false })
</script>

<template>
  <div class="cm-card chart-card" :style="{ height: `${height}px` }">
    <div class="chart-card__head">
      <span class="chart-card__title">{{ title }}</span>
      <slot name="extra" />
    </div>
    <div class="chart-card__body">
      <a-skeleton v-if="loading" active :paragraph="{ rows: 5 }" />
      <slot v-else />
    </div>
  </div>
</template>

<style scoped>
.chart-card {
  display: flex;
  flex-direction: column;
}

/* 标题区不再压分隔线：Apple 卡片靠留白分层，不靠线 */
.chart-card__head {
  height: 52px;
  padding: 0 var(--cm-space-5);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chart-card__title {
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.chart-card__body {
  flex: 1;
  min-height: 0;
  padding: 0 var(--cm-space-3) var(--cm-space-3);
}
</style>
