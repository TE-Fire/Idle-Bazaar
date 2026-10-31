/**
 * 学生端地址簿（对应后端 `UserAddressController`）
 *
 * <p>`kind` 区分两类地址：1 收货（买家维护，送货上门下单时选）/ 2 自提（卖家维护，发布自提商品时选）。
 * 两类按 `userId + kind` 隔离，买家永远看不到卖家的自提地址，反之亦然。
 *
 * <p>地址被订单引用后会在下单那一刻快照进订单，因此这里改 / 删都不影响历史订单。
 */

import { request } from './http'

/** 与后端 `AddressKindEnum` 一致 */
export type AddressKind = 1 | 2

/** 1 收货地址、2 自提地址 */
export const ADDRESS_KIND = { SHIP: 1 as AddressKind, PICKUP: 2 as AddressKind }

export interface UserAddressVO {
  id: string
  userId: string
  kind: AddressKind
  contactName: string
  contactPhone: string
  province?: string | null
  city?: string | null
  district?: string | null
  detail: string
  /** 0/1，同类地址唯一默认 */
  isDefault: number
  createTime?: string
  updateTime?: string
}

export interface UserAddressReq {
  kind: AddressKind
  contactName: string
  contactPhone: string
  province?: string
  city?: string
  district?: string
  detail: string
  isDefault?: number
}

export const userAddressApi = {
  /** 地址列表：按 kind 过滤，默认地址排最前 */
  list: (kind: AddressKind) =>
    request<UserAddressVO[]>({ url: '/api/address/list', params: { kind } }),

  create: (req: UserAddressReq) =>
    request<string>({ url: '/api/address', method: 'POST', data: req }),

  update: (id: string, req: UserAddressReq) =>
    request<void>({ url: `/api/address/${id}`, method: 'PUT', data: req }),

  remove: (id: string) => request<void>({ url: `/api/address/${id}`, method: 'DELETE' }),

  setDefault: (id: string) => request<void>({ url: `/api/address/${id}/default`, method: 'POST' })
}

/** 拼完整地址文本，列表展示与下单预览共用 */
export function fullAddress(a: UserAddressVO): string {
  return [a.province, a.city, a.district, a.detail].filter(Boolean).join(' ')
}
