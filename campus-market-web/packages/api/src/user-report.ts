/**
 * 学生端举报与系统通知（notes/22 批次 3）
 *
 * <p>举报的对象类型是数字（1 商品 / 2 用户 / 3 动态 / 4 商品评论 / 5 动态评论），
 * 与后端 `ReportTargetTypeEnum` 一一对应——前端不下判断，只透传。
 *
 * <p>系统通知与私信是两套未读：通知走 `zone=2`（平台发出，`sender_id=0`），
 * 私信走 `zone=1`，各自计数互不影响。
 */

import { request } from './http'

/** 举报对象类型，与后端枚举对齐 */
export const REPORT_TARGET = {
  ITEM: 1,
  USER: 2,
  MOMENT: 3,
  ITEM_COMMENT: 4,
  MOMENT_COMMENT: 5
} as const

export interface ReportCreateParams {
  targetType: number
  targetId: string
  /** ≤255 字，预置选项拼出来的短语 */
  reason: string
  /** 凭证图 ≤3 张（已上传的公开地址） */
  evidence?: string[]
}

/** 举报原因的预置选项：管理端要按原因归类，自由文本没法聚合 */
export const REPORT_REASONS = ['广告导流', '辱骂攻击', '违法违规', '虚假信息', '其他'] as const

export const userReportApi = {
  /** 提交举报，返回单据 id；同人同对象存在未处理单据时返回 3005 */
  submit: (data: ReportCreateParams) =>
    request<string>({ url: '/api/report', method: 'POST', data })
}

export interface NoticeVO {
  id: string
  /** 4 系统处置（下架/裁决）/ 5 互动（被赞·被评·被关注）/ 6 平台公告 */
  type: number
  /** 只有公告有（存在 extra.title） */
  title?: string | null
  content: string
  /** 0 未读 1 已读 */
  readFlag: number
  createTime: string
}

export interface NoticePageVO {
  list: NoticeVO[]
  total: string
  pageNum: string
  pageSize: string
}

/** 首页播报条的一条公告：无已读态，读过也继续播 */
export interface BulletinVO {
  id: string
  title?: string | null
  content: string
  createTime: string
}

export const userNoticeApi = {
  list: (pageNum = 1, pageSize = 20) =>
    request<NoticePageVO>({ url: '/api/notice/list', params: { pageNum, pageSize } }),

  /** 播报条：免登录，按公告批次去重，最多 3 条 */
  bulletin: (limit = 3) => request<BulletinVO[]>({ url: '/api/notice/bulletin', params: { limit } }),

  /** 未读条数（字符串形式的数字） */
  unread: () => request<string>({ url: '/api/notice/unread' }),

  readAll: () => request<void>({ url: '/api/notice/read-all', method: 'POST' })
}
