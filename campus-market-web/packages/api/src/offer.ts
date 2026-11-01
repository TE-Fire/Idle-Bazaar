/**
 * 议价（/api/offer，状态机见 notes/28）
 *
 * <p>一对一的私密价：出价只对卖家可见，卖家同意后买家按该价付款，
 * **商品标价全程不变**——其他买家看到的仍是原价。因此这里没有任何修改商品的接口。
 *
 * <p>聊天卡片不调这里的 detail：消息列表已按最新状态回填 extra，
 * 卡片点开才需要 detail 拿完整信息（商品摘要、对方昵称）。
 */

import { request } from './http'
import type { OfferCardPayload } from './chat'

export type OfferStatus = OfferCardPayload['status']

export interface OfferVO {
  id: string
  itemId: string
  itemTitle?: string | null
  itemMainImage?: string | null
  /** 商品当前标价（不是议价价） */
  itemPrice?: string | null
  originPrice: string
  offerPrice: string
  status: OfferStatus
  /** 付款截止，仅 AGREED 有值 */
  expireTime?: string | null
  buyerId: string
  sellerId: string
  /** 对方昵称 */
  peerNick?: string | null
  rejectReason?: string | null
  createTime: string
  /** 当前登录人是否为卖家，决定展示「同意/拒绝」还是「去付款」 */
  asSeller: boolean
}

export interface OfferPage {
  list: OfferVO[]
  total: number
  pageNum: number
  pageSize: number
}

export const userOfferApi = {
  /** 发起议价：出价须低于标价且不低于标价的 10%；同商品同时只能有一条进行中 */
  create: (itemId: string, offerPrice: number) =>
    request<string>({ url: '/api/offer/create', method: 'POST', data: { itemId, offerPrice } }),

  /** 卖家同意：买家可在 10 分钟内按该价付款 */
  agree: (id: string) => request<void>({ url: `/api/offer/${id}/agree`, method: 'POST' }),

  reject: (id: string, reason?: string) =>
    request<void>({ url: `/api/offer/${id}/reject`, method: 'POST', data: reason ? { reason } : {} }),

  /** 买家撤回：撤回后不占名额，可立刻重新出价 */
  cancel: (id: string) => request<void>({ url: `/api/offer/${id}/cancel`, method: 'POST' }),

  list: (role: 'buyer' | 'seller' = 'buyer', pageNum = 1, pageSize = 10) =>
    request<OfferPage>({ url: '/api/offer/list', params: { role, pageNum, pageSize } }),

  detail: (id: string) => request<OfferVO>({ url: `/api/offer/${id}` })
}
