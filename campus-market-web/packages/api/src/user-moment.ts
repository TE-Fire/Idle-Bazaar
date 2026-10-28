/**
 * 学生端动态（后端 `MomentController`，业务规则见 notes/22 S3–S6）
 *
 * <p>三条形状约定：
 *   ① 操作人由服务端从令牌取，**不传 userId**；`userMoments(userId)` 里的 userId 只是「看谁的」。
 *   ② 后端 `Long` / `BigDecimal` 全局转字符串（JacksonConfig），因此 `id`、`likeCount`、
 *      `commentCount`、`total`、`itemPrice` 实际都是 `"12"` 而非 `12`，参与运算要 `Number()`。
 *   ③ 点赞 / 取消点赞幂等：重复调用返回成功，前端乐观更新即可，失败回滚。
 *
 * <p>敏感词过滤在服务端：命中高危词整条被拒（4024），低危词原地替换为 `*`，
 * 所以前端拿到的 content 已是被替换过的版本——不要在前端再过滤一遍。
 */

import { request } from './http'

/** 动态作者公开信息（后端 `UserPubVO`，与关注列表同一形状） */
export interface MomentAuthorVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  creditScore?: number
  /** 0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
}

/**
 * 一条动态。
 *
 * <p>`type=1` 纯状态（content + images）；`type=2` 分享商品（额外带 item* 摘要，
 * 省掉前端再查一次商品）。`itemId` 存在时才渲染商品卡。
 */
export interface MomentVO {
  id: string
  userId: string
  author?: MomentAuthorVO
  /** 1 纯状态 / 2 分享商品 */
  type: number
  content?: string
  images?: string[]
  itemId?: string
  itemTitle?: string
  itemMainImage?: string
  itemPrice?: string
  /** 以下三项后端是 long → 字符串 */
  likeCount: string
  liked: boolean
  commentCount: string
  /**
   * 0 正常 / 1 敏感词屏蔽 / 2 举报下架。
   * 只有看自己主页时才会拿到 1（前端要在卡片上标「仅自己可见」），广场与看他人恒为 0。
   */
  auditStatus?: number
  createTime: string
}

export interface MomentCommentVO {
  id: string
  momentId: string
  userId: string
  author?: MomentAuthorVO
  /** 回复目标**用户** id；null 表示直接评论动态（不是评论 id） */
  replyTo?: string
  /** 回复目标昵称，前端渲染「回复 @xx」用，省一次查人 */
  replyToNick?: string
  content: string
  createTime: string
}

/** 详情 = 动态本体 + 评论（正序，暂不分页） */
export interface MomentDetailVO {
  moment: MomentVO
  comments: MomentCommentVO[]
}

/** 不是 PageResult：动态流只在广场/主页用，形状简单，计数仍是 long → 字符串 */
export interface MomentPageVO {
  list: MomentVO[]
  total: string
  pageNum: string
  pageSize: string
}

export interface MomentPublishParams {
  /** 1 纯状态 / 2 分享商品；写错返回参数错误 */
  type: number
  content?: string
  /** 最多 9 张，已上传的公开 URL */
  images?: string[]
  /** type=2 必传，且商品必须在售，否则 4023 */
  itemId?: string
}

export const userMomentApi = {
  /** 发布，返回动态 id（字符串） */
  publish: (data: MomentPublishParams) =>
    request<string>({ url: '/api/moment/publish', method: 'POST', data }),

  /** 广场流：全站正常动态，时间倒序 */
  square: (pageNum = 1, pageSize = 10) =>
    request<MomentPageVO>({ url: '/api/moment/square', params: { pageNum, pageSize } }),

  /** 关注流：只看已关注作者，时间倒序；未关注任何人时返回空页 */
  following: (pageNum = 1, pageSize = 10) =>
    request<MomentPageVO>({ url: '/api/moment/following', params: { pageNum, pageSize } }),

  /** 某人主页的动态，时间倒序 */
  userMoments: (userId: string, pageNum = 1, pageSize = 10) =>
    request<MomentPageVO>({ url: `/api/moment/user/${userId}`, params: { pageNum, pageSize } }),

  /** 详情（含评论正序） */
  detail: (id: string) => request<MomentDetailVO>({ url: `/api/moment/${id}` }),

  /** 点赞，幂等 */
  like: (id: string) => request<void>({ url: `/api/moment/${id}/like`, method: 'POST' }),

  /** 取消点赞，幂等 */
  unlike: (id: string) => request<void>({ url: `/api/moment/${id}/unlike`, method: 'POST' }),

  /** 发评论；内容服务端过敏感词。replyTo 传被回复的**用户** id，不传即直接评论 */
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

/**
 * 商品评论（notes/22 S7）。
 *
 * <p>与购后星级评价 `user_review` 是两套：这里不限买家、可多次发，只要求商品在售。
 * 下架后不能新增（4025），但历史评论仍可读。
 */
export const userItemCommentApi = {
  /** 评论列表，正序分页 */
  list: (itemId: string, pageNum = 1, pageSize = 20) =>
    request<ItemCommentPageVO>({
      url: `/api/item/${itemId}/comments`,
      params: { pageNum, pageSize }
    }),

  /** 发评论；内容服务端过敏感词，replyTo 是目标**用户** id */
  publish: (itemId: string, content: string, replyTo?: string) =>
    request<void>({
      url: `/api/item/${itemId}/comment`,
      method: 'POST',
      data: replyTo ? { content, replyTo } : { content }
    })
}
