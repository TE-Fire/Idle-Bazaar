/**
 * A5 审核队列的 mock 数据（business/03 F-A2 / F-A3 / F-A4）
 *
 * <p>三种来源（商品审核 / 举报处理 / 申诉复审）统一压成 `QueueTask` 一种形状，
 * 才能共用同一套「权重排序 + Drawer 连续审核」。这是 A5 能做成单页的前提。
 *
 * <p>权重不是写死的，**由 `calcWeight` 从原始字段算出**（见 @campus/shared）：
 * 举报×3 + 风险分归一化 + 信用扣减 + 价格异常 + 等待补偿。
 * 好处是页面上能逐项解释「它为什么排第一」，论文里也能直接引用公式。
 *
 * <p>队列**必须按权重排序，不能按时间**——按时间等于让风险最高的单子排在后面（F-A2 判据）。
 */

import { calcWeight, type QueueKind, type QueueTask } from '@campus/shared'
import { ITEMS } from './items'
import { hoursBetween, minutesBetween } from './now'

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
  /** 申诉：原处置人与原处置结果（换人复审要用） */
  handledBy?: string
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
  { id: 'q12', kind: 'appeal', itemId: 'i2017', submitAt: '2026-09-28T10:00:00+08:00', reason: '已删除联系方式与二维码，请求复审', handledBy: '审核员·李', handledResult: '强制下架 + 扣分 15' },
  { id: 'q13', kind: 'appeal', itemId: 'i2018', submitAt: '2026-09-29T12:00:00+08:00', reason: '商品为本人正品，可提供购买凭证', handledBy: '审核员·李', handledResult: '驳回（图片不实）' }
]

export const QUEUE_TASKS: QueueTask[] = SEEDS.map((s) => {
  const item = ITEMS.find((i) => i.id === s.itemId)
  if (!item) throw new Error(`mock 数据不一致：队列 ${s.id} 引用了不存在的商品 ${s.itemId}`)

  const waitHours = Math.max(0, hoursBetween(s.submitAt))
  // 权重与它的四个组成部分一起返回，这里拆开存：QueueTask.parts 只放组成（用于解释排序原因），
  // 总分单独放 weight（用于排序）。不要把 calcWeight 的返回值直接断言成 WeightParts，会丢掉 weight。
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
    itemTints: item.tints,
    itemPrice: item.price,
    categoryAvgPrice: item.categoryAvgPrice,
    sellerId: item.sellerId,
    sellerNick: item.sellerNick,
    sellerCredit: item.sellerCredit,
    reportCount: item.reportCount,
    riskScore: item.riskScore,
    submitAt: s.submitAt,
    reason: s.reason,
    handledBy: s.handledBy,
    handledResult: s.handledResult,
    parts,
    weight
  }
})

/** 等待分钟数：SLA 与「等待时长」列都用它 */
export const waitMinutesOf = (task: QueueTask): number => minutesBetween(task.submitAt)

/** 申诉任务排在前面：F-A4 规定「申诉队列优先级高于普通审核」 */
const KIND_ORDER: Record<QueueKind, number> = { appeal: 0, report: 1, item: 2 }

export const sortedQueue = (tasks: QueueTask[] = QUEUE_TASKS): QueueTask[] =>
  [...tasks].sort((a, b) => {
    const byKind = KIND_ORDER[a.kind] - KIND_ORDER[b.kind]
    if (byKind !== 0) return byKind
    return b.weight - a.weight
  })
