/**
 * 交易互评（/api/order/{id}/review、/api/user/{id}/reviews，notes/13 O18）
 *
 * <p>四条约定：
 *   ① 提交时**不传被评价人**——`targetId` 由服务端按「订单的对方」推出，
 *      前端传的话差评就能挂到任意人头上。
 *   ② 重复提交返回既有评价（后端幂等），前端不必自己判重，直接把「去评价」按钮换掉即可。
 *   ③ 标签只能取服务端下发的常量表（`tagTable`），前端不许自己编文案。
 *   ④ 能不能评由后端算（`canReview`）+ 截止时间（`expireAt`），前端不自己推窗口。
 */

import { request, type PageResult } from './http'

export interface UserReviewVO {
  id: string
  orderId: string
  reviewerId: string
  reviewerNick?: string | null
  reviewerAvatar?: string | null
  /** 1–5 星 */
  rating: number
  tags?: string[] | null
  comment?: string | null
  createTime: string
}

export interface ReviewTagItem {
  label: string
  /** 1 好评向 / 2 差评向 */
  polarity: number
}

export interface OrderReviewVO {
  orderId: string
  /** 我提交的评价 */
  mine?: UserReviewVO | null
  /** 对方给我的评价 */
  peer?: UserReviewVO | null
  canReview: boolean
  /** 评价截止时间（面交后 30 天），不能评时不返回 */
  expireAt?: string | null
  tagTable?: { positive: ReviewTagItem[]; negative: ReviewTagItem[] }
}

export interface UserReviewCreateReq {
  rating: number
  tags?: string[]
  comment?: string
}

export interface ReviewSummaryVO {
  /** 平均分，字符串（如 "4.8"），无评价时为 "0.0" */
  avg: string
  total: number
  /** 1–5 星条数，五档齐全 */
  counts: Record<string, number>
  latest: UserReviewVO[]
}

export const userReviewApi = {
  /** 提交评价：仅已完成(4)且面交后 30 天内 */
  submit: (orderId: string, req: UserReviewCreateReq) =>
    request<UserReviewVO>({ url: `/api/order/${orderId}/review`, method: 'POST', data: req }),

  /** 该单的评价状态（含标签表） */
  ofOrder: (orderId: string) => request<OrderReviewVO>({ url: `/api/order/${orderId}/review` }),

  /** 某人收到的评价，免登录 */
  list: (userId: string, pageNum = 1, pageSize = 10) =>
    request<PageResult<UserReviewVO>>({
      url: `/api/user/${userId}/reviews`,
      params: { pageNum, pageSize }
    }),

  /** 评价汇总：平均分 + 五档条数 + 最近 3 条，免登录 */
  summary: (userId: string) => request<ReviewSummaryVO>({ url: `/api/user/${userId}/review-summary` })
}
