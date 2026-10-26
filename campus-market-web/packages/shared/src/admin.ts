/**
 * 管理端业务枚举 / 模型（business/03 + design/03）
 *
 * <p>**前端先行**的部分：用户、订单两域的后端还没落地（计划由作者自行实现），
 * 这里先把状态与口径定死，后端枚举出来后以本文件为准对齐一次，不要两边各写一套。
 * 字典 / 商品 / 审核三域已接真接口，它们的**数据结构**属于接口契约、定义在 `@campus/api`，
 * 本文件只保留全端共用的枚举、文案与公式。
 *
 * <p>两条硬约定：
 *   ① `id` 一律 string（后端雪花 ID 转字符串，number 会精度丢失）；
 *   ② 已接接口的模块金额是**定长字符串**（`MoneyString`，看 `@campus/api` 的 `item.ts`）；
 *      仍是 mock 的模块（用户 / 订单）暂时用 number，接接口时一并改。
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

/**
 * 队列里的一条任务，三种来源统一成一个形状才能共用「权重排序 + 连续审核」。
 *
 * <p>**任务的字段形状不在这里**：它是接口契约，随后端 `ReviewTaskVO` 走，
 * 定义在 `@campus/api` 的 `review.ts`（`ReviewTaskVO`），字段名与后端的差异要一次对齐完。
 * 本文件只留与**展示**有关的东西：来源枚举与中文名、权重公式、SLA 档位。
 */
export const QUEUE_KINDS = ['item', 'report', 'appeal'] as const
export type QueueKind = (typeof QUEUE_KINDS)[number]

export const QUEUE_KIND_LABELS: Record<QueueKind, string> = {
  item: '商品审核',
  report: '举报处理',
  appeal: '申诉复审'
}

/** 权重分的五个组成部分，拆开存是为了在 UI 上能解释「为什么它排第一」 */
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

/**
 * 审核队列权重公式（business/03 F-A2，可直接写进论文）
 *
 * <p>注意最后一项是**补偿而不是惩罚**：等待越久分越高，否则举报少但一直没人管的单子会永远沉底。
 *
 * <p>**队列页不调用它**（notes/09 问题 2）：排序与权重五项都由后端下发，
 * 前端再算一次就会出现「排第一的那条解释出来的分不是最高」。
 * 留在这里是因为它是一份可被论文直接引用的公式实现，也是权重口径的单一说明。
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

/**
 * 订单状态（八态，与后端 `OrderStatusEnum` 枚举名逐字一致）
 *
 * <p>**不再是 mock 那套 UNPAID/PAID/MEETING/DONE**：那套是原型期的自造码，
 * 与 `trade_order.status`、后端 `@JsonValue` 输出的枚举名对不上，真实接口接进来时
 * 状态标签会整列显示成兜底文案。以库里 1–8 的语义为准：
 * 待付款 / 待面交 / 已验货 / 已完成 / 已取消 / 退款中 / 已退款 / 纠纷中。
 *
 * <p>`VERIFIED`（已验货）是面交扫码核销后的独立中间态，不是终态。
 */
export const ORDER_STATUS = {
  WAIT_PAY: 'WAIT_PAY',
  WAIT_MEET: 'WAIT_MEET',
  VERIFIED: 'VERIFIED',
  COMPLETED: 'COMPLETED',
  CANCELED: 'CANCELED',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
  DISPUTING: 'DISPUTING'
} as const
export type OrderStatus = (typeof ORDER_STATUS)[keyof typeof ORDER_STATUS]

