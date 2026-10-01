/**
 * A6 订单管理的 mock 数据（business/03 A-B7 / F-A6）
 *
 * <p>**必须看商品快照而非当前商品**（F-A6 判据）：卖家可能事后改价改描述，
 * 所以这里 `snapshot` 与 `ITEMS` 里当前值**故意不一致**——
 * o3002 的显示器下单时 899 元，现在商品已降到 640 元。
 * 页面必须把「快照」两个字标出来，否则纠纷取证时会拿错价格。
 */

import type { ItemSnapshot, TradeOrder } from '@campus/shared'
import { ITEMS } from './items'

const isoAgo = (days: number, hours = 0): string => {
  const d = new Date('2026-09-30T10:00:00+08:00')
  d.setDate(d.getDate() - days)
  d.setHours(d.getHours() - hours)
  return d.toISOString()
}

interface Seed {
  id: string
  orderNo: string
  itemId: string
  /** 快照与当前商品不一致时的覆盖值 */
  override?: Partial<ItemSnapshot>
  buyerId: string
  buyerNick: string
  status: TradeOrder['status']
  meetPoint: string
  /** 约定面交：距今 N 小时后（负数=过去） */
  meetInHours: number
  createdDaysAgo: number
  dispute?: NonNullable<TradeOrder['dispute']> & { daysAgo: number }
}

const SEEDS: Seed[] = [
  { id: 'o3001', orderNo: 'CM20260928001', itemId: 'i2001', buyerId: 'u1002', buyerNick: '小满', status: 'DONE', meetPoint: '中心校区·图书馆东门', meetInHours: -30, createdDaysAgo: 3 },
  { id: 'o3002', orderNo: 'CM20260927002', itemId: 'i2014', override: { price: 899, description: '带原装箱子和电源线，原价出' }, buyerId: 'u1004', buyerNick: '果冻', status: 'MEETING', meetPoint: '中心校区·一食堂门口', meetInHours: 6, createdDaysAgo: 2 },
  { id: 'o3003', orderNo: 'CM20260929003', itemId: 'i2005', buyerId: 'u1011', buyerNick: '拾柒', status: 'DISPUTE', meetPoint: '东校区·教学楼中庭', meetInHours: -12, createdDaysAgo: 1, dispute: { reason: '当面验货发现车架有裂纹，与描述不符，要求退款', by: 'buyer', at: isoAgo(0, 10), daysAgo: 0 } },
  { id: 'o3004', orderNo: 'CM20260930004', itemId: 'i2003', buyerId: 'u1015', buyerNick: '糖糖', status: 'PAID', meetPoint: '中心校区·图书馆东门', meetInHours: 26, createdDaysAgo: 0 },
  { id: 'o3005', orderNo: 'CM20260930005', itemId: 'i2022', buyerId: 'u1014', buyerNick: '石头', status: 'UNPAID', meetPoint: '南苑宿舍区·快递驿站旁', meetInHours: 30, createdDaysAgo: 0 },
  { id: 'o3006', orderNo: 'CM20260926006', itemId: 'i2006', buyerId: 'u1013', buyerNick: '阿蓝', status: 'DONE', meetPoint: '南苑宿舍区·快递驿站旁', meetInHours: -52, createdDaysAgo: 4 },
  { id: 'o3007', orderNo: 'CM20260925007', itemId: 'i2019', buyerId: 'u1002', buyerNick: '小满', status: 'CANCELED', meetPoint: '中心校区·体育馆南侧', meetInHours: -80, createdDaysAgo: 5 },
  { id: 'o3008', orderNo: 'CM20260924008', itemId: 'i2016', buyerId: 'u1001', buyerNick: '阿泽', status: 'REFUNDED', meetPoint: '东校区·三舍架空层', meetInHours: -100, createdDaysAgo: 6, dispute: { reason: '当面拒收，风扇噪音过大', by: 'buyer', at: isoAgo(5), daysAgo: 5 } },
  { id: 'o3009', orderNo: 'CM20260923009', itemId: 'i2015', buyerId: 'u1011', buyerNick: '拾柒', status: 'DONE', meetPoint: '中心校区·图书馆东门', meetInHours: -140, createdDaysAgo: 7 },
  { id: 'o3010', orderNo: 'CM20260929010', itemId: 'i2020', buyerId: 'u1008', buyerNick: '青禾', status: 'DISPUTE', meetPoint: '南苑宿舍区·快递驿站旁', meetInHours: -4, createdDaysAgo: 1, dispute: { reason: '买家未按约定时间取货且拒绝沟通，要求放款', by: 'seller', at: isoAgo(0, 6), daysAgo: 0 } },
  { id: 'o3011', orderNo: 'CM20260930011', itemId: 'i2004', buyerId: 'u1004', buyerNick: '果冻', status: 'UNPAID', meetPoint: '南苑宿舍区·快递驿站旁', meetInHours: 20, createdDaysAgo: 0 },
  { id: 'o3012', orderNo: 'CM20260927012', itemId: 'i2023', buyerId: 'u1015', buyerNick: '糖糖', status: 'DONE', meetPoint: '中心校区·一食堂门口', meetInHours: -46, createdDaysAgo: 3 },
  { id: 'o3013', orderNo: 'CM20260928013', itemId: 'i2013', buyerId: 'u1002', buyerNick: '小满', status: 'REFUNDING', meetPoint: '中心校区·体育馆南侧', meetInHours: -20, createdDaysAgo: 2, dispute: { reason: '鞋码与描述不符（标 42.5 实为 41）', by: 'buyer', at: isoAgo(1), daysAgo: 1 } },
  { id: 'o3014', orderNo: 'CM20260930014', itemId: 'i2024', buyerId: 'u1011', buyerNick: '拾柒', status: 'PAID', meetPoint: '东校区·教学楼中庭', meetInHours: 34, createdDaysAgo: 0 },
  { id: 'o3015', orderNo: 'CM20260922015', itemId: 'i2002', buyerId: 'u1008', buyerNick: '青禾', status: 'DONE', meetPoint: '中心校区·一食堂门口', meetInHours: -190, createdDaysAgo: 8 }
]

export const ORDERS: TradeOrder[] = SEEDS.map((s) => {
  const item = ITEMS.find((i) => i.id === s.itemId)
  if (!item) throw new Error(`mock 数据不一致：订单 ${s.id} 引用了不存在的商品 ${s.itemId}`)

  const meetAt = new Date('2026-09-30T10:00:00+08:00')
  meetAt.setHours(meetAt.getHours() + s.meetInHours)

  return {
    id: s.id,
    orderNo: s.orderNo,
    itemId: s.itemId,
    snapshot: {
      title: item.title,
      price: item.price,
      condition: item.condition,
      description: item.description,
      tints: item.tints,
      ...s.override
    },
    buyerId: s.buyerId,
    buyerNick: s.buyerNick,
    sellerId: item.sellerId,
    sellerNick: item.sellerNick,
    status: s.status,
    meetPoint: s.meetPoint,
    meetAt: meetAt.toISOString(),
    createdAt: isoAgo(s.createdDaysAgo),
    dispute: s.dispute ? { reason: s.dispute.reason, by: s.dispute.by, at: s.dispute.at } : undefined
  }
})

/** 快照与当前商品是否不一致：详情页要给出提示，这是纠纷取证的关键 */
export const snapshotDrifted = (order: TradeOrder): boolean => {
  const item = ITEMS.find((i) => i.id === order.itemId)
  if (!item) return false
  return (
    item.price !== order.snapshot.price ||
    item.condition !== order.snapshot.condition ||
    item.description !== order.snapshot.description
  )
}
