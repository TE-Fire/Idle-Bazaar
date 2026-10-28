/**
 * 学生端社交 · 关注与粉丝（后端 `SocialController`，业务规则见 notes/22 S1·S2）
 *
 * <p>三条与后端约定的形状：
 *   ① 操作人由服务端从令牌取，**不传 followerId**——传了也不看，等于允许替别人关注。
 *   ② `type` 只能是 `following` / `fans`，写错返回 参数错误(1000)。
 *   ③ 计数（total / followingCount / fansCount）后端是 `long`，全局 Jackson 把 Long 转字符串，
 *      所以实际是 `"12"` 而不是 `12`。参与运算或传给需要数字的组件前要 `Number()`。
 *
 * <p>关注 / 取关都是幂等的：重复调用返回成功，前端不必自己拦重复点击（按钮上加 loading 只是为了观感）。
 */

import { request } from './http'

/** following=我关注的人，fans=关注我的人 */
export type FollowListType = 'following' | 'fans'

export interface FollowUserVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  /** 认证：0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
  /** 当前查看者是否已关注这个人；粉丝列表里决定按钮显示「回关」还是「已关注」 */
  following?: boolean
  /** 关系建立时间，倒序排列的依据 */
  followTime?: string
}

/** 不是 PageResult：后端多带了目标用户的两项计数，省掉主页再一次统计请求 */
export interface FollowListVO {
  list: FollowUserVO[]
  /** 当前 type 的总条数（long → 字符串） */
  total: string
  pageNum: string
  pageSize: string
  /** 目标用户的关注数（long → 字符串） */
  followingCount: string
  /** 目标用户的粉丝数（long → 字符串） */
  fansCount: string
}

export interface FollowListParams {
  type?: FollowListType
  /** 留空 = 看自己；传他人 id = 看其公开主页 */
  userId?: string
  pageNum?: number
  pageSize?: number
}

/** 用户公开主页聚合（后端 `UserProfileVO`）：计数是 long → 字符串 */
export interface UserProfileVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  /** 认证：0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
  followingCount: string
  fansCount: string
  /** 在售商品数 */
  onSaleCount: string
  /** 查看者是否已关注（自己的主页恒 false） */
  following: boolean
  /** 是否自己的主页 */
  self: boolean
}

export const userSocialApi = {
  /** 用户公开主页。userId 留空 = 看自己 */
  profile: (userId?: string) =>
    request<UserProfileVO>({ url: '/api/social/profile', params: userId ? { userId } : {} }),

  /** 关注。已关注幂等成功；关注自己 / 用户不存在 → 1000 */
  follow: (followeeId: string) =>
    request<void>({ url: '/api/social/follow', method: 'POST', data: { followeeId } }),

  /** 取关。未关注幂等成功 */
  unfollow: (followeeId: string) =>
    request<void>({ url: '/api/social/unfollow', method: 'POST', data: { followeeId } }),

  /** 是否已关注，个人主页按钮的初始态 */
  check: (followeeId: string) =>
    request<boolean>({ url: '/api/social/follow/check', params: { followeeId } }),

  /**
   * 关注 / 粉丝分页列表。
   *
   * <p>查询参数走 `params`：axios 的 GET `data` 会被静默丢弃（与 user-order.ts 同坑）。
   */
  list: (params: FollowListParams = {}) =>
    request<FollowListVO>({ url: '/api/social/follow/list', params: { ...params } })
}
