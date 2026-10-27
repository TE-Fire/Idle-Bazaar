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

/*
 * 分类与校区不在这份契约里定死：它们是**字典表数据**，管理端可增删改停用，
 * 名字由后端 `categoryName` / `campusName` 下发。
 * 早期这里写死过两份常量（分类含「自行车 / 宿舍家具 / 乐器」，校区是
 * 「中心校区 / 南苑宿舍区」），与库里实际字典对不上，筛选时只有名字恰好相同的项能命中。
 */

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
  /** 分类名（字典下发，通常是二级分类，如「手机平板」） */
  category: string
  /** 现价（元） */
  price: number
  /** 原价（元），可为空 */
  originalPrice?: number
  condition: ConditionGrade
  /** 校区名（字典下发） */
  campus: string
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
