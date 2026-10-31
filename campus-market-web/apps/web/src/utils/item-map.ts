/**
 * ApiItemVO → GoodsItem 映射（web 端唯一入口）
 *
 * <p>存在理由：卡片 / 对比 / 发布预览消费的是 shared 包的 GoodsItem 契约，
 * 而学生端接口返回的是 ApiItemVO——两者字段名与粒度都不同，需要一个唯一转换点。
 * HomeView、CompareView（分享链接按 ids 回显）共用，禁止各处各写一份。
 *
 * <p>三处字段缺口在此消化：
 *   ① wantCount：学生端 VO 不下发收藏数，恒 0——卡片据此隐藏「N 人想要」而不是显示 0；
 *   ② verified：取 sellerAuthStatus === 2（已认证）；
 *   ③ category / campus 是字典中文名，原样透传为 string，不做枚举校验——
 *      字典项是运行期数据，前端写死枚举必然与库里对不上。
 */

import type { ApiItemVO } from '@campus/api'
import type { ConditionGrade, GoodsItem } from '@campus/shared'

/** 占位渐变两色：由雪花 id 哈希取色，同一商品每次刷新一致（截图复现，同 mock 的 LCG 思路） */
export function tintsOf(id: string): [string, string] {
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0
  const hue = h % 360
  return [`hsl(${hue}, 70%, 72%)`, `hsl(${(hue + 40) % 360}, 65%, 60%)`]
}

/** 信用分 → 文案三档。口径：≥105 极好、≥90 良好、其余一般（与种子数据 92–112 的分布匹配） */
export function creditLabel(credit?: number | null): string {
  if (credit == null) return '一般'
  if (credit >= 105) return '极好'
  if (credit >= 90) return '良好'
  return '一般'
}

export function toGoodsItem(vo: ApiItemVO): GoodsItem {
  return {
    id: String(vo.id),
    title: vo.title,
    categoryId: vo.categoryId,
    category: vo.categoryName ?? '',
    price: Number(vo.price),
    originalPrice: vo.originPrice != null ? Number(vo.originPrice) : undefined,
    condition: vo.grade as ConditionGrade,
    campus: vo.campusName ?? '',
    meetPoint: vo.meetPointName ?? '',
    negotiable: vo.negotiable === 1,
    delivery: vo.deliveryIncluded === 1,
    status: vo.status,
    wantCount: 0,
    seller: {
      id: String(vo.sellerId),
      nick: vo.sellerNick ?? '校园用户',
      verified: vo.sellerAuthStatus === 2,
      credit: creditLabel(vo.sellerCredit),
      tints: tintsOf(String(vo.sellerId))
    },
    publishedAt: vo.publishAt,
    description: vo.description ?? '',
    images: vo.images?.map((im) => im.url) ?? (vo.mainImage ? [vo.mainImage] : []),
    tints: tintsOf(String(vo.id))
  }
}
