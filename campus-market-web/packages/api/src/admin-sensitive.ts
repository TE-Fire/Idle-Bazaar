/**
 * 管理端敏感词库（对应后端 `SensitiveWordAdminController`，notes/22 §五）
 *
 * <p>词库同时作用于发帖、动态评论、商品评论与商品描述——改一个词全端生效。
 * 后端改完自增 `cm:sensitive:ver`，各实例 3 秒内惰性重载，前端不需要额外的刷新动作。
 *
 * <p>`level` 用数字而非「拦截 / 疑似」：数字是契约口径，展示文案由页面按需映射，
 * 后端新增中间级别时前端不用改类型。
 */

import { request, type PageResult } from './http'

export interface SensitiveWordVO {
  id: string
  word: string
  /** 1 高危（发布直接拒）/ 2 低危（替换成等长 *） */
  level: number
  /** 1 启用 / 0 停用 */
  status: number
  createTime: string
}

export interface SensitiveWordQuery {
  level?: number
  status?: number
  pageNum?: number
  pageSize?: number
}

export interface SensitiveWordSaveReq {
  word: string
  /** 缺省按 2 低危处理 */
  level?: number
}

export const adminSensitiveApi = {
  list: (params: SensitiveWordQuery = {}) =>
    request<PageResult<SensitiveWordVO>>({
      url: '/admin/sensitive/list',
      // 显式展开：http 层要的是 Record<string, unknown>
      params: {
        level: params.level,
        status: params.status,
        pageNum: params.pageNum,
        pageSize: params.pageSize
      }
    }),

  create: (data: SensitiveWordSaveReq) =>
    request<void>({ url: '/admin/sensitive', method: 'POST', data }),

  update: (id: string, data: SensitiveWordSaveReq) =>
    request<void>({ url: `/admin/sensitive/${id}`, method: 'PUT', data }),

  setEnabled: (id: string, enabled: boolean) =>
    request<void>({ url: `/admin/sensitive/${id}/status`, method: 'POST', data: { enabled } }),

  remove: (id: string) => request<void>({ url: `/admin/sensitive/${id}`, method: 'DELETE' })
}
