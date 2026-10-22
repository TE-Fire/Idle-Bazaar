/**
 * 学生端 API（uni 版，与 Web 端 @campus/api 的 user-* 三个文件同契约）
 *
 * <p>类型从简：只声明页面用得到的字段；金额出参是字符串（后端 BigDecimal 定长输出），
 * 入参 price 用 number（JSON 数字，后端按 BigDecimal 收）。
 */

import { request } from '../utils/request'

/* ── 登录 ─────────────────────────────────────────────── */

export interface UserLoginVO {
  userId: string
  nickname?: string
  authStatus?: number
  creditScore?: number
  tokenName?: string
  tokenValue?: string
}

export const userAuthApi = {
  /** 演示期验证码随响应回显（devCode） */
  sendCode: (phone: string) =>
    request<{ resendAfter: number; devCode?: string }>({
      url: '/api/auth/send-code',
      method: 'POST',
      data: { phone }
    }),

  login: (phone: string, code: string) =>
    request<UserLoginVO>({
      url: '/api/auth/login',
      method: 'POST',
      data: { phone, code }
    }),

  logout: () => request<void>({ url: '/api/auth/logout', method: 'POST' })
}

/* ── 字典 ─────────────────────────────────────────────── */

export interface CategoryVO {
  id: string
  name: string
  parentId: string
}

export const userDictApi = {
  all: () =>
    request<{
      categories: CategoryVO[]
      grades: Array<{ code: string; label: string }>
      campuses: Array<{ id: string; name: string }>
      meetPoints: Array<{ id: string; name: string; campusId: string }>
    }>({ url: '/api/dict/all' })
}

/* ── 商品 ─────────────────────────────────────────────── */

export interface ApiItemVO {
  id: string
  title: string
  mainImage?: string
  categoryId: string
  categoryName?: string
  /** 2 位小数字符串 */
  price: string
  originPrice?: string
  /** 成色 A–D */
  grade: string
  campusId: string
  campusName?: string
  meetPointId?: string
  meetPointName?: string
  description?: string
  sellerId: string
  sellerNick?: string
  sellerCredit?: number
  /** 1 可议价；议价在聊天里进行，不进订单 */
  negotiable?: number
  deliveryIncluded?: number
  status: 'DRAFT' | 'PENDING' | 'ON_SALE' | 'SOLD' | 'OFFLINE' | 'REJECTED' | 'FORCED_OFFLINE'
  publishAt: string
  viewCount: number
  images?: Array<{ id: string; url: string; sort: number }>
}

export interface ItemPublishReq {
  title: string
  categoryId: string
  grade: string
  price: number
  originPrice?: number
  campusId: string
  meetPointId?: string
  description?: string
  images?: string[]
  negotiable?: number
  deliveryIncluded?: number
  /** true = 暂存草稿（不进队、豁免图片与描述） */
  draft?: boolean
}

/* ── 询单留言（同步模式：进会话轮询 + 拉取即已读）──────────── */

export interface ChatConversationVO {
  itemId: string
  itemTitle?: string
  itemMainImage?: string
  peerUserId: string
  peerNick?: string
  lastContent?: string
  lastAt?: string
  unread: number
}

export interface ChatMessageVO {
  id: string
  senderId: string
  content: string
  createdAt: string
  /** 服务端比较发送人与当前登录用户给出，前端不自己判断左右 */
  mine: boolean
}

export const userChatApi = {
  send: (itemId: string, toUserId: string, content: string) =>
    request<string>({
      url: '/api/chat/send',
      method: 'POST',
      data: { itemId, toUserId, content }
    }),

  conversations: () => request<ChatConversationVO[]>({ url: '/api/chat/conversations' }),

  /** 倒序分页；beforeId 传列表最后一条的 id 向上翻历史 */
  messages: (itemId: string, withUserId: string, beforeId?: string) =>
    request<ChatMessageVO[]>({
      url: '/api/chat/messages',
      data: { itemId, withUserId, beforeId }
    }),

  /** 标已读：进会话时调 */
  read: (itemId: string, withUserId: string) =>
    request<void>({ url: '/api/chat/read', method: 'POST', data: { itemId, withUserId } })
}

/* ── 商品 ─────────────────────────────────────────────── */

export const userItemApi = {
  publish: (req: ItemPublishReq) =>
    request<string>({ url: '/api/item/publish', method: 'POST', data: { ...req } }),

  edit: (id: string, req: ItemPublishReq) =>
    request<void>({ url: `/api/item/${id}/edit`, method: 'POST', data: { ...req } }),

  mine: (status?: string, pageNum = 1, pageSize = 10) =>
    request<{ list: ApiItemVO[]; total: number }>({
      url: '/api/item/mine',
      data: { status, pageNum, pageSize }
    }),

  detail: (id: string) => request<ApiItemVO>({ url: `/api/item/${id}` }),

  remove: (id: string) => request<void>({ url: `/api/item/${id}/delete`, method: 'POST' }),

  offline: (id: string) => request<void>({ url: `/api/item/${id}/offline`, method: 'POST' })
}
