/**
 * 管理端业务枚举 / 模型（business/03 + design/03）
 *
 * <p>与 `item.ts` 同为**前端先行**版本：后端业务代码未落地（计划由作者自行实现），
 * 这里先把字典、状态、权重口径定死，后端枚举出来后以本文件为准对齐一次，
 * 不要两边各写一套。
 *
 * <p>两条硬约定（沿用 item.ts）：
 *   ① `id` 一律 string（后端雪花 ID 转字符串，number 会精度丢失）；
 *   ② 金额单位**元**（number），后端若按分存必须在 api 层换算。
 */

/* ───────────────────────── 一、五张字典（A-B6 / F-A7） ───────────────────────── */

/**
 * 字典的五种类型。business/03 F-A7 写的是「四个可维护字典」，
 * 其中「学院 / 校区」实际是两张表（dict_college、dict_campus），故这里是五张。
 */
export const DICT_TYPES = ['category', 'grade', 'meetPoint', 'college', 'campus'] as const
export type DictType = (typeof DICT_TYPES)[number]

export const DICT_LABELS: Record<DictType, string> = {
  category: '分类',
  grade: '成色等级',
  meetPoint: '面交点',
  college: '学院',
  campus: '校区'
}

/** 分类树（两级：dict_category） */
export interface DictCategoryItem {
  id: string
  name: string
  /** 一级分类为 null */
  parentId: string | null
  sort: number
  enabled: boolean
  /** 该分类下的在售商品数，用于删除前的提示 */
  itemCount: number
}

/** 成色等级 A–D（dict_grade）：文案与颜色全端共用，不允许前端写死 */
export interface DictGradeItem {
  code: 'A' | 'B' | 'C' | 'D'
  label: string
  desc: string
  /** design/01 §4.1：A/B 绿、C 橙、D 灰 */
  color: string
  enabled: boolean
}

/** 面交点（dict_meet_point） */
export interface DictMeetPointItem {
  id: string
  name: string
  campusId: string
  /** 开放时段文案，如「08:00–22:00」 */
  openHours: string
  enabled: boolean
}

export interface DictCollegeItem {
  id: string
  name: string
  /** 所属校区，报表「学院×品类」矩阵的行由此派生 */
  campusId: string
  enabled: boolean
}

export interface DictCampusItem {
  id: string
  name: string
  enabled: boolean
}

/* ───────────────────────── 二、用户（A-B5 / F-A5） ───────────────────────── */

/** 学号认证状态（business/03 §4.4） */
export const AUTH_STATUS = {
  NONE: 'NONE',
  PENDING: 'PENDING',
  PASSED: 'PASSED',
  REJECTED: 'REJECTED'
} as const
export type AuthStatus = (typeof AUTH_STATUS)[keyof typeof AUTH_STATUS]

export const AUTH_STATUS_LABELS: Record<AuthStatus, { label: string; color: string }> = {
  NONE: { label: '未认证', color: 'default' },
  PENDING: { label: '审核中', color: 'blue' },
  PASSED: { label: '已认证', color: 'green' },
  REJECTED: { label: '未通过', color: 'red' }
}

/** 账号状态（business/03 §4.4）：正常 → 警告 → 封禁（可解封） */
export const ACCOUNT_STATUS = {
  NORMAL: 'NORMAL',
  WARNED: 'WARNED',
  BANNED: 'BANNED'
} as const
export type AccountStatus = (typeof ACCOUNT_STATUS)[keyof typeof ACCOUNT_STATUS]

export const ACCOUNT_STATUS_LABELS: Record<AccountStatus, { label: string; color: string }> = {
  NORMAL: { label: '正常', color: 'green' },
  WARNED: { label: '已警告', color: 'orange' },
  BANNED: { label: '已封禁', color: 'red' }
}

/** 认证材料（骨架期用渐变占位，MinIO 就位后换成 URL） */
export interface AuthMaterial {
  /** 一卡通 / 校园邮箱截图 */
  kind: '学生证' | '一卡通' | '校园邮箱'
  /** 占位渐变（两色），与 GoodsItem.tints 同思路 */
  tints: [string, string]
}

export interface AppUser {
  id: string
  nick: string
  realName: string
  /** 学号：认证审核的核对项之一 */
  studentNo: string
  collegeId: string
  campusId: string
  creditScore: number
  authStatus: AuthStatus
  accountStatus: AccountStatus
  itemCount: number
  orderCount: number
  registerAt: string
  lastLoginAt: string
  /** 头像占位渐变（两色） */
  tints: [string, string]
  /** 仅在打开认证审核抽屉时需要 */
  material?: AuthMaterial
  /** 上次认证被驳回的原因 */
  rejectReason?: string
}

