/**
 * 展示格式化（三端共用）
 *
 * <p>规则来自 design/01 §七：价格统一 `¥` + 阿拉伯数字，不带小数时省略 `.00`；
 * 时间相对显示（3 小时前 / 昨天），超过 7 天显示绝对日期。
 */

/** `¥128` / `¥128.50`；`null | undefined` 返回占位符，避免视图里到处判空 */
export function formatPrice(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '¥—'
  const fixed = Math.round(value * 100) / 100
  return Number.isInteger(fixed) ? `¥${fixed}` : `¥${fixed.toFixed(2)}`
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 相对时间；`now` 可注入，便于测试与「论文截图固定」场景。
 * 超过 7 天返回 `YYYY-MM-DD`。
 */
export function formatRelativeTime(iso: string, now: number = Date.now()): string {
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return '—'
  const diff = now - t

  if (diff < MINUTE) return '刚刚'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} 分钟前`
  if (diff < DAY) return `${Math.floor(diff / HOUR)} 小时前`
  if (diff < 2 * DAY) return '昨天'
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)} 天前`

  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
