/**
 * 询单留言（/api/chat，同步模式——无推送，前端进会话轮询 + 拉取即已读）
 *
 * <p>会话按「商品 + 双方」定位：列表项带 itemId + peerUserId，进详情/发消息
 * 都用这两个 id，不传 sessionId（服务端 upsert，前端拿不到也不需要）。
 * itemTitle / itemMainImage / price / status 供会话窗顶部商品卡片展示。
 */

import { request } from './http'
import type { ItemStatus } from './item'

export interface ChatConversationVO {
  itemId: string
  itemTitle?: string | null
  itemMainImage?: string | null
  /** 商品价格（MoneyString）；面交谈价参照，卡片展示 */
  price?: string | null
  /** 商品状态枚举名；非 ON_SALE 时卡片置灰提示 */
  status?: ItemStatus | null
  peerUserId: string
  peerNick?: string | null
  /** 对方头像；为空时前端回退昵称首字 */
  peerAvatar?: string | null
  lastContent?: string | null
  lastAt?: string | null
  unread: number
}

export interface ChatMessageVO {
  id: string
  senderId: string
  content: string
  /** 1 文本 / 7 议价卡片 */
  type?: number
  /** 卡片载荷（JSON 串）：offerId / itemId / originPrice / offerPrice / status / expireTime */
  extra?: string | null
  createdAt: string
  /** 服务端按发送人与当前登录用户比较给出，前端不自己判断左右 */
  mine: boolean
}

/** 议价卡片载荷（服务端按最新单据回填，不是发送时的快照） */
export interface OfferCardPayload {
  offerId: string
  itemId: string
  originPrice: string
  offerPrice: string
  status: 'PENDING' | 'AGREED' | 'REJECTED' | 'EXPIRED' | 'ORDERED'
  expireTime?: string
}

export const OFFER_STATUS_LABELS: Record<OfferCardPayload['status'], string> = {
  PENDING: '待卖家答复',
  AGREED: '卖家已同意',
  REJECTED: '卖家已拒绝',
  EXPIRED: '已失效',
  ORDERED: '已成交'
}

export interface ChatSendReq {
  itemId: string
  toUserId: string
  /** 文本留言 1–500 字；图片消息与面交卡片本期不做 */
  content: string
}

export const chatApi = {
  send: (req: ChatSendReq) =>
    request<string>({ url: '/api/chat/send', method: 'POST', data: req }),

  conversations: () =>
    request<ChatConversationVO[]>({ url: '/api/chat/conversations' }),

  /** 倒序分页；beforeId 传列表最后一条的 id 向上翻历史 */
  messages: (itemId: string, withUserId: string, beforeId?: string, size?: number) =>
    request<ChatMessageVO[]>({
      url: '/api/chat/messages',
      params: { itemId, withUserId, beforeId, size }
    }),

  /** 标已读：进会话时调 */
  read: (itemId: string, withUserId: string) =>
    request<void>({ url: '/api/chat/read', method: 'POST', params: { itemId, withUserId } })
}
