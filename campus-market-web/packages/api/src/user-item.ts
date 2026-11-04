/**
 * 学生端商品（/api/item）：发布（含草稿）、编辑、我的商品、删除、下架
 *
 * <p>与管理端 `item.ts` 的分工：那边是处置视角（列表 / 详情 / 强制下架 / 删除，
 * 没有编辑——business/03「不做替用户改价改描述」）；这边是卖家视角，
 * 一切操作以 `seller_id = 当前登录用户` 为前提，后端从令牌取用户，不接受客户端传 id。
 *
 * <p>发布与编辑共用 `ItemPublishReq`：
 *   ① `draft=true` 暂存草稿——落草稿(1)、不进审核队列、豁免图片与描述；
 *   ② 已发布商品编辑时改价格或改成色回待审核重新进队，只改描述免审。
 * 价格入参用 number（JSON 数字），后端按 BigDecimal 收；出参一律是字符串。
 */

import { request, type PageResult } from './http'
import type { ItemStatus } from './item'

/** 与后端 `ApiItemVO` 对齐；可空字段为可选（Jackson non_null，null 不出现在 JSON） */
export interface ApiItemVO {
  id: string
  title: string
  mainImage?: string | null
  categoryId: string
  categoryName?: string | null
  /** 2 位小数字符串 */
  price: string
  originPrice?: string | null
  /** 成色 A–D */
  grade: string
  campusId: string
  campusName?: string | null
  meetPointId?: string | null
  meetPointName?: string | null
  sellerId: string
  sellerNick?: string | null
  sellerCredit?: number | null
  /** 卖家认证态（app_user.auth_status：0未认证 1审核中 2已认证 3未通过）；旧后端不返回时为 undefined */
  sellerAuthStatus?: number | null
  /** 卖家头像 URL（app_user.avatar_url）；空时前端回退占位色块 */
  sellerAvatar?: string | null
  /** 1 可议价；议价在聊天里进行，不进订单 */
  negotiable?: number | null
  /** 收货方式：1面交 2自提 3送货上门 */
  deliveryType?: number | null
  /** 自提地址 id（deliveryType=2 时有效） */
  pickupAddressId?: string | null
  description?: string | null
  status: ItemStatus
  /** 发布时间（草稿是创建时间） */
  publishAt: string
  viewCount: number
  /** 仅详情有 */
  images?: Array<{ id: string; url: string; sort: number }> | null
  /** 当前登录人是否已收藏；未登录恒 false。仅详情返回 */
  favorited?: boolean | null
  /** 收藏时间；仅「我的收藏」列表返回 */
  favoritedAt?: string | null
}

/** 发布 / 编辑入参。images 是已上传的 URL 列表（≤9，第一张为主图），上传走独立接口 */
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
  /** 收货方式：1面交 2自提 3送货上门（必填） */
  deliveryType: number
  /** 自提地址 id：deliveryType=2 时必填 */
  pickupAddressId?: string
  /** true = 暂存草稿（不进队、豁免图片与描述）；已发布商品编辑时忽略 */
  draft?: boolean
}

/** 我的商品 / 搜索共用的查询条件 */
export interface MyItemParams {
  keyword?: string
  /** 传一级分类 id 命中其下所有二级 */
  categoryId?: string
  status?: ItemStatus
  campusId?: string
  /** 卖家 id：个人主页按卖家拉在售列表用 */
  sellerId?: string
  pageNum?: number
  pageSize?: number
}

export const userItemApi = {
  /** 发布；draft=true 存草稿。返回新商品 id */
  publish: (req: ItemPublishReq) =>
    request<string>({ url: '/api/item/publish', method: 'POST', data: req }),

  /** 在售商品列表（免登录）。待审核 / 已下架 / 被处置的一律不出 */
  list: (params: MyItemParams = {}) =>
    request<PageResult<ApiItemVO>>({ url: '/api/item/list', params: { ...params } }),

  detail: (id: string) => request<ApiItemVO>({ url: `/api/item/${id}` }),

  /** 编辑。草稿续存传 draft=true；草稿送审传 draft=false（必进队） */
  edit: (id: string, req: ItemPublishReq) =>
    request<void>({ url: `/api/item/${id}/edit`, method: 'POST', data: req }),

  /** 我的商品：全状态（含草稿），按最近更新倒序 */
  mine: (params: MyItemParams = {}) =>
    request<PageResult<ApiItemVO>>({ url: '/api/item/mine', params: { ...params } }),

  /** 删除（软删）。仅草稿 / 已下架 / 审核未过可删；在售 / 已售出返回 1107 */
  remove: (id: string) => request<void>({ url: `/api/item/${id}/delete`, method: 'POST' }),

  /** 卖家主动下架 → 已下架(4)，可重新编辑上架 */
  offline: (id: string) => request<void>({ url: `/api/item/${id}/offline`, method: 'POST' }),

  /** 收藏，幂等：已收藏再调返回成功 */
  favorite: (id: string) => request<void>({ url: `/api/item/${id}/favorite`, method: 'POST' }),

  /** 取消收藏，幂等：未收藏再调返回成功 */
  unfavorite: (id: string) => request<void>({ url: `/api/item/${id}/favorite`, method: 'DELETE' }),

  /** 我的收藏：按收藏时间倒序；已删除与强制下架的商品不返回 */
  favorites: (params: { pageNum?: number; pageSize?: number } = {}) =>
    request<PageResult<ApiItemVO>>({ url: '/api/item/favorites', params: { ...params } })
}
