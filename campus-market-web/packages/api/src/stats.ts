/**
 * 统计接口（/admin/stats，口径见 notes/25）
 *
 * <p>看板六块一次返回：拆成六个接口会让首屏出现「KPI 已经到了、图还在转」，
 * 前端还得自己拼加载态。SLA 卡是本页第七块，与报表「分审核员明细」口径不同，别互相推导。
 *
 * <p>金额与比率都是字符串（项目统一约定），展示前 `Number()` 再运算。
 */

import { request } from './http'

export interface DashboardKpi {
  /** 累计 GMV，两位小数字符串 */
  gmv: string
  /** 今日新增商品 */
  itemCount: number
  /** 今日成交笔数 */
  orderCount: number
  /** 今日活跃用户 */
  activeUsers: number
}

export interface DashboardTrend {
  labels: string[]
  published: number[]
  sold: number[]
  gmv: string[]
}

export interface NameValue {
  name: string
  value: number
}

export interface DashboardPending {
  audit: number
  report: number
  appeal: number
  refund: number
}

export interface DashboardAlert {
  /** price 价格异常 / brush 高频发布 / credit 低信用在售 */
  key: string
  count: number
}

export interface SlaThresholds {
  firstResponseHours: number
  p95Hours: number
  targetRate: number
  backlogHours: number
  avgHandleSeconds: number
  appealHours: number
}

export interface DashboardSla {
  thresholds: SlaThresholds
  handled: number
  avgMin: number
  /** 达标率百分数字符串 */
  okRate: string
  /** P95 分钟数字符串 */
  p95Min: string
  backlog: number
  handleSec: number
  daily: Array<{ day: string; value: number }>
}

export interface DashboardVO {
  kpi: DashboardKpi
  trend: DashboardTrend
  categorySlices: NameValue[]
  gradeBars: NameValue[]
  topCategories: NameValue[]
  topColleges: NameValue[]
  pending: DashboardPending
  alerts: DashboardAlert[]
  sla: DashboardSla
}

export interface ReportTimeline {
  labels: string[]
  published: number[]
  sold: number[]
}

export interface ReportMatrix {
  metric: string
  colleges: string[]
  categories: string[]
  /** 与 colleges / categories 同序对齐：cells[行][列] */
  cells: number[][]
}

export interface ReportMeetHeat {
  points: string[]
  slots: string[]
  cells: number[][]
}

export interface ReviewerSlaRow {
  name: string
  handled: number
  avgMin: number
  okRate: string
  p95Min: string
  handleSec: number
}

export interface ReportSlaTable {
  rows: ReviewerSlaRow[]
  total: ReviewerSlaRow
}

export type MatrixMetric = 'sold' | 'published' | 'rate'

export const statsApi = {
  /** days 只影响趋势区，其余块与窗口无关 */
  dashboard: (days: 7 | 30) => request<DashboardVO>({ url: '/admin/stats/dashboard', params: { days } }),

  timeline: (weeks = 18) =>
    request<ReportTimeline>({ url: '/admin/stats/report/timeline', params: { weeks } }),

  matrix: (metric: MatrixMetric = 'sold') =>
    request<ReportMatrix>({ url: '/admin/stats/report/matrix', params: { metric } }),

  meetPoint: () => request<ReportMeetHeat>({ url: '/admin/stats/report/meet-point' }),

  sla: () => request<ReportSlaTable>({ url: '/admin/stats/report/sla' })
}
