/**
 * 学生端社交 · 关注与粉丝（与 Web 端 `packages/api/src/user-social.ts` 同契约）
 *
 * <p>操作人由服务端从令牌取，不传 followerId。`type` 只能是 following / fans。
 * 计数是 long 转字符串，参与运算要 Number()。
 *
 * <p>本端没有 axios，GET 参数走 `data`（uni.request 会拼成 query），与 Web 端相反。
 */

import { request } from '../utils/request'

export type FollowListType = 'following' | 'fans'

export interface FollowUserVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  /** 0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
  /** 我看 TA 是否已关注：粉丝列表里决定显示「回关」还是「已关注」 */
  following?: boolean
  followTime?: string
}

export interface FollowListVO {
  list: FollowUserVO[]
  /** 以下五项后端是 long → 字符串 */
  total: string
  pageNum: string
  pageSize: string
  followingCount: string
  fansCount: string
}

/** 用户公开主页聚合：计数是 long → 字符串 */
export interface UserProfileVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  authStatus?: number
  followingCount: string
  fansCount: string
  onSaleCount: string
  /** 查看者是否已关注（自己的主页恒 false） */
  following: boolean
  self: boolean
}

export const userSocialApi = {
  /** 用户公开主页。userId 留空 = 看自己 */
  profile: (userId?: string) => {
    const data: Record<string, unknown> = {}
    if (userId) {
      data.userId = userId
    }
    return request<UserProfileVO>({ url: '/api/social/profile', data })
  },

  follow: (followeeId: string) =>
    request<void>({ url: '/api/social/follow', method: 'POST', data: { followeeId } }),

  unfollow: (followeeId: string) =>
    request<void>({ url: '/api/social/unfollow', method: 'POST', data: { followeeId } }),

  check: (followeeId: string) =>
    request<boolean>({ url: '/api/social/follow/check', data: { followeeId } }),

  /**
   * 分页列表。`userId` 只在看他人时传——**不要传 undefined**，
   * uni 会把参数原样拼进 query，服务端收到字符串 "undefined" 直接报用户不存在。
   */
  list: (type: FollowListType, pageNum = 1, pageSize = 20, userId?: string) => {
    const data: Record<string, unknown> = { type, pageNum, pageSize }
    if (userId) {
      data.userId = userId
    }
    return request<FollowListVO>({ url: '/api/social/follow/list', data })
  }
}

/* ── 举报与系统通知（notes/22 批次 3）── */

/** 举报对象类型，与后端 ReportTargetTypeEnum 对齐 */
export const REPORT_TARGET = {
  ITEM: 1,
  USER: 2,
  MOMENT: 3,
  ITEM_COMMENT: 4,
  MOMENT_COMMENT: 5
} as const

/** 预置原因：管理端按原因归类，自由文本没法聚合 */
export const REPORT_REASONS = ['广告导流', '辱骂攻击', '违法违规', '虚假信息', '其他']

export const reportApi = {
  /** 同人同对象有未处理单据时返回 3005 */
  submit: (targetType: number, targetId: string, reason: string) =>
    request<string>({ url: '/api/report', method: 'POST', data: { targetType, targetId, reason } })
}

export interface NoticeVO {
  id: string
  /** 4 系统处置 / 5 互动（被赞·被评·被关注）/ 6 平台公告 */
  type: number
  /** 只有公告有（后端从 extra.title 解析） */
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

export const noticeApi = {
  list: (pageNum = 1, pageSize = 20) =>
    request<NoticePageVO>({ url: '/api/notice/list', data: { pageNum, pageSize } }),

  unread: () => request<string>({ url: '/api/notice/unread' }),

  /** 单条已读：点开一封时调用，幂等 */
  readOne: (id: string) => request<void>({ url: `/api/notice/${id}/read`, method: 'POST' }),

  readAll: () => request<void>({ url: '/api/notice/read-all', method: 'POST' })
}
