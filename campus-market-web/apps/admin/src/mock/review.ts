/**
 * A5 审核队列的 mock 数据（business/03 F-A2 / F-A3 / F-A4）
 *
 * <p>**只有看板在用**：`DashboardView` 的「待处置队列」角标要按来源计数、按等待时长算积压，
 * 而看板整页还是 mock（A8 未落地），所以这里继续供一份夹具。
 * 审核队列页（`ReviewView.vue`）已经接 `/admin/review/queue`，不再读这个文件。
 *
 * <p>夹具的类型就是接口的形状（`ReviewTaskVO`），**不再另定一套 mock 类型**——
 * 两套形状意味着看板与队列页对「一条待审核任务」的理解可能不同，
 * 等看板接接口时才会发现字段对不上（notes/09 §8 的「别留两套」）。
 *
 * <p>排序**不在这里**：队列顺序由后端给（类型优先 + 组内权重降序 + 实时等待补偿），
 * 前端再实现一遍排序就等于把最容易漂移的口径写了两处。
 */

import { calcWeight } from '@campus/shared'
import type { QueueKind, ReviewTaskVO } from '@campus/api'
import { ITEMS } from './items'
import { categoryName } from './dict'
import { hoursBetween } from './now'

/** 举报原因（预设，与驳回原因同源，便于统计归类） */
export const REPORT_REASONS = [
  '图片不实（网图或与实物不符）',
  '涉违禁品',
  '校外引流（含联系方式/二维码）',
  '价格异常（疑似诈骗）',
  '成色虚标',
  '其他'
] as const

/** 申诉理由（卖家提交） */
export const APPEAL_REASONS = [
  '商品为本人正品，可提供购买凭证',
  '已删除联系方式与二维码，请求复审',
  '成色描述已按实物修改',
  '价格系误填，已更正'
] as const

interface Seed {
  id: string
  kind: QueueKind
  itemId: string
  submitAt: string
  reason?: string
  /** 申诉：原处置人、原处置结果（换人复审要用） */
  handledBy?: string
  handledByName?: string
  handledResult?: string
}

const SEEDS: Seed[] = [
  // 商品审核：来自 status=PENDING 的商品
  { id: 'q1', kind: 'item', itemId: 'i2009', submitAt: '2026-09-30T08:00:00+08:00' },
  { id: 'q2', kind: 'item', itemId: 'i2012', submitAt: '2026-09-29T08:00:00+08:00' },
  { id: 'q3', kind: 'item', itemId: 'i2013', submitAt: '2026-09-30T08:00:00+08:00' },
  { id: 'q4', kind: 'item', itemId: 'i2007', submitAt: '2026-09-30T01:00:00+08:00' },
  { id: 'q5', kind: 'item', itemId: 'i2011', submitAt: '2026-09-29T20:00:00+08:00' },
  { id: 'q6', kind: 'item', itemId: 'i2008', submitAt: '2026-09-30T05:00:00+08:00' },
  { id: 'q7', kind: 'item', itemId: 'i2010', submitAt: '2026-09-30T07:00:00+08:00' },

  // 举报处理
  { id: 'q8', kind: 'report', itemId: 'i2017', submitAt: '2026-09-29T06:00:00+08:00', reason: '校外引流（含联系方式/二维码）' },
  { id: 'q9', kind: 'report', itemId: 'i2020', submitAt: '2026-09-30T04:00:00+08:00', reason: '成色虚标' },
  { id: 'q10', kind: 'report', itemId: 'i2024', submitAt: '2026-09-29T22:00:00+08:00', reason: '图片不实（网图或与实物不符）' },
  { id: 'q11', kind: 'report', itemId: 'i2003', submitAt: '2026-09-30T09:00:00+08:00', reason: '价格异常（疑似诈骗）' },

  // 申诉复审：优先级高于普通审核（F-A4）
  {
    id: 'q12',
    kind: 'appeal',
    itemId: 'i2017',
    submitAt: '2026-09-28T10:00:00+08:00',
    reason: '已删除联系方式与二维码，请求复审',
    handledBy: '2102',
    handledByName: '审核员·李',
    handledResult: '已强制下架'
  },
  {
    id: 'q13',
    kind: 'appeal',
    itemId: 'i2018',
    submitAt: '2026-09-29T12:00:00+08:00',
    reason: '商品为本人正品，可提供购买凭证',
    handledBy: '2102',
    handledByName: '审核员·李',
    handledResult: '已驳回'
  }
]

export const QUEUE_TASKS: ReviewTaskVO[] = SEEDS.map((s) => {
  const item = ITEMS.find((i) => i.id === s.itemId)
  if (!item) throw new Error(`mock 数据不一致：队列 ${s.id} 引用了不存在的商品 ${s.itemId}`)

  const waitHours = Math.max(0, Math.round(hoursBetween(s.submitAt)))
  // mock 的权重仍走 shared 的公式（后端那边是同一套口径的实现，两边都以 F-A2 为准）
  const { weight, ...parts } = calcWeight({
    reportCount: item.reportCount,
    riskScore: item.riskScore,
    sellerCredit: item.sellerCredit,
    price: item.price,
    categoryAvgPrice: item.categoryAvgPrice,
    waitHours
  })

  return {
    id: s.id,
    kind: s.kind,
    itemId: item.id,
    itemTitle: item.title,
    categoryName: categoryName(item.categoryId),
    description: item.description,
    // mock 商品没有图片（骨架期用渐变占位），接接口后由后端下发 mainImage
    mainImage: undefined,
    images: [],
    itemPrice: item.price.toFixed(2),
    categoryAvgPrice: item.categoryAvgPrice.toFixed(2),
    sellerId: item.sellerId,
    sellerNick: item.sellerNick,
    sellerCredit: item.sellerCredit,
    reportCount: item.reportCount,
    riskScore: item.riskScore,
    itemStatus: item.status,
    submitAt: s.submitAt,
    waitHours,
    reason: s.reason,
    evidence: undefined,
    handledBy: s.handledBy,
    handledByName: s.handledByName,
    handledResult: s.handledResult,
    parts,
    weight
  }
})
