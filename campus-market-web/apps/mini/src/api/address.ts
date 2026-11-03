/**
 * 用户地址簿（`user_address`，收货 SHIP / 自提 PICKUP 两类同表）
 *
 * <p>两类地址靠 `kind` 隔离：买家只维护收货地址、卖家只维护自提地址，同一接口下互不可见。
 * 服务端不提供「按 id 查一条」的口子——地址簿容量很小，列表一次取全即可，
 * 编辑页也从列表页带参数进来，不单独拉详情。
 *
 * <p>地址被订单引用后会在下单时快照进订单，所以改 / 删地址不影响历史订单。
 */

import { request } from '@/utils/request'

/** 1 收货地址（买家维护） 2 自提地址（卖家维护），与后端 AddressKindEnum 对齐 */
export const ADDRESS_KIND = {
  ship: 1,
  pickup: 2
} as const

export type AddressKind = (typeof ADDRESS_KIND)[keyof typeof ADDRESS_KIND]

export interface AddressVO {
  id: string
  kind: number
  contactName: string
  contactPhone: string
  province?: string
  city?: string
  district?: string
  detail: string
  /** 1 默认；同类地址里唯一 */
  isDefault?: number
}

export interface AddressReq {
  kind: number
  contactName: string
  contactPhone: string
  province?: string
  city?: string
  district?: string
  detail: string
  isDefault?: number
}

export const addressApi = {
  list: (kind: number) => request<AddressVO[]>({ url: `/api/address/list?kind=${kind}` }),
  create: (req: AddressReq) => request<string>({ url: '/api/address', method: 'POST', data: { ...req } }),
  update: (id: string, req: AddressReq) =>
    request<void>({ url: `/api/address/${id}`, method: 'PUT', data: { ...req } }),
  remove: (id: string) => request<void>({ url: `/api/address/${id}`, method: 'DELETE' }),
  setDefault: (id: string) => request<void>({ url: `/api/address/${id}/default`, method: 'POST' })
}

/** 地址摘要（省市区 + 详细），列表、picker、下单页共用一份拼法 */
export function addressText(a: Pick<AddressVO, 'province' | 'city' | 'district' | 'detail'>): string {
  return [a.province, a.city, a.district, a.detail].filter(Boolean).join('')
}
