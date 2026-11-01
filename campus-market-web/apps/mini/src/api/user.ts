/**
 * 学生端 API（uni 版，与 Web 端 @campus/api 的 user-* 三个文件同契约）
 *
 * <p>类型从简：只声明页面用得到的字段；金额出参是字符串（后端 BigDecimal 定长输出），
 * 入参 price 用 number（JSON 数字，后端按 BigDecimal 收）。
 */

import { request } from '../utils/request'

/* ── 登录 / 注册 / 找回密码 ─────────────────────────────── */

export interface UserLoginVO {
  userId: string
  nickname?: string
  avatarUrl?: string
  authStatus?: number
  creditScore?: number
  /** 本人可见的账号信息 */
  phone?: string
  email?: string
  hasPassword?: boolean
  tokenName?: string
  tokenValue?: string
}

/** 验证码下发响应 */
export interface VerifyCodeVO {
  resendAfter: number
  /** 1 短信 2 QQ 邮箱 */
  channel: number
  devCode?: string
}

/** 验证码渠道，与后端 CODE_CHANNEL 同源 */
export const CODE_CHANNEL = {
  sms: 1,
  mail: 2
} as const

/** 验证码场景，与后端 MailCodeSceneEnum 的 code 一一对应 */
export const MAIL_CODE_SCENE = {
  register: 1,
  reset: 2,
  login: 3
} as const

export type MailCodeScene = (typeof MAIL_CODE_SCENE)[keyof typeof MAIL_CODE_SCENE]

/** 登录方式：1 验证码 2 密码 */
export const USER_LOGIN_TYPE = {
  code: 1,
  password: 2
} as const

export type UserLoginType = (typeof USER_LOGIN_TYPE)[keyof typeof USER_LOGIN_TYPE]

export interface UserRegisterReq {
  phone: string
  /** 只接受 QQ 邮箱（@qq.com / @foxmail.com） */
  email: string
  nickname: string
  /** 8–32 位且不含空格 */
  password: string
  mailCode: string
}

export interface UserResetPasswordReq {
  /** 手机号或 QQ 邮箱，决定用哪个渠道的验证码 */
  account: string
  code: string
  password: string
}

/** 资料编辑请求：两个字段都可选，至少传一个；字段不传 = 不修改 */
export interface UserProfileUpdateReq {
  nickname?: string
  avatarUrl?: string
}

export const userAuthApi = {
  /** 发码：account 是手机号（短信）或 QQ 邮箱（邮件）；演示期验证码随响应回显 */
  sendCode: (account: string, scene: MailCodeScene) =>
    request<VerifyCodeVO>({
      url: '/api/auth/send-code',
      method: 'POST',
      data: { account, scene }
    }),

  /** 登录：type=1 验证码登录（account 决定渠道）/ type=2 密码登录 */
  login: (account: string, type: UserLoginType, credential: string) =>
    request<UserLoginVO>({
      url: '/api/auth/login',
      method: 'POST',
      data: { account, type, credential }
    }),

  /** 注册：邮箱已验证，后端直接下发令牌 */
  register: (req: UserRegisterReq) =>
    request<UserLoginVO>({
      url: '/api/auth/register',
      method: 'POST',
      data: { ...req }
    }),

  /** 找回密码：成功后该账号所有会话下线 */
  resetPassword: (req: UserResetPasswordReq) =>
    request<void>({
      url: '/api/auth/reset-password',
      method: 'POST',
      data: { ...req }
    }),

  /** 编辑昵称 / 头像（局部更新）；成功返回最新用户信息，前端据此刷新会话 */
  updateProfile: (req: UserProfileUpdateReq) =>
    request<UserLoginVO>({ url: '/api/auth/profile', method: 'PUT', data: { ...req } }),

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
      colleges: Array<{ id: string; name: string; campusId?: string }>
      meetPoints: Array<{ id: string; name: string; campusId: string }>
    }>({ url: '/api/dict/all' })
}

/* ── 学号认证（notes/20 D5）───────────────────────────── */

export interface UserAuthApplyVO {
  id: string
  userId: string
  realName: string
  studentNo: string
  collegeId: string
  credentialUrl?: string
  /** 1审核中 2通过 3未通过 */
  status: number
  rejectReason?: string
  handleTime?: string
  createTime: string
}

export interface UserAuthApplyReq {
  realName: string
  studentNo: string
  collegeId: string
  credentialUrl?: string
}

