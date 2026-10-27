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

export const userSocialApi = {
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