/** 信用分变动（credit_log）：初始 100，上限 120 */
export interface CreditLogItem {
  id: string
  userId: string
  /** 正数为加分 */
  delta: number
  after: number
  reason: string
  at: string
}

/* ───────────────────────── 三、商品（A-B4） ───────────────────────── */

/**
 * 管理视角的商品：比 C 端的 GoodsItem 多三个「处置用」字段——
 * 举报次数、风险分、同类目均价偏离，这三个是审核权重的输入（见下节）。
 */
export interface AdminItem {
  id: string
  title: string
  categoryId: string
  price: number
  originalPrice?: number
  condition: 'A' | 'B' | 'C' | 'D'
  campusId: string
  meetPoint: string
  sellerId: string
  sellerNick: string
  sellerCredit: number
  status: 'DRAFT' | 'PENDING' | 'ON_SALE' | 'SOLD' | 'OFFLINE' | 'REJECTED' | 'FORCED_OFFLINE'
  publishAt: string
  /** 审核通过时间，SLA 的「首审响应」要用 */
  auditAt?: string
  viewCount: number
  wantCount: number
  reportCount: number
  /** 违禁图识别给出的 0–100 风险分，0 表示未识别 */
  riskScore: number
  /** 同类目成交均价（元），用于价格异常判定 */
  categoryAvgPrice: number
  description: string
  /** 骨架期占位渐变（两色） */
  tints: [string, string]
}

/** 价格异常：偏离同类目均价 3 倍以上（business/03 F-A2 权重公式） */
export const isPriceAbnormal = (item: Pick<AdminItem, 'price' | 'categoryAvgPrice'>): boolean => {
  const avg = item.categoryAvgPrice
  if (avg <= 0) return false
  return item.price > avg * 3 || item.price < avg / 3
}

/** 下架原因（预设，驳回/强制下架共用） */
export const OFFLINE_REASONS = [
  '图片不实（网图或与实物不符）',
  '涉违禁品',
  '校外引流（含联系方式/二维码）',
  '分类与成色错配',
  '价格异常（疑似刷单或诱导）',
  '其他'
] as const
export type OfflineReason = (typeof OFFLINE_REASONS)[number]

/* ───────────────────────── 四、审核 / 举报 / 申诉（A-B2 / A-B3 / F-A2~F-A4） ───────────────────────── */

/** 队列里的一条任务，三种来源统一成一个形状才能共用「权重排序 + 连续审核」 */
export const QUEUE_KINDS = ['item', 'report', 'appeal'] as const
export type QueueKind = (typeof QUEUE_KINDS)[number]

export const QUEUE_KIND_LABELS: Record<QueueKind, string> = {
  item: '商品审核',
  report: '举报处理',
  appeal: '申诉复审'
}

/** 权重分的四个组成部分，拆开存是为了在 UI 上能解释「为什么它排第一」 */
export interface WeightParts {
  /** 举报次数 × 3 */
  report: number
  /** 图片风险分（0–100）归一化为 0–10 */
  risk: number
  /** 卖家信用扣减分：(100 − 信用分) / 10 */
  credit: number
  /** 价格异常分：偏离均价 3 倍以上 = 5 分 */
  price: number
  /** 等待衰减补偿：每满 12 小时 +1 */
  wait: number
}

export interface QueueTask {
  id: string
  kind: QueueKind
  itemId: string
  itemTitle: string
  itemTints: [string, string]
  itemPrice: number
  categoryAvgPrice: number
  sellerId: string
  sellerNick: string
  sellerCredit: number
  reportCount: number
  riskScore: number
  /** 进入队列的时间，SLA 与权重都依赖它 */
  submitAt: string
  /** 举报/申诉特有：原因与凭证 */
  reason?: string
  /** 申诉特有：原处置人与原处置结果（换人复审要用） */
  handledBy?: string
  handledResult?: string
  /** 商品审核的等待时长（分钟），由 submitAt 与当前时间算出 */
  parts: WeightParts
  weight: number
}

/**
 * 审核队列权重公式（business/03 F-A2，可直接写进论文）
 *
 * <p>注意最后一项是**补偿而不是惩罚**：等待越久分越高，否则举报少但一直没人管的单子会永远沉底。
 */
