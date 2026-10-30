/**
 * 交易互评（/api/order/{id}/review、/api/user/{id}/reviews，notes/13 O18）
 *
 * <p>提交时**不传被评价人**：后端按「订单的对方」推出 targetId。
 * 标签只能取后端下发的常量表，前端不硬编码文案。
 */

import { request } from '@/utils/request'

export interface MiniReviewVO {
  id: string
  orderId: string
  reviewerId: string
  reviewerNick?: string
  reviewerAvatar?: string
  rating: number
  tags?: string[]
  comment?: string
  createTime: string
}

export interface ReviewTagItem {
  label: string
  /** 1 好评向 / 2 差评向 */
  polarity: number
}

export interface OrderReviewVO {
  orderId: string
  mine?: MiniReviewVO
  peer?: MiniReviewVO
  canReview: boolean
  expireAt?: string
  tagTable?: { positive: ReviewTagItem[]; negative: ReviewTagItem[] }
}

export interface ReviewSummaryVO {
  avg: string
  total: number
  counts: Record<string, number>
  latest: MiniReviewVO[]
}

export const userReviewApi = {
  submit: (orderId: string, req: { rating: number; tags?: string[]; comment?: string }) =>
    request<MiniReviewVO>({ url: `/api/order/${orderId}/review`, method: 'POST', data: { ...req } }),

  ofOrder: (orderId: string) => request<OrderReviewVO>({ url: `/api/order/${orderId}/review` }),

  list: (userId: string, pageNum = 1, pageSize = 10) =>
    request<{ list: MiniReviewVO[]; total: number }>({
      url: `/api/user/${userId}/reviews`,
      data: { pageNum, pageSize }
    }),

  summary: (userId: string) => request<ReviewSummaryVO>({ url: `/api/user/${userId}/review-summary` })
}
