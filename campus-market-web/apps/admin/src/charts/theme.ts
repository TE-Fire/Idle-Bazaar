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
 *
 * 2026-10-01 全站切 iOS/Apple 风：色板对齐 Apple 系统色 + 品牌蓝 #0071E3，
 * 与 design/03 §ECharts 全局配置约定、admin.semantic.json 三处同源。
 */
export const CHART_COLORS: string[] = [
  '#0071E3',
  '#34C759',
  '#FF9F0A',
  '#AF52DE',
  '#5AC8FA',
  '#FF3B30',
  '#8E8E93',
  '#FF2D55'
]

/** 通用 grid：四边留够，避免轴标签被裁 */
export const GRID = { left: 48, right: 24, top: 32, bottom: 32 } as const

export const AXIS_LINE = { lineStyle: { color: '#D2D2D7' } } as const
export const AXIS_LABEL = { color: '#8E8E93', fontSize: 12 } as const
export const SPLIT_LINE = { lineStyle: { color: '#E8E8ED', type: 'dashed' } } as const

export const TOOLTIP = {
  trigger: 'axis',
  backgroundColor: 'rgba(29,29,31,.92)',
  borderWidth: 0,
  borderRadius: 10,
  /*
   * padding 只能写单个数字：TOOLTIP 带 as const，数组会被定型成 readonly 元组，
   * 而 EChartsOption 要的是可变 number[]（与 CHART_COLORS 不能 as const 同理）。
   */
  padding: 12,
  textStyle: { color: '#fff', fontSize: 12 },
  /** ECharts 不吃 CSS 变量，阴影只能写死在这里（design/03 已同步） */
  extraCssText: 'box-shadow: 0 8px 30px rgba(0,0,0,.18);'
} as const

/** 折线/面积：smooth 必须为 false（数据要精确，不做平滑） */
export const LINE_SERIES_BASE = {
  smooth: false,
  symbol: 'circle',
  symbolSize: 6,
  lineStyle: { width: 2 }
} as const

/** 柱状：柱宽 ≤24px，圆角 [6,6,0,0]（同样不能 as const，理由见 CHART_COLORS） */
export const BAR_SERIES_BASE = {
  barMaxWidth: 24,
  itemStyle: { borderRadius: [6, 6, 0, 0] as number[] }
}
