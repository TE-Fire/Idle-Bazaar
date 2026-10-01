/**
 * 审核时效 SLA 的 mock：近 14 日按天聚合 + 汇总（business/03 F-A9）
 *
 * <p>为什么是「按天已聚合」的数据形态：
 * 分位数（P95）必须按全量样本算，前端拿到单条流水自己算既慢又不准，
 * 真实接口应该由后端统计好给 `{day, handled, ok, avgMin, p95Min}` 这种日聚合对象。
 * mock 阶段直接给聚合值，接真接口时把 `SLA_DAILY` 换成接口返回即可，组件不用改。
 *
 * <p>两级指标不许混用（F-A9 判据）：
 *  ① 响应时效（对卖家的承诺）：首审 ≤ 2h、P95 ≤ 6h、达标率 ≥ 95%、超 24h 记积压红牌
 *  ② 操作效率（对审核员的要求）：单条平均处置 ≤ 30s（因此详情用 Drawer，不跳新页）
 */

import type { SlaConfig } from '@campus/shared'

/** 一天的聚合结果 */
export interface SlaDayStat {
  /** 日期（MM-DD） */
  day: string
  /** 当日已处置件数 */
  handled: number
  /** 其中首审响应 ≤ 阈值（默认 2h）的件数 */
  ok: number
  /** 当日平均首审时长（分钟） */
  avgMin: number
  /** 当日 P95 首审时长（分钟） */
  p95Min: number
}

/**
 * 近 14 日（09-16 ~ 09-29，MOCK_NOW 为 2026-09-30 的前 14 天）。
 *
 * <p>刻意保留 09-24 这一天异常（平均 132 分钟，超 120 分钟目标线）：
 * SLA 卡片要能看出「哪天出了问题」，全绿的图对运营没有价值。
 */
export const SLA_DAILY: SlaDayStat[] = [
  { day: '09-16', handled: 32, ok: 31, avgMin: 38, p95Min: 86 },
  { day: '09-17', handled: 36, ok: 36, avgMin: 42, p95Min: 98 },
  { day: '09-18', handled: 28, ok: 28, avgMin: 35, p95Min: 78 },
  { day: '09-19', handled: 41, ok: 40, avgMin: 51, p95Min: 112 },
  { day: '09-20', handled: 38, ok: 38, avgMin: 47, p95Min: 104 },
  { day: '09-21', handled: 30, ok: 30, avgMin: 39, p95Min: 88 },
  { day: '09-22', handled: 35, ok: 34, avgMin: 44, p95Min: 96 },
  { day: '09-23', handled: 44, ok: 43, avgMin: 58, p95Min: 124 },
  { day: '09-24', handled: 36, ok: 24, avgMin: 132, p95Min: 392 },
  { day: '09-25', handled: 42, ok: 41, avgMin: 55, p95Min: 118 },
  { day: '09-26', handled: 33, ok: 33, avgMin: 41, p95Min: 92 },
  { day: '09-27', handled: 29, ok: 29, avgMin: 36, p95Min: 80 },
  { day: '09-28', handled: 26, ok: 26, avgMin: 33, p95Min: 74 },
  { day: '09-29', handled: 24, ok: 24, avgMin: 29, p95Min: 68 }
]

/** 单条平均处置（秒）——操作效率，与响应时效是两个层级，不要放在一起比较 */
export const AVG_HANDLE_SECONDS = 24

export interface SlaSummary {
  /** 平均首审时长（分钟），按处置量加权 */
  avgMin: number
  /** 达标率（%） */
  okRate: number
  /** 整体 P95（分钟），各日 P95 按处置量加权近似 */
  p95Min: number
  /** 单条平均处置（秒） */
  handleSec: number
}

/**
 * 汇总近 14 日的**已处置**统计量。
 *
 * <p>`p95Min` 用加权平均近似，并在 UI 上标「≈」：真实系统应由后端按全量样本算 P95，
 * 这里只有按天聚合值，直接把某天的 P95 当整体 P95 是错的，取加权才勉强可用——
 * 论文里就写「整体 P95 由后端按全量样本计算」，mock 阶段标注近似值。
 *
 * <p>积压件数**不在这里**：它是「当前尚未处置」的实时量，来自审核队列，口径不同不要掺进来。
 */
export const slaSummary = (): SlaSummary => {
  const handled = SLA_DAILY.reduce((s, d) => s + d.handled, 0)
  const ok = SLA_DAILY.reduce((s, d) => s + d.ok, 0)
  const minutes = SLA_DAILY.reduce((s, d) => s + d.avgMin * d.handled, 0)
  const p95 = SLA_DAILY.reduce((s, d) => s + d.p95Min * d.handled, 0)

  return {
    avgMin: Math.round(minutes / handled),
    okRate: Math.round((ok / handled) * 1000) / 10,
    p95Min: Math.round(p95 / handled),
    handleSec: AVG_HANDLE_SECONDS
  }
}

/** 目标线的分钟数：阈值可在设置页调，所以从配置算，不写死 120 */
export const targetMinutes = (cfg: SlaConfig): number => cfg.firstResponseHours * 60

/* ── 分审核员明细（F-A9 呈现点之三：报表分审核员明细） ─────────── */

export interface ReviewerSlaStat {
  name: string
  handled: number
  ok: number
  /** 累计首审时长（分钟）：avgMin = minutesTotal ÷ handled，存总量才能保证按处置量加权可加 */
  minutesTotal: number
  p95Min: number
  handleSec: number
}

/**
 * 两位审核员近 14 日的聚合。**数字不是随手写的**：
 * handled 合计 474、ok 合计 457、minutesTotal 合计 23860，均与 SLA_DAILY 逐日求和严格相等
 * （221×45 + 253×55 = 23860 = Σ avgMin×handled）。报表的「合计」行与看板 SLA 卡是同一个数，
 * 分审核员只是把同一份事实按人拆开——否则又造出「同一指标两个数」。
 */
export const REVIEWER_SLA: ReviewerSlaStat[] = [
  { name: '审核员·李', handled: 221, ok: 215, minutesTotal: 9945, p95Min: 96, handleSec: 22 },
  // 周的单条平均处置 31s 刻意超 30s 目标：整表全绿对运营没有信息量（与看板 09-24 红柱同理）
  { name: '审核员·周', handled: 253, ok: 242, minutesTotal: 13915, p95Min: 138, handleSec: 31 }
]

export interface ReviewerSlaRow {
  key: string
  name: string
  handled: number
  avgMin: number
  okRate: number
  p95Min: number
  handleSec: number
}

/**
 * 派生展示行。合计行的 avgMin / okRate / p95 / handleSec 全部取 `slaSummary()` 的结果
 * （与看板 SLA 卡同一出处），不在这里重新加权——重新算必然出现尾数不一致。
 */
export const reviewerSlaRows = (summary: SlaSummary): ReviewerSlaRow[] => [
  ...REVIEWER_SLA.map((r, i) => ({
    key: `r${i}`,
    name: r.name,
    handled: r.handled,
    avgMin: Math.round(r.minutesTotal / r.handled),
    okRate: Math.round((r.ok / r.handled) * 1000) / 10,
    p95Min: r.p95Min,
    handleSec: r.handleSec
  })),
  {
    key: 'total',
    name: '合计',
    handled: REVIEWER_SLA.reduce((s, r) => s + r.handled, 0),
    avgMin: summary.avgMin,
    okRate: summary.okRate,
    p95Min: summary.p95Min,
    handleSec: summary.handleSec
  }
]
