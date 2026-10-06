/**
 * 审核队列（对应后端 ReviewAdminController，business/03 A-B2 / A-B3，流程见 notes/09）
 *
 * <p>三种来源（商品审核 / 举报处理 / 申诉复审）在**后端**合成一条队列（notes/09 问题 3），
 * 前端只按 `kind` 过滤切 Tab。排序口径（类型优先 + 组内权重降序 + 权重含实时等待补偿）
 * 只在后端实现一处——两边各算一次必然漂移，而「为什么它排第一」正是 F-A2 的判据。
 *
 * <p>权重五项由后端下发（notes/09 问题 2），前端不再本地算：
 * `@campus/shared` 的 `calcWeight` 保留但不参与队列展示（它是论文里可引用的公式实现）。
 *
 * <p>**没有单独的详情接口**：队列不分页，任务的全部字段（含商品图与凭证）一次给全，
 * Drawer 直接用列表里那一份。连续审核（J / K、处置完自动跳下一条）依赖「整个队列在前端手上」。
 *
 * <p>三条与后端约定的形状：
 *   ① `kind` 传/收**小写枚举名**（`item` / `report` / `appeal`），后端 `QueueKindEnum` 用 `@JsonValue`
 *      输出，与这里的 `QueueKind` 同形。
 *   ② 金额是**定长字符串**（`MoneyString`），与商品接口同一口径，要比较时显式 `Number(x)`。
 *   ③ `id` 是三种单据各自的主键，**跨来源不唯一**——列表 key 与处置入参都要带上 `kind`。
 */

import { request } from './http'
import type { ItemImageVO, ItemOfflineReq, ItemStatus, MoneyString } from './item'

/* ── 返回体（对齐后端 dto/resp，Long 已全局转 String） ── */

/** 队列来源，与后端 `QueueKindEnum` 的 `@JsonValue` 输出逐字一致 */
export type QueueKind = 'item' | 'report' | 'appeal'

/**
 * 权重五项：`report`(举报×3) / `risk`(风险分归一 0–10) / `credit`(信用扣减) /
 * `price`(价格异常 0 或 5) / `wait`(等待每满 12 小时 +1)。
 *
 * <p>前四项取 `item_audit` 的**进队快照**，`wait` 由后端按当前时间实时算——
 * 拆开返回是为了在 UI 上逐项解释「它为什么排第一」，只给总分审核员无法判断该不该信它。
 */
export interface ReviewWeightParts {
  report: number
  risk: number
  credit: number
  price: number
  wait: number
}

/** 队列里的一行。三种来源压成一个形状，前端才不用写三套渲染 */
export interface ReviewTaskVO {
  /** 单据主键：`item_audit.id` / `report.id` / `appeal.id`，跨来源不唯一 */
  id: string
  kind: QueueKind
  itemId: string
  itemTitle: string
  /** 二级分类名，判读「分类与描述是否对得上」用 */
  categoryName?: string | null
  /** 商品描述，判读四项里的那一项 */
  description?: string | null
  /** 主图；演示数据是外链（cdn.campus.local），加载失败时前端回退占位色块 */
  mainImage?: string | null
  images: ItemImageVO[]
  itemPrice: MoneyString
  /** 同类目已成交均价；该分类没有已成交订单时为 "0.00"（价格异常判定会跳过） */
  categoryAvgPrice: MoneyString
  sellerId: string
  sellerNick?: string | null
  sellerCredit?: number | null
  /** `report` 表的**实时**举报数，与权重里的进队快照口径不同 */
  reportCount: number
  /** 图片风险分 0–100，取该商品最新一条审核记录的快照 */
  riskScore: number
  /** 商品当前状态：举报「成立」会下架商品，商品若不可下架后端返回 1106 */
  itemStatus: ItemStatus
  /** 进队时间：等待补偿与「等待时长」列的基准 */
  submitAt: string
  /** 已等待小时数，由后端算好，省一次前后端时钟对齐 */
  waitHours: number
  /** 举报原因 / 申诉理由；商品审核没有 */
  reason?: string | null
  /** 举报 / 申诉的凭证 JSON，原样透传（结构由 C 端表单决定） */
  evidence?: string | null
  /** 申诉特有：原处置人 id（F-A4：复审人不得是他，命中后端返回 3002） */
  handledBy?: string | null
  /** 申诉特有：原处置人姓名 */
  handledByName?: string | null
  /** 申诉特有：原处置结果文案，如「已强制下架」 */
  handledResult?: string | null
  parts: ReviewWeightParts
  /** 排序键 = parts 五项之和 */
  weight: number
}

