/**
 * 系统设置（/admin/settings，notes/25 §四）
 *
 * <p>目前只有 SLA 六个阈值。阈值落 `sys_config` 而不是前端常量：
 * 设置页改完刷新就丢过一次，「可调」就成了空话。写完全量六键，后端清缓存后
 * 看板、报表、审核队列三处立刻按新阈值判定。
 */

import { request } from './http'
import type { SlaThresholds } from './stats'

export const settingsApi = {
  sla: () => request<SlaThresholds>({ url: '/admin/settings/sla' }),

  updateSla: (data: SlaThresholds) =>
    request<void>({ url: '/admin/settings/sla', method: 'PUT', data })
}