export const ORDER_STATUS_LABELS: Record<OrderStatus, { label: string; color: string }> = {
  WAIT_PAY: { label: '待付款', color: 'default' },
  WAIT_MEET: { label: '待面交', color: 'blue' },
  VERIFIED: { label: '已验货', color: 'cyan' },
  COMPLETED: { label: '已完成', color: 'green' },
  CANCELED: { label: '已取消', color: 'default' },
  REFUNDING: { label: '退款中', color: 'orange' },
  REFUNDED: { label: '已退款', color: 'orange' },
  DISPUTING: { label: '纠纷中', color: 'red' }
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

/**
 * 纠纷裁决的两种结果（business/03 F-A6 本期口径）
 *
 * <p>**没有「部分退款」**：订单终态只有已退款 / 已完成，库里没有部分退状态，
 * 学生端也没有对应流程，收了这个选项也落不了库。`value` 直接就是
 * `POST /admin/order/{id}/arbitrate` 的 `result` 取值，前端不做二次翻译。
 */
export const ARBITRATE_ACTIONS = [
  { value: 'REFUND_BUYER', label: '退款买家', hint: '订单转已退款，商品恢复在售' },
  { value: 'PAY_SELLER', label: '放款卖家', hint: '订单转已完成，货款归卖家' }
] as const

/* ───────────────────────── 六、系统设置（A-B9） ───────────────────────── */

/**
 * 操作日志「英文码 → 中文标签」映射（**前端维护**，2026-10-06 决策）
 *
 * <p>为什么放在前端而不在后端 VO 里翻译：`opType` 是随业务持续增加的**长尾**——
 * 每加一个写接口就可能多一个 `type`（当前 22 个），后端每加一个都得跟着改翻译层。
 * 放这里加一行即可。两张表都用 `Record<string, string>` 且**兜底显示原码**，
 * 漏配的后果是「看到一个英文码」而不是空白——不追求完整性，只求不丢信息。
 *
 * <p>码值以后端 `@OpLog(type = "...")` 为准：**改注解时要同步改这里**。
 * 中文用业务动作自己的说法（「强制下架」），不按英文码直译（不写「关闭货架」）。
 *
 * <p>与 `description` 的分工：`description` 是注解上的接口级说明，更具体
 * （审核入口的强制下架是「强制下架（审核入口）」）；这里给的是**同类动作的统一短标签**，
 * 用于列表快速扫读。两者不完全一致不算 bug。
 */
export const LOG_MODULE_LABELS: Record<string, string> = {
  item: '商品',
  review: '审核',
  content: '内容',
  system: '系统',
  /** 预留：订单模块的介入裁决还没落地（business/03 F-A11 ⑤） */
  order: '订单'
}

export const OP_TYPE_LABELS: Record<string, string> = {
  /* item */
  OFF_SHELF: '强制下架',
  DELETE: '删除商品',

  /* review */
  APPROVE: '审核通过',
  REJECT: '审核驳回',
  FORCE_OFF: '审核下架',
  REPORT_HANDLE: '举报处置',
  APPEAL_JUDGE: '申诉裁决',

  /* content（四个字典共用一个 module，靠 type 区分是改哪张表） */
  CATEGORY_CREATE: '新增分类',
  CATEGORY_UPDATE: '编辑分类',
  CATEGORY_STATUS: '启停分类',
  GRADE_SAVE: '保存成色',
  GRADE_STATUS: '启停成色',
  MEET_POINT_CREATE: '新增面交点',
  MEET_POINT_UPDATE: '编辑面交点',
  MEET_POINT_STATUS: '启停面交点',
  COLLEGE_CREATE: '新增学院',
  COLLEGE_UPDATE: '编辑学院',
  COLLEGE_STATUS: '启停学院',
  CAMPUS_CREATE: '新增校区',
  CAMPUS_UPDATE: '编辑校区',
  CAMPUS_STATUS: '启停校区',

  /* system */
  ASSIGN_ROLE: '分配角色'
}

/** 模块筛选下拉的取值；从标签表派生，避免两处各写一遍 */
export const LOG_MODULES = Object.keys(LOG_MODULE_LABELS)

/** 敏感词（sys_sensitive_word）：命中即进高风险队列 */
export interface SensitiveWord {
  id: string
  word: string
  level: '拦截' | '疑似'
  enabled: boolean
}