/**
 * SLA 概览 + 阈值。阈值也由后端下发而不是前端各存一份常量：
 * 将来挪到 A9 系统设置可配时，页面按 2 小时标红、后端按 4 小时统计的口径分裂就不会发生。
 */
export interface ReviewSlaVO {
  /** 队列总条数 */
  pending: number
  /** 等待 ≥ `firstResponseHours` 且 < `backlogHours`：要催 */
  due: number
  /** 等待 ≥ `backlogHours`：积压红牌 */
  overdue: number
  firstResponseHours: number
  backlogHours: number
  /** 单条处置目标（秒），操作效率指标，不要与响应时效混用 */
  avgHandleSeconds: number
}

export interface ReviewQueueVO {
  list: ReviewTaskVO[]
  sla: ReviewSlaVO
}

/* ── 入参 ── */

export interface ReviewRejectReq {
  /** 必填：驳回要写明依据，卖家据此修改后重提 */
  reason: string
  note?: string
}

export interface ReportHandleReq {
  /** 1 成立（商品下架） / 2 不成立（商品不动） */
  result: 1 | 2
  /** 成立时的处置依据，必填；不成立时可不填 */
  reason?: string
  note?: string
}

export interface AppealJudgeReq {
  /** 2 维持（商品不动） / 3 撤销（商品按原处置反向恢复） */
  result: 2 | 3
  note?: string
}

export const reviewApi = {
  /** 合并队列（不分页）。`kind` 不传 = 全部；筛选项由前端切 Tab，不必重新请求 */
  queue: (kind?: QueueKind) =>
    request<ReviewQueueVO>({ url: '/admin/review/queue', params: kind ? { kind } : {} }),

  /** 通过：商品「待审核 → 在售」并写 `published_at`，审核单据转已通过 */
  pass: (auditId: string) =>
    request<void>({ url: `/admin/review/item/${auditId}/pass`, method: 'POST' }),

  /** 驳回：商品「待审核 → 审核未过」，`reason` 必填（后端 `@NotBlank` → 1000） */
  reject: (auditId: string, req: ReviewRejectReq) =>
    request<void>({ url: `/admin/review/item/${auditId}/reject`, method: 'POST', data: req }),

  /**
   * 强制下架。与商品页的「下架」是同一个动作、同一个 Service 方法，
   * 区别只在入口：这里多一道「单据仍是待审核」的前置校验（重复处置 → 3001）。
   */
  forceOffline: (auditId: string, req: ItemOfflineReq) =>
    request<void>({ url: `/admin/review/item/${auditId}/force-offline`, method: 'POST', data: req }),

  /**
   * 举报裁决。没有独立的「受理」步骤（notes/09 问题 4）：
   * 点开即受理，裁决一步到位写 `status=3` + `result`。
   * 成立时商品一律走强制下架（→ 已强制下架），不区分轻重。
   */
  handleReport: (reportId: string, req: ReportHandleReq) =>
    request<void>({ url: `/admin/review/report/${reportId}/handle`, method: 'POST', data: req }),

  /**
   * 申诉复审。复审人不得是原处置人（F-A4），命中返回 3002——
   * 单人演示时这就是「换人复审」唯一能跑到的分支，页面要把 3002 的原文提示出来。
   */
  judgeAppeal: (appealId: string, req: AppealJudgeReq) =>
    request<void>({ url: `/admin/review/appeal/${appealId}/judge`, method: 'POST', data: req })
}
