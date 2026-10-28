/**
 * 管理端社交举报（对应后端 SocialReportAdminController，notes/22 批次 3）
 *
 * <p>与商品举报（`review.report.*`）是两条通道：商品的进审核队列按权重排序，
 * 动态与评论不进队列，只做「下架内容」一种处置。
 */

import { request } from './http'

export interface SocialReportVO {
  id: string
  reporterId: string
  reporterNick?: string
  /** 3 动态 / 4 商品评论 / 5 动态评论 */
  targetType: number
  targetTypeName?: string
  targetId: string
  /** 被举报内容摘要（后端已截断） */
  targetSummary?: string
  targetAuthorId?: string
  targetAuthorNick?: string
  reason: string
  evidence?: string[]
  /** 1 待受理 2 处理中 3 已处理 */
  status: number
  /** 1 成立 2 不成立 */
  result?: number | null
  handleNote?: string
  createTime: string
}

export interface SocialReportPageVO {
  list: SocialReportVO[]
  total: string
  pageNum: string
  pageSize: string
}

export interface SocialReportQuery {
  status?: number
  targetType?: number
  pageNum?: number
  pageSize?: number
}

export const adminSocialReportApi = {
  list: (params: SocialReportQuery = {}) =>
    request<SocialReportPageVO>({
      url: '/admin/social/report/list',
      // 显式展开而不是直接传 params：http 层要的是 Record<string, unknown>
      params: {
        status: params.status,
        targetType: params.targetType,
        pageNum: params.pageNum,
        pageSize: params.pageSize
      }
    }),

  /** result：1 成立（内容下架并通知作者）/ 2 不成立 */
  handle: (reportId: string, data: { result: number; reason?: string; note?: string }) =>
    request<void>({ url: `/admin/social/report/${reportId}/handle`, method: 'POST', data })
}
