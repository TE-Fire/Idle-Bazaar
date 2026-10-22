/**
 * 询单留言（/api/chat，同步模式——无推送，前端进会话轮询 + 拉取即已读）
 *
 * <p>会话按「商品 + 双方」定位：列表项带 itemId + peerUserId，进详情/发消息
 * 都用这两个 id，不传 sessionId（服务端 upsert，前端拿不到也不需要）。
 */

import { request } from './http'

export interface ChatConversationVO {
  itemId: string
  itemTitle?: string | null
  itemMainImage?: string | null
  peerUserId: string
  peerNick?: string | null
  lastContent?: string | null
  lastAt?: string | null
  unread: number
}

export interface ChatMessageVO {
  id: string
  senderId: string
  content: string
  createdAt: string
  /** 服务端按发送人与当前登录用户比较给出，前端不自己判断左右 */
  mine: boolean
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
