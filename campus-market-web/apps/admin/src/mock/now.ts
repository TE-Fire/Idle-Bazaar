/**
 * mock 的「当前时间」基准
 *
 * <p>审核队列的等待时长、权重分、SLA 档位全部由 `submitAt` 与「现在」的差值算出。
 * 如果直接用 `Date.now()`，每次刷新数字都在变——论文里贴的截图对不上，
 * UI 问题也难以复现（与 `mock/dashboard.ts` 不用 Math.random 是同一个理由）。
 *
 * <p>所以这里钉死一个基准时刻，所有派生量都相对它计算。
 * 后端接口就绪后删掉本文件，等待时长改由服务端下发。
 */

export const MOCK_NOW = new Date('2026-09-30T10:00:00+08:00')

/** 两个时间点相差多少小时（可为负，调用方自行 clamp） */
export const hoursBetween = (from: string | Date, to: Date = MOCK_NOW): number => {
  const a = typeof from === 'string' ? new Date(from) : from
  return (to.getTime() - a.getTime()) / 3_600_000
}

/** 相差多少分钟 */
export const minutesBetween = (from: string | Date, to: Date = MOCK_NOW): number => {
  return Math.round(hoursBetween(from, to) * 60)
}

/** 「3 小时 12 分」这种人读格式，用于等待时长列 */
export const humanizeWait = (minutes: number): string => {
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h < 24) return m === 0 ? `${h} 小时` : `${h} 小时 ${m} 分`
  const d = Math.floor(h / 24)
  return `${d} 天 ${h % 24} 小时`
}
