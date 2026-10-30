/**
 * A9 系统设置的 mock 数据（business/03 A-B9 / F-A9）
 *
 * <p>只剩 SLA 一块：敏感词已接真接口（`packages/api/src/admin-sensitive.ts`，2026-10-30），
 * 操作日志在 `packages/api/src/log.ts`（2026-10-06 接入），管理员账号在 `packages/api/src/admin.ts`，
 * 都不在这里 mock。
 *
 * <p>SLA 默认值直接取自 business/03 F-A9 的口径：
 * 首审响应 ≤ 2h、P95 ≤ 6h、达标率 ≥ 95%、超 24h 记积压红牌、单条处置 ≤ 30s、申诉 48h。
 */

import type { SlaConfig } from '@campus/shared'

/** SLA 阈值可在设置页调整，所以它是「可写」的 mock——页面改的是这份对象 */
export const DEFAULT_SLA: SlaConfig = {
  firstResponseHours: 2,
  p95Hours: 6,
  targetRate: 95,
  backlogHours: 24,
  avgHandleSeconds: 30,
  appealHours: 48
}
