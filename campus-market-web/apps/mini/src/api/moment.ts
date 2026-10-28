/**
 * 学生端动态（与 Web 端 `packages/api/src/user-moment.ts` 同契约，后端 `MomentController`）
 *
 * <p>三条形状约定：
 *   ① 操作人由服务端从令牌取，**不传 userId**；`userMoments(userId)` 里的只是「看谁的」。
 *   ② 后端 Long / BigDecimal 全局转字符串，`id`、`likeCount`、`commentCount`、`total`、
 *      `itemPrice` 实际都是字符串，参与运算要 `Number()`。
 *   ③ 点赞 / 取消赞幂等，重复调用返回成功。
 *
 * <p>本端没有 axios，GET 参数走 `data`（uni.request 会拼成 query），与 Web 端相反。
 */

import { request } from '../utils/request'

export interface MomentAuthorVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  /** 0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
}

/** type=1 纯状态；type=2 分享商品（带 item* 摘要） */
export interface MomentVO {
  id: string
  userId: string
  author?: MomentAuthorVO
  type: number
  content?: string
  images?: string[]
  itemId?: string
  itemTitle?: string
  itemMainImage?: string
  itemPrice?: string
  likeCount: string
  liked: boolean
  commentCount: string
  createTime: string
}

export interface MomentCommentVO {
  id: string
  momentId: string
  userId: string
  author?: MomentAuthorVO
  /** 回复目标用户 id，null 表示直接评论动态 */
  replyTo?: string
  replyToNick?: string
  content: string
  createTime: string
}

export interface MomentDetailVO {
  moment: MomentVO
  comments: MomentCommentVO[]
}

export interface MomentPageVO {
  list: MomentVO[]
  total: string
  pageNum: string
  pageSize: string
}

export interface MomentPublishParams {
  /** 1 纯状态 / 2 分享商品 */
  type: number
  content?: string
  images?: string[]
  itemId?: string
}

export const momentApi = {
  publish: (data: MomentPublishParams) =>
    request<string>({ url: '/api/moment/publish', method: 'POST', data }),

  square: (pageNum = 1, pageSize = 10) =>
    request<MomentPageVO>({ url: '/api/moment/square', data: { pageNum, pageSize } }),

  userMoments: (userId: string, pageNum = 1, pageSize = 10) =>
    request<MomentPageVO>({ url: `/api/moment/user/${userId}`, data: { pageNum, pageSize } }),

  detail: (id: string) => request<MomentDetailVO>({ url: `/api/moment/${id}` }),

  like: (id: string) => request<void>({ url: `/api/moment/${id}/like`, method: 'POST' }),

  unlike: (id: string) => request<void>({ url: `/api/moment/${id}/unlike`, method: 'POST' }),

  /** replyTo 传被回复的**用户** id，不传即直接评论 */
  comment: (id: string, content: string, replyTo?: string) =>
    request<void>({
      url: `/api/moment/${id}/comment`,
      method: 'POST',
      data: replyTo ? { content, replyTo } : { content }
    })
}

/** 商品评论一行（后端 `ItemCommentVO`，形状与动态评论一致） */
export interface ItemCommentVO {
  id: string
  itemId: string
  userId: string
  author?: MomentAuthorVO
  /** 回复目标**用户** id */
  replyTo?: string
  replyToNick?: string
  content: string
  createTime: string
}

export interface ItemCommentPageVO {
  list: ItemCommentVO[]
  total: string
  pageNum: string
  pageSize: string
}

/** 商品评论（notes/22 S7）：不限买家、可多次发，仅 ON_SALE 可发 */
export const itemCommentApi = {
  list: (itemId: string, pageNum = 1, pageSize = 50) =>
    request<ItemCommentPageVO>({
      url: `/api/item/${itemId}/comments`,
      data: { pageNum, pageSize }
    }),

  /** replyTo 是目标**用户** id */
  publish: (itemId: string, content: string, replyTo?: string) =>
    request<void>({
      url: `/api/item/${itemId}/comment`,
      method: 'POST',
      data: replyTo ? { content, replyTo } : { content }
    })
}
