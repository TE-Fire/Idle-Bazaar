<script setup lang="ts">
/**
 * KPI 卡（design/03 §4.1）
 *
 * <p>高度 116px、一行 4 个、间距 16px；
 * 结构 = 标题(13px 次要) → 数值(28px/600/tabular-nums) → 涨跌徽章(浅色胶囊底)。
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
    <div v-if="trend" class="kpi__trend">
      <span class="kpi__badge" :class="{ 'is-up': up, 'is-down': down }">
        <span>{{ up ? '▲' : down ? '▼' : '—' }}</span>
        <span>{{ ratioText }}</span>
      </span>
      <span class="kpi__label">{{ trend.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.kpi {
  height: 116px;
  padding: var(--cm-space-5);
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
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--cm-color-text-1);
  line-height: 1.15;
}

.kpi__trend {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  font-variant-numeric: tabular-nums;
}

/*
 * 涨跌徽章：浅色胶囊底 + 对应文字色。
 * color-mix 从令牌色派生浅底，避免为「红 10% 透明」再造一组令牌；
 * 不支持 color-mix 的旧浏览器只是没有底色，文字颜色不受影响。
 */
.kpi__badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: var(--cm-radius-pill);
  font-weight: 500;
}

.kpi__badge.is-up {
  color: var(--cm-color-up);
  background: color-mix(in srgb, var(--cm-color-up) 10%, transparent);
}

.kpi__badge.is-down {
  color: var(--cm-color-down);
  background: color-mix(in srgb, var(--cm-color-down) 10%, transparent);
}

.kpi__label {
  color: var(--cm-color-text-3);
}
</style>
