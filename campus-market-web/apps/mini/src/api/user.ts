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
  grade: string
  campusId: string
  campusName?: string
  meetPointId?: string
  meetPointName?: string
  description?: string
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
