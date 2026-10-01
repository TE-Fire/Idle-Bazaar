/**
 * ECharts 按需引入（tech/02 §6.4：不要全量引 `echarts`）
 *
 * <p>每加一种图，必须在这里补一个 `XxxChart` 与它可能用到的组件，
 * 否则运行期会静默不渲染（ECharts 5 不再在控制台报「Component xxx is not imported」，
 * 只画一个空画布，很难查）。
 *
 * <p>版本组合：echarts 5.6.0 + vue-echarts 7.0.3。
 * vue-echarts 8 起 peer 变成 echarts ^6，与文档锁定的 5.x 冲突，暂不升。
 */

import { use } from 'echarts/core'
import { BarChart, HeatmapChart, LineChart, PieChart } from 'echarts/charts'
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  VisualMapComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

export function setupEcharts(): void {
  use([
    BarChart,
    LineChart,
    PieChart,
    HeatmapChart,
    GridComponent,
    TooltipComponent,
    LegendComponent,
    VisualMapComponent,
    // 环形图中心的「总在售件数」用 graphic 画文字，漏了它那行字会静默消失
    GraphicComponent,
    CanvasRenderer
  ])
}
