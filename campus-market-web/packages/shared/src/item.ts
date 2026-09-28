/**
 * 商品与交易的业务枚举 / 模型（三端共用）
 *
 * <p>与 `perm-codes.ts` 同理：这里的字符串要跟后端枚举、数据库字典、原型文案
 * 保持一致。后端学生端业务代码尚未落地（campus-user-api 目前只有 /api/health），
 * 所以这份定义是**前端先行**版本——后端枚举出来后以本文件为准做一次对齐，
 * 不要两边各写一套。
 *
 * <p>成色四档文案取自 `prototypes/web/web-home.html` 侧栏；配色取自
 * `prototypes/web/web.css` 的 `.cond.a~d`（A/B 绿、C 橙、D 灰）。
 */

/** 成色 A–D：平台统一定义，虚标会被举报并影响信用（business/01 §三 F-W3） */
export const CONDITION_GRADES = ['A', 'B', 'C', 'D'] as const
export type ConditionGrade = (typeof CONDITION_GRADES)[number]

export const CONDITION_LABELS: Record<ConditionGrade, string> = {
  A: '全新未拆',
  B: '几乎全新',
  C: '轻微使用痕迹',
  D: '明显使用痕迹'
}

/**
 * 商品状态（business/01 §四 商品（卖家视角））
 *
 * <p>注意没有「已预定」——该状态已取消，拍下不锁货，付款才占位。
 */
export const ITEM_STATUS = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  ON_SALE: 'ON_SALE',
  SOLD: 'SOLD',
  OFFLINE: 'OFFLINE',
  REJECTED: 'REJECTED',
  FORCED_OFFLINE: 'FORCED_OFFLINE'
} as const
export type ItemStatus = (typeof ITEM_STATUS)[keyof typeof ITEM_STATUS]

export const ITEM_STATUS_LABELS: Record<ItemStatus, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  ON_SALE: '在售',
  SOLD: '已售出',
  OFFLINE: '已下架',
  REJECTED: '审核未过',
  FORCED_OFFLINE: '已强制下架'
}

/** 分类条（prototypes/web/web-home.html .cat） */
export const CATEGORIES = [
  '首页',
  '教材教辅',
  '数码电子',
  '自行车',
  '宿舍家具',
  '服饰鞋包',
  '运动器材',
  '乐器',
  '票务卡券',
  '其他闲置'
] as const
export type Category = (typeof CATEGORIES)[number]

/** 校区 / 面交点（原型侧栏三档） */
export const CAMPUSES = ['中心校区', '东校区', '南苑宿舍区'] as const
export type Campus = (typeof CAMPUSES)[number]

/** 卡片角标：急出与「N 人想要」用价格色底，其余半透黑底（design/01 §4.1） */
export const ITEM_TAGS = ['急出', '可议价', '包配送'] as const
export type ItemTag = (typeof ITEM_TAGS)[number]

/**
 * 商品
 *
 * <p>两条硬约定：① `id` 一律 string（后端雪花 ID 已转字符串，number 会精度丢失）；
 * ② 价格单位**元**（number）。后端若按分存，需在 api 层换算，不要让视图层知道。
 *
 * <p>`tints` 是骨架期的图片占位（渐变色对）——MinIO 与上传接口就位后换成真实 URL，
 * 字段名保持 `images`，视图层不用改。
 */
export interface GoodsItem {
  id: string
  title: string
  category: Category
  /** 现价（元） */
  price: number
  /** 原价（元），可为空 */
  originalPrice?: number
  condition: ConditionGrade
  campus: Campus
  /** 面交点：校园内的具体交接位置 */
  meetPoint: string
  negotiable: boolean
  delivery: boolean
  status: ItemStatus
  /** 「N 人想要」 */
  wantCount: number
  /** 角标，至多两个 */
  badge?: ItemTag
  seller: SellerBrief
  /** ISO 字符串；展示时按 design/01 §七 做相对时间 */
  publishedAt: string
  description: string
  images: string[]
  /** 骨架期占位渐变（两色） */
  tints: [string, string]
}

export interface SellerBrief {
  id: string
  nick: string
  /** 学号认证通过 */
  verified: boolean
  /** 信用等级文案，原型取「极好 / 良好 / 一般」三档 */
  credit: string
  /** 头像占位渐变（两色） */
  tints: [string, string]
}