export const calcWeight = (input: {
  reportCount: number
  riskScore: number
  sellerCredit: number
  price: number
  categoryAvgPrice: number
  waitHours: number
}): WeightParts & { weight: number } => {
  const report = input.reportCount * 3
  const risk = Math.round((input.riskScore / 100) * 10) / 1
  const credit = (100 - input.sellerCredit) / 10
  const price = isPriceAbnormal({ price: input.price, categoryAvgPrice: input.categoryAvgPrice })
    ? 5
    : 0
  const wait = Math.floor(input.waitHours / 12)
  return {
    report,
    risk,
    credit,
    price,
    wait,
    weight: Math.round((report + risk + credit + price + wait) * 10) / 10
  }
}

/** SLA 两级指标（business/03 F-A9）：响应时效 vs 操作效率，不可混用 */
export interface SlaConfig {
  /** 首审响应达标阈值（小时） */
  firstResponseHours: number
  /** P95 首审时长上限（小时） */
  p95Hours: number
  /** 整体达标率下限（%） */
  targetRate: number
  /** 超过该时长未处置记积压红牌（小时） */
  backlogHours: number
  /** 单条平均处置目标（秒） */
  avgHandleSeconds: number
  /** 申诉复审承诺时长（小时） */
  appealHours: number
}

/** 等待时长相对 SLA 的档位：临期要催、超时要红牌 */
export type SlaLevel = 'normal' | 'due' | 'overdue'

export const slaLevel = (waitHours: number, cfg: Pick<SlaConfig, 'firstResponseHours' | 'backlogHours'>): SlaLevel => {
  if (waitHours >= cfg.backlogHours) return 'overdue'
  if (waitHours >= cfg.firstResponseHours) return 'due'
  return 'normal'
}

export const SLA_LEVEL_LABELS: Record<SlaLevel, { label: string; color: string }> = {
  normal: { label: '正常', color: 'green' },
  due: { label: '临期', color: 'orange' },
  overdue: { label: '超时', color: 'red' }
}

/* ───────────────────────── 五、订单（A-B7 / F-A6） ───────────────────────── */

export const ORDER_STATUS = {
  UNPAID: 'UNPAID',
  PAID: 'PAID',
  MEETING: 'MEETING',
  DONE: 'DONE',
  CANCELED: 'CANCELED',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
  DISPUTE: 'DISPUTE'
} as const
export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS]

export const ORDER_STATUS_LABELS: Record<OrderStatus, { label: string; color: string }> = {
  UNPAID: { label: '待付款', color: 'default' },
  PAID: { label: '已付款', color: 'blue' },
  MEETING: { label: '待面交', color: 'blue' },
  DONE: { label: '已完成', color: 'green' },
  CANCELED: { label: '已取消', color: 'default' },
  REFUNDING: { label: '退款中', color: 'orange' },
  REFUNDED: { label: '已退款', color: 'orange' },
  DISPUTE: { label: '纠纷中', color: 'red' }
}

/**
 * 商品快照：下单时的价格 / 成色 / 描述
 *
 * <p>F-A6 判据明确要求看**快照而非当前商品**——卖家可能事后改价改描述，
 * 这是纠纷取证的关键设计点，页面上必须把「快照」两个字标出来。
 */
export interface ItemSnapshot {
  title: string
  price: number
  condition: 'A' | 'B' | 'C' | 'D'
  description: string
  tints: [string, string]
}

export interface TradeOrder {
  id: string
  orderNo: string
  itemId: string
  snapshot: ItemSnapshot
  buyerId: string
  buyerNick: string
  sellerId: string
  sellerNick: string
  status: OrderStatus
  meetPoint: string
  /** 约定面交时间 */
  meetAt: string
  createdAt: string
  /** 仅纠纷单有 */
  dispute?: {
    reason: string
    /** 发起方：买家或卖家 */
    by: 'buyer' | 'seller'
    at: string
  }
}

/** 纠纷裁决的三种结果（business/03 F-A6） */
export const ARBITRATE_ACTIONS = [
  { value: 'refundBuyer', label: '退款买家' },
  { value: 'paySeller', label: '放款卖家' },
  { value: 'partial', label: '部分退款' }
] as const

/* ───────────────────────── 六、系统设置（A-B9） ───────────────────────── */

export interface OperationLogItem {
  id: string
  operator: string
  /** 模块名：商品 / 用户 / 审核 / 订单 / 内容 / 系统 */
  module: string
  action: string
  target: string
  /** 处置依据（驳回原因等），可为空 */
  basis?: string
  ip: string
  at: string
}

/** 敏感词（sys_sensitive_word）：命中即进高风险队列 */
export interface SensitiveWord {
  id: string
  word: string
  level: '拦截' | '疑似'
  enabled: boolean
}
