/**
 * ApiItemVO → GoodsItem 映射（mini 端唯一入口，与 web 端 utils/item-map.ts 同口径）
 *
 * <p>存在理由：卡片 / 分类页 / 发布预览消费 shared 的 GoodsItem 契约，接口返回 ApiItemVO，
 * 字段名与粒度都不同，需要唯一转换点。
 *
 * <p>三处字段缺口：① wantCount 学生端不下发，恒 0（卡片隐藏「N 人想要」）；
 * ② verified 取 sellerAuthStatus === 2；③ category/campus 是字典中文名，原样透传 string。
 */

import type { ApiItemVO } from '@/api/user'
import type { ConditionGrade, GoodsItem } from '@campus/shared'

/** 占位渐变两色：由 id 哈希取色，刷新不跳色（与 web 端同口径） */
export function tintsOf(id: string): [string, string] {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  const hue = h % 360
  return [`hsl(${hue}, 70%, 78%)`, `hsl(${(hue + 40) % 360}, 65%, 66%)`]
}

export function toGoodsItem(vo: ApiItemVO): GoodsItem {
  return {
    id: String(vo.id),
    title: vo.title,
    categoryId: vo.categoryId ?? '',
    category: vo.categoryName ?? '',
    price: Number(vo.price),
    originalPrice: vo.originPrice != null ? Number(vo.originPrice) : undefined,
    condition: vo.grade as ConditionGrade,
    campus: vo.campusName ?? '',
    meetPoint: vo.meetPointName ?? '',
    negotiable: vo.negotiable === 1,
    // 与 web 端同口径：delivery 表示「送货上门」，不再有包邮标记
    delivery: vo.deliveryType === 3,
    deliveryType: vo.deliveryType ?? 1,
    status: vo.status,
    wantCount: 0,
    seller: {
      id: String(vo.sellerId),
      nick: vo.sellerNick ?? '校园用户',
      verified: (vo as { sellerAuthStatus?: number }).sellerAuthStatus === 2,
      credit: (vo.sellerCredit ?? 100) >= 105 ? '极好' : (vo.sellerCredit ?? 100) >= 90 ? '良好' : '一般',
      tints: tintsOf(String(vo.sellerId))
    },
    publishedAt: vo.publishAt,
    description: vo.description ?? '',
    images: vo.images?.map((im) => im.url) ?? (vo.mainImage ? [vo.mainImage] : []),
    tints: tintsOf(String(vo.id))
  }
}
