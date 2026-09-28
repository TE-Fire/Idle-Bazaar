<script setup lang="ts">
/**
 * KPI 卡（design/03 §4.1）
 *
 * <p>高度 108px、一行 4 个、间距 16px；
 * 结构 = 标题(13px 次要) → 数值(30px/600/tabular-nums) → 同比环比(12px)。
 *
 * <p>涨跌配色遵守**中国股市惯例：涨红跌绿**，并且必须带 ▲/▼ 符号——
 * 不靠颜色单独表意（色盲可读，也是无障碍要求）。
 */

interface Props {
  title: string
  /** 已格式化的展示值（如 "1.2 万"、"¥ 86,400"） */
  value: string
  /** 悬浮显示的精确值：缩写展示时必填，否则用户拿不到真数 */
  exact?: string
  /** 环比：ratio > 0 上升（红），< 0 下降（绿） */
  trend?: { ratio: number; label: string }
}

const props = defineProps<Props>()

const up = (props.trend?.ratio ?? 0) > 0
const down = (props.trend?.ratio ?? 0) < 0
/** 保留一位小数，正号不显示（▲ 已经是正号了） */
const ratioText = props.trend ? `${Math.abs(props.trend.ratio).toFixed(1)}%` : ''
</script>

<template>
  <div class="cm-card kpi">
    <div class="kpi__title">{{ title }}</div>
    <div class="kpi__value" :title="exact">{{ value }}</div>
    <div v-if="trend" class="kpi__trend" :class="{ 'is-up': up, 'is-down': down }">
      <span class="kpi__arrow">{{ up ? '▲' : down ? '▼' : '—' }}</span>
      <span>{{ ratioText }}</span>
      <span class="kpi__label">{{ trend.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.kpi {
  height: 108px;
  padding: var(--cm-space-4);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.kpi__title {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.kpi__value {
  font-size: var(--cm-font-size-xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--cm-color-text-1);
  line-height: 1.15;
}

.kpi__trend {
  font-size: var(--cm-font-size-xs);
  font-variant-numeric: tabular-nums;
  color: var(--cm-color-text-3);
}

/* 涨红跌绿：与股市惯例一致，符号已保证不靠颜色单独表意 */
.kpi__trend.is-up {
  color: var(--cm-color-up);
}

.kpi__trend.is-down {
  color: var(--cm-color-down);
}

.kpi__arrow {
  margin-right: 2px;
}

.kpi__label {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-text-3);
}
</style>
