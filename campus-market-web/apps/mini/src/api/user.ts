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
  /** 一级分类后端置 null（Jackson non_null 后键消失）；二级为其父 id */
  parentId?: string
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

/** 在售列表查询条件（后端只支持这三个服务端条件 + 分页；价格/成色排序在后端参数化之前不做） */
export interface ItemListParams {
  keyword?: string
  /** 一级分类 id，命中其下所有二级 */
  categoryId?: string
  campusId?: string
  pageNum?: number
  pageSize?: number
}

export interface ItemPage {
  list: ApiItemVO[]
  total: number
  pageNum: number
  pageSize: number
}

export const userItemApi = {
  publish: (req: ItemPublishReq) =>
    request<string>({ url: '/api/item/publish', method: 'POST', data: { ...req } }),

  edit: (id: string, req: ItemPublishReq) =>
    request<void>({ url: `/api/item/${id}/edit`, method: 'POST', data: { ...req } }),

  /** 在售商品列表（免登录），发布时间倒序 */
  list: (params: ItemListParams = {}) =>
    request<ItemPage>({ url: '/api/item/list', data: { ...params } }),

  mine: (status?: string, pageNum = 1, pageSize = 10) =>
    request<{ list: ApiItemVO[]; total: number }>({
      url: '/api/item/mine',
      data: { status, pageNum, pageSize }
    }),

  detail: (id: string) => request<ApiItemVO>({ url: `/api/item/${id}` }),

  remove: (id: string) => request<void>({ url: `/api/item/${id}/delete`, method: 'POST' }),

  offline: (id: string) => request<void>({ url: `/api/item/${id}/offline`, method: 'POST' })
}

/* ── 订单（状态机见 notes/13 第二节）────────────────────── */

/** 与后端 OrderStatusEnum 枚举名逐字一致 */
export type OrderStatus =
  | 'WAIT_PAY'
  | 'WAIT_MEET'
  | 'VERIFIED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REFUNDING'
  | 'REFUNDED'
  | 'DISPUTING'

export interface OrderVO {
  id: string
  orderNo: string
  itemId: string
  buyerId: string
  sellerId: string
  /** 2 位小数字符串 */
  amount: string
  status: OrderStatus
  meetPointId?: string
  /** 约定面交时间 */
  meetTime?: string
  /** 核销码（付款后生成，面交时出示/扫描） */
  verifyCode?: string
  expireTime?: string
  payTime?: string
  cancelBy?: number
  cancelReason?: string
  itemTitle?: string
  itemPrice?: string
  itemGrade?: string
  itemMainImage?: string
  createTime: string
}

export const userOrderApi = {
  /** 下单：只插待付款订单，商品不锁定；金额一律按标价 */
  create: (req: { itemId: string; meetPointId?: string; meetTime?: string }) =>
    request<string>({ url: '/api/order/create', method: 'POST', data: { ...req } }),

  /** 模拟支付 → 待面交 */
  pay: (id: string) => request<void>({ url: `/api/order/${id}/pay`, method: 'POST' }),

  /** 待付款直接作废；已付款的取消 = 发起退款申请 */
  cancel: (id: string, reason?: string) =>
    request<void>({ url: `/api/order/${id}/cancel`, method: 'POST', data: reason ? { reason } : {} }),

  /** 卖家处理退款：agree=true 已退款；false 拒绝 → 纠纷中 */
  refund: (id: string, agree: boolean, note?: string) =>
    request<void>({ url: `/api/order/${id}/refund`, method: 'POST', data: { agree, note } }),

  /** 发起纠纷（待面交/已验货/退款中可发起） */
  dispute: (id: string, reason: string) =>
    request<void>({ url: `/api/order/${id}/dispute`, method: 'POST', data: { reason } }),

  /** 扫码核销：待面交 → 已验货 */
  verify: (id: string, code: string) =>
    request<void>({ url: `/api/order/${id}/verify`, method: 'POST', data: { code } }),

  /** 确认收货：已验货 → 已完成 */
  receive: (id: string) => request<void>({ url: `/api/order/${id}/receive`, method: 'POST' }),

  /** role：buyer 我买到的（默认）/ seller 我卖出的 */
  list: (role: 'buyer' | 'seller' = 'buyer', pageNum = 1, pageSize = 10) =>
    request<{ list: OrderVO[]; total: number; pageNum: number; pageSize: number }>({
      url: '/api/order/list',
      data: { role, pageNum, pageSize }
    }),

  detail: (id: string) => request<OrderVO>({ url: `/api/order/${id}` })
}