export const userAuthApplyApi = {
  apply: (req: UserAuthApplyReq) =>
    request<string>({ url: '/api/user/auth/apply', method: 'POST', data: { ...req } }),

  /** 最近一次申请：无申请返回 null；驳回时带 rejectReason */
  latest: () => request<UserAuthApplyVO | null>({ url: '/api/user/auth/latest' })
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
  /** 1 可议价：买家可在聊天里出价，卖家同意后按议价价成交；不改商品标价 */
  negotiable?: number
  deliveryIncluded?: number
  status: 'DRAFT' | 'PENDING' | 'ON_SALE' | 'SOLD' | 'OFFLINE' | 'REJECTED' | 'FORCED_OFFLINE'
  publishAt: string
  viewCount: number
  images?: Array<{ id: string; url: string; sort: number }>
  /** 当前登录人是否已收藏；未登录恒 false。仅详情返回 */
  favorited?: boolean
  /** 收藏时间；仅「我的收藏」列表返回 */
  favoritedAt?: string
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
  /** 2 位小数字符串；会话窗商品卡片展示，面交谈价参照 */
  price?: string
  /** 与 ApiItemVO.status 同形的枚举名；非 ON_SALE 时卡片置灰 */
  status?: 'DRAFT' | 'PENDING' | 'ON_SALE' | 'SOLD' | 'OFFLINE' | 'REJECTED' | 'FORCED_OFFLINE'
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
  /** 1 文本 / 7 议价卡片；卡片按 extra 渲染成可操作卡片 */
  type?: number
  /** 卡片载荷（JSON 串）：offerId / itemId / originPrice / offerPrice / status / expireTime */
  extra?: string | null
  createdAt: string
  /** 服务端比较发送人与当前登录用户给出，前端不自己判断左右 */
  mine: boolean
}

/** 议价卡片载荷（服务端已按最新状态回填，不是发送时的快照） */
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

/** 议价单（我的议价列表用；聊天卡片读 extra 即可） */
export interface OfferVO {
  id: string
  itemId: string
  itemTitle?: string
  itemMainImage?: string
  itemPrice?: string
  originPrice: string
  offerPrice: string
  status: OfferCardPayload['status']
  expireTime?: string
  peerNick?: string
  rejectReason?: string
  createTime: string
  asSeller: boolean
}

export const userOfferApi = {
  /** 发起议价：出价须低于标价且不低于标价的 10% */
  create: (itemId: string, offerPrice: number) =>
    request<string>({ url: '/api/offer/create', method: 'POST', data: { itemId, offerPrice } }),

  agree: (id: string) => request<void>({ url: `/api/offer/${id}/agree`, method: 'POST' }),

  reject: (id: string, reason?: string) =>
    request<void>({ url: `/api/offer/${id}/reject`, method: 'POST', data: reason ? { reason } : {} }),

  cancel: (id: string) => request<void>({ url: `/api/offer/${id}/cancel`, method: 'POST' }),

  list: (role: 'buyer' | 'seller' = 'buyer', pageNum = 1, pageSize = 10) =>
    request<{ list: OfferVO[]; total: number; pageNum: number; pageSize: number }>({
      url: '/api/offer/list',
      data: { role, pageNum, pageSize }
    }),

  detail: (id: string) => request<OfferVO>({ url: `/api/offer/${id}` })
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
  /** 卖家 id：个人主页按卖家拉在售列表用 */
  sellerId?: string
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

  offline: (id: string) => request<void>({ url: `/api/item/${id}/offline`, method: 'POST' }),

  /** 收藏，幂等 */
  favorite: (id: string) => request<void>({ url: `/api/item/${id}/favorite`, method: 'POST' }),

  /** 取消收藏，幂等 */
  unfavorite: (id: string) => request<void>({ url: `/api/item/${id}/favorite`, method: 'DELETE' }),

  /** 我的收藏：收藏时间倒序；已删除与被强制下架的商品不返回 */
  favorites: (pageNum = 1, pageSize = 10) =>
    request<{ list: ApiItemVO[]; total: number }>({
      url: '/api/item/favorites',
      data: { pageNum, pageSize }
    })
}

/* ── 订单（状态机见 notes/13 第二节）────────────────────── */

/** 与后端 OrderStatusEnum 枚举名逐字一致 */
export type OrderStatus =
  | 'WAIT_PAY'
  | 'WAIT_MEET'
  | 'VERIFIED'
  | 'COMPLETED'
  | 'CANCELED'
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
  /** 下单：只插待付款订单，商品不锁定；金额按标价，传 offerId 则按已同意的议价价 */
  create: (req: { itemId: string; offerId?: string; meetPointId?: string; meetTime?: string }) =>
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
