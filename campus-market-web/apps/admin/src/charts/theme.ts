/**
 * ECharts 全局配置（design/03 §4.2「ECharts 全局配置约定」）
 *
 * <p>这里的值是**从设计规范抄下来的**，不是拍脑袋配的。改配色前先改 design/03，
 * 再改这里，最后改 `packages/tokens`——三处不一致时以 design/03 为准（论文依据）。
 */

/**
 * 8 色循环，首色为品牌蓝。
 * 这里**不能加 `as const`**：ECharts 的 `color` 要的是可变数组 `ZRColor[]`，
 * readonly 元组赋值不进去（`as const` 只在某些常量上安全，数组类的一律别加）。
 */
export const CHART_COLORS: string[] = [
  '#1677FF',
  '#0FA968',
  '#FF8F1F',
  '#7C4DFF',
  '#00B5C4',
  '#E5484D',
  '#8C8C94',
  '#F5A623'
]

/** 通用 grid：四边留够，避免轴标签被裁 */
export const GRID = { left: 48, right: 24, top: 32, bottom: 32 } as const

export const AXIS_LINE = { lineStyle: { color: '#E5E7EB' } } as const
export const AXIS_LABEL = { color: '#9A9AA4', fontSize: 12 } as const
export const SPLIT_LINE = { lineStyle: { color: '#F0F0F4', type: 'dashed' } } as const

export const TOOLTIP = {
  trigger: 'axis',
  backgroundColor: 'rgba(31,31,38,.9)',
  textStyle: { color: '#fff', fontSize: 12 }
} as const

/** 折线/面积：smooth 必须为 false（数据要精确，不做平滑） */
export const LINE_SERIES_BASE = {
  smooth: false,
  symbol: 'circle',
  symbolSize: 6,
  lineStyle: { width: 2 }
} as const

/** 柱状：柱宽 ≤24px，圆角 [4,4,0,0]（同样不能 as const，理由见 CHART_COLORS） */
export const BAR_SERIES_BASE = {
  barMaxWidth: 24,
  itemStyle: { borderRadius: [4, 4, 0, 0] as number[] }
}
