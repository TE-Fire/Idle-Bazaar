/**
 * 管理端订单（对应后端 AdminOrderController，business/03 A-B7 / F-A6）
 *
 * <p>**本模块的核心是「快照 vs 当前商品」**：纠纷裁决要看下单那一刻的商品是什么样，
 * 而卖家随时可能改价改描述。所以 `itemPrice` / `itemGrade` 是快照值，
 * `currentPrice` / `currentGrade` 是现在的值，漂移标记由后端算好下发——前端不再自己比对，
 * 规则只留在后端一处。
 *
 * <p>几条与后端的约定：
 *   ① `status` 传**枚举名**（`WAIT_PAY` 等），后端用 `@JsonValue` 输出同形，前端不必维护 1–8 映射；
 *   ② 金额是 **2 位小数字符串**（`"899.00"`），展示直接用，比较前显式 `Number()`；
 *   ③ 时间是 `yyyy-MM-dd HH:mm:ss`（后端钉死格式），**不要再 slice/replace**；
 *   ④ 可空字段可能整个不出现（后端 Jackson 配了 `non_null`），判空一律用 `!x`。
 *
 * <p>裁决只有两种结果（不做部分退款）：见 `ARBITRATE_ACTIONS`。
 */

import { request, type PageResult } from './http'
import type { MoneyString } from './item'

/** 与后端 `OrderStatusEnum` 枚举名逐字一致 */
export type AdminOrderStatus =
  | 'WAIT_PAY'
  | 'WAIT_MEET'
  | 'VERIFIED'
  | 'COMPLETED'
  | 'CANCELED'
  | 'REFUNDING'
  | 'REFUNDED'
  | 'DISPUTING'

export interface AdminOrderVO {
  id: string
  orderNo: string
  itemId: string

  /* ── 下单时的商品快照（订单表冗余列，不随卖家改动） ── */
  itemTitle?: string
  itemPrice?: MoneyString
  itemGrade?: string
  itemMainImage?: string | null
  /** 快照描述：只有详情接口解析 `snapshot` JSON 才有 */
  itemDescription?: string

  /* ── 当前商品（卖家可能已改过）；商品被删除时整组缺失 ── */
  currentPrice?: MoneyString
  currentGrade?: string
  currentDescription?: string
  /** 价格 / 成色 / 描述任一与快照不同；商品已不存在也算漂移 */
  snapshotDrifted: boolean

  amount: MoneyString
  status: AdminOrderStatus

  buyerId: string
  buyerNick?: string | null
  sellerId: string
  sellerNick?: string | null

  meetPointId?: string | null
  meetPointName?: string | null
  /** 约定面交时间 */
  meetTime?: string | null
  payTime?: string | null
  /** 待付款超时时间 */
  expireTime?: string | null
  createTime: string

  /** 1 买家 2 卖家 3 系统；管理端强制退款不单独设值 */
  cancelBy?: number | null
  cancelReason?: string | null

  /* ── 纠纷（该订单最新一条 biz_type=3 的申诉单） ── */
  appealId?: string | null
  disputeReason?: string | null
  /** 1 买家发起 2 卖家发起 */
  disputeBy?: number | null
  disputeAt?: string | null
  disputeEvidence?: string | null
  /** 已处理时的结论备注 */
  disputeNote?: string | null
}

export interface AdminOrderQuery {
  keyword?: string
  status?: AdminOrderStatus
  /** 只看纠纷（等价于 status=DISPUTING，但它是管理端最常用的入口，单独一个开关） */
  disputeOnly?: boolean
  pageNum?: number
  pageSize?: number
}

export interface AdminOrderRefundReq {
  /** 强制退款原因，落订单的 cancel_reason，买卖双方可见 */
  note?: string
}

export interface OrderArbitrateReq {
  /** REFUND_BUYER 退款买家 / PAY_SELLER 放款卖家 */
  result: 'REFUND_BUYER' | 'PAY_SELLER'
  note?: string
}

export const adminOrderApi = {
  /** 分页列表。空值不传，后端按「不筛选」处理 */
  list: (params: AdminOrderQuery = {}) => {
    const query: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') {
        query[k] = v
      }
    }
    return request<PageResult<AdminOrderVO>>({ url: '/admin/order/list', params: query })
  },

  detail: (id: string) => request<AdminOrderVO>({ url: `/admin/order/${id}` }),

  /**
   * 强制退款。仅「退款中 / 纠纷中」，其余状态后端返回 2003。
   * 后果与卖家同意退款一致：订单转已退款、商品恢复在售并重置占位标记。
   */
  refund: (id: string, req: AdminOrderRefundReq) =>
    request<void>({ url: `/admin/order/${id}/refund`, method: 'POST', data: req }),

  /**
   * 纠纷裁决。仅「纠纷中」，且该订单必须挂着待复审的申诉单。
   * 后端内部走申诉裁决链路：退款买家 → 已退款，放款卖家 → 已完成。
   */
  arbitrate: (id: string, req: OrderArbitrateReq) =>
    request<void>({ url: `/admin/order/${id}/arbitrate`, method: 'POST', data: req })
}
