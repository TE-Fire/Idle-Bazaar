/**
 * 学生端订单（对应后端 `OrderController`，状态机见 notes/13 第二节）
 *
 * <p>与管理端 `order.ts` 是两套东西：那份是运营视角（列表 / 详情 / 强制退款 / 裁决），
 * 这份是买卖双方视角（下单、付款、退款、纠纷、核销、收货）。**不要合并**——
 * 权限点、动作集合、可见字段都不同，管理端还要看 snapshot。
 *
 * <p>三条与后端约定的形状：
 *   ① `status` 一律传**枚举名**，且是 `CANCELED`（单 L，与后端 `OrderStatusEnum` 逐字一致）。
 *      写成 `CANCELLED` 不会报错，只会永远匹配不上，已取消订单静默显示成空。
 *   ② `verify` 的核销码走 **query 参数**（后端 `@RequestParam String code`），不是 body。
 *   ③ `amount` / `itemPrice` 是 2 位小数**字符串**，参与运算要显式 `Number()`。
 *
 * <p>可空字段标可选：后端 Jackson 配了 `non_null`，null 不出现在 JSON 里，
 * 前端拿到的是 `undefined`（判空用 `!x`，见 tech/02 §6.1）。
 */

import { request, type PageResult } from './http'

/** 与后端 `OrderStatusEnum` 枚举名逐字一致（注意 CANCELED 单 L） */
export type UserOrderStatus =
  | 'WAIT_PAY'
  | 'WAIT_MEET'
  | 'VERIFIED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'REFUNDING'
  | 'REFUNDED'
  | 'DISPUTING'
  /** 送货上门：待卖家发货 */
  | 'WAIT_SHIP'
  /** 送货上门：卖家已发货，待买家确认收货 */
  | 'SHIPPED'
  /** 自提：待买家到提货地址自取 */
  | 'WAIT_PICKUP'

/** 与后端 `DeliveryTypeEnum` 枚举名逐字一致 */
export type DeliveryType = 'MEET' | 'PICKUP' | 'DELIVERY'

/** 买卖双方查同一个用户的两个方向，后端按 buyer_id / seller_id 走两个索引 */
export type OrderRole = 'buyer' | 'seller'

export interface UserOrderVO {
  id: string
  orderNo: string
  itemId: string
  buyerId: string
  sellerId: string
  /** 买家昵称（详情页展示交易对方用，可能为空） */
  buyerNick?: string
  /** 买家头像 URL，空时前端按 buyerId 派生 */
  buyerAvatar?: string
  /** 卖家昵称 */
  sellerNick?: string
  /** 卖家头像 URL */
  sellerAvatar?: string
  /** 2 位小数字符串 */
  amount: string
  status: UserOrderStatus
  /** 收货方式：面交/自提/送货上门，决定履约动作与地址交互 */
  deliveryType?: DeliveryType
  /** 地址 id：送货上门=买家收货地址；自提=卖家提货地址；面交为空 */
  addressId?: string
  /** 地址快照·联系人 */
  addressContact?: string
  /** 地址快照·联系电话 */
  addressPhone?: string
  /** 地址快照·完整地址（省+市+区+详细） */
  addressDetail?: string
  meetPointId?: string
  /** 面交点名称，如「一食堂门口」 */
  meetPointName?: string
  /** 面交点所属校区名，如「主校区」 */
  meetPointCampus?: string
  /** 约定面交时间 */
  meetTime?: string
  /** 核销码：一单一码，买卖任一方出示或输入即成立 */
  verifyCode?: string
  verifyTime?: string
  /** 待付款超时时间，由定时任务扫掉 */
  expireTime?: string
  payTime?: string
  /** 取消方 1 买家 2 卖家 3 系统超时 */
  cancelBy?: number
  cancelReason?: string
  /** 以下四项是下单时的快照，不随卖家事后改商品而变化 */
  itemTitle?: string
  itemPrice?: string
  itemGrade?: string
  itemMainImage?: string
  createTime: string
}

export interface OrderCreateReq {
  itemId: string
  /** 议价单 id：从聊天议价卡片点「去付款」时传入，金额按议价价；不传则按商品标价 */
  offerId?: string
  meetPointId?: string
  /** 送货上门时买家选定的收货地址 id（其余方式不传） */
  addressId?: string
  meetTime?: string
}

export interface OrderListParams {
  role?: OrderRole
  status?: UserOrderStatus
  pageNum?: number
  pageSize?: number
}

export const userOrderApi = {
  /** 下单：只插待付款订单，商品不锁定；金额按标价，传 offerId 则按已同意的议价价 */
  create: (req: OrderCreateReq) =>
    request<string>({ url: '/api/order/create', method: 'POST', data: { ...req } }),

  /** 模拟支付 → 待面交，并批量取消同商品其余待付款订单 */
  pay: (id: string) => request<void>({ url: `/api/order/${id}/pay`, method: 'POST' }),

  /** 待付款直接作废；已付款的取消 = 发起退款申请，落退款中等卖家处理 */
  cancel: (id: string, reason?: string) =>
    request<void>({ url: `/api/order/${id}/cancel`, method: 'POST', data: reason ? { reason } : {} }),

  /** 卖家处理退款：agree=true 已退款（商品恢复在售）；false 拒绝 → 纠纷中 */
  refund: (id: string, agree: boolean, note?: string) =>
    request<void>({ url: `/api/order/${id}/refund`, method: 'POST', data: { agree, note } }),

  /** 发起纠纷：待面交 / 已验货 / 退款中可发起，同事务写申诉单进审核队列 */
  dispute: (id: string, reason: string) =>
    request<void>({ url: `/api/order/${id}/dispute`, method: 'POST', data: { reason } }),

  /** 扫码核销：待面交 → 已验货。核销码是 query 参数，不是 body */
  verify: (id: string, code: string) =>
    request<void>({ url: `/api/order/${id}/verify`, method: 'POST', params: { code } }),

  /** 卖家发货（仅送货上门）：待发货 → 已发货 */
  ship: (id: string) => request<void>({ url: `/api/order/${id}/ship`, method: 'POST' }),

  /** 确认收货：已验货/已发货/待自提 → 已完成；面交必须先核销不能跳过 */
  receive: (id: string) => request<void>({ url: `/api/order/${id}/receive`, method: 'POST' }),

  /**
   * role：buyer 我买到的（默认）/ seller 我卖出的。
   *
   * <p>查询参数必须走 `params`：这是 axios，GET 请求的 `data` 会被**静默丢弃**，
   * 写成 data 的后果是 role / status 全不生效，永远查回买家第一页。
   * （小程序端没这个坑，uni.request 的 data 会转成 query，两边不能照抄。）
   */
  list: (params: OrderListParams = {}) =>
    request<PageResult<UserOrderVO>>({ url: '/api/order/list', params: { ...params } }),

  detail: (id: string) => request<UserOrderVO>({ url: `/api/order/${id}` }),

  /**
   * 查当前用户对该商品是否有未完成订单：有则返回 { orderId, status }，否则 null。
   * GET 入参走 params（axios 的 GET data 会被静默丢弃）。
   */
  myOpen: (itemId: string) =>
    request<{ orderId: string; status: string } | null>({
      url: '/api/order/my-open',
      params: { itemId }
    })
}
