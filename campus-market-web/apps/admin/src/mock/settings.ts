/**
 * A9 系统设置的 mock 数据（business/03 A-B9 / F-A9）
 *
 * <p>只剩两块：SLA 配置（settings.edit）、敏感词（settings.edit）。
 * 另外两块都已接真接口——操作日志在 `packages/api/src/log.ts`（2026-10-06 接入），
 * 管理员账号在 `packages/api/src/admin.ts`，都不在这里 mock。
 *
 * <p>SLA 默认值直接取自 business/03 F-A9 的口径：
 * 首审响应 ≤ 2h、P95 ≤ 6h、达标率 ≥ 95%、超 24h 记积压红牌、单条处置 ≤ 30s、申诉 48h。
 */

import type { SensitiveWord, SlaConfig } from '@campus/shared'

/** SLA 阈值可在设置页调整，所以它是「可写」的 mock——页面改的是这份对象 */
export const DEFAULT_SLA: SlaConfig = {
  firstResponseHours: 2,
  p95Hours: 6,
  targetRate: 95,
  backlogHours: 24,
  avgHandleSeconds: 30,
  appealHours: 48
}

export const SENSITIVE_WORDS: SensitiveWord[] = [
  { id: 'w1', word: '加微信', level: '疑似', enabled: true },
  { id: 'w2', word: '代写', level: '拦截', enabled: true },
  { id: 'w3', word: '代考', level: '拦截', enabled: true },
  { id: 'w4', word: '私聊优惠', level: '疑似', enabled: true },
  { id: 'w5', word: '先款后货', level: '拦截', enabled: true },
  { id: 'w6', word: '校外', level: '疑似', enabled: false }
]
