/**
 * A3 商品管理的 mock 数据（business/03 A-B4）
 *
 * <p>`categoryAvgPrice` 是按一级分类给的成交均价，只用于两件事：
 *   ① 审核权重里的「价格异常分」（偏离 3 倍以上 = 5 分）；
 *   ② 商品详情页给审核员一个「这个价合不合理」的参照。
 * 所以它是**审核用**的字段，不是给买家看的划线价（那是 `originalPrice`）。
 *
 * <p>`riskScore` 是违禁图识别给出的 0–100 分，0 表示未识别/无风险。
 * 骨架期没有真算法，按「引流图 / 网图 / 违禁品」三类手工赋值，让队列排序看得出差别。
 */

import type { AdminItem } from '@campus/shared'
import { tintFor } from './tints'

/** 一级分类成交均价：权重公式的价格异常判定基准 */
const AVG_PRICE: Record<string, number> = {
  ca1: 25,
  ca2: 800,
  ca3: 60,
  ca4: 45,
  ca5: 150,
  ca501: 260,
  ca6: 15,
  ca7: 500,
  ca8: 30
}

const avgOf = (categoryId: string): number => {
  if (AVG_PRICE[categoryId] !== undefined) return AVG_PRICE[categoryId]
  const root = categoryId.slice(0, 3)
  return AVG_PRICE[root] ?? 50
}

const isoAgo = (days: number, hours = 0): string => {
  const d = new Date('2026-09-30T10:00:00+08:00')
  d.setDate(d.getDate() - days)
  d.setHours(d.getHours() - hours)
  return d.toISOString()
}

interface Seed {
  id: string
  title: string
  categoryId: string
  price: number
  originalPrice?: number
  condition: AdminItem['condition']
  campusId: string
  meetPoint: string
  sellerId: string
  sellerNick: string
  sellerCredit: number
  status: AdminItem['status']
  /** 发布于 N 天前（+N 小时） */
  ago: [number, number]
  viewCount: number
  wantCount: number
  reportCount: number
  riskScore: number
  description: string
}

const SEEDS: Seed[] = [
  { id: 'i2001', title: '高等数学（同济七版）上册 全新未拆', categoryId: 'ca101', price: 18, originalPrice: 45, condition: 'A', campusId: 'cp1', meetPoint: '中心校区·图书馆东门', sellerId: 'u1001', sellerNick: '阿泽', sellerCredit: 108, status: 'ON_SALE', ago: [3, 0], viewCount: 214, wantCount: 9, reportCount: 0, riskScore: 0, description: '上学期买的，翻了两次发现买错版本，塑封都没拆。' },
  { id: 'i2002', title: 'iPad 10.9 寸 64G WiFi 版 深空灰', categoryId: 'ca201', price: 1880, originalPrice: 3599, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·一食堂门口', sellerId: 'u1014', sellerNick: '石头', sellerCredit: 100, status: 'ON_SALE', ago: [5, 0], viewCount: 1120, wantCount: 47, reportCount: 0, riskScore: 12, description: '用了半年，无任何磕碰，电池健康 98%。带原装充电头。' },
  { id: 'i2003', title: '【急出】考研数学全套资料 含笔记', categoryId: 'ca103', price: 88, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·图书馆东门', sellerId: 'u1008', sellerNick: '青禾', sellerCredit: 112, status: 'ON_SALE', ago: [1, 6], viewCount: 386, wantCount: 21, reportCount: 1, riskScore: 0, description: '手写笔记 + 真题合集 + 网课讲义，考完试出。' },
  { id: 'i2004', title: '宿舍折叠书桌 可升降 带书架', categoryId: 'ca301', price: 65, originalPrice: 189, condition: 'C', campusId: 'cp3', meetPoint: '南苑宿舍区·快递驿站旁', sellerId: 'u1002', sellerNick: '小满', sellerCredit: 100, status: 'ON_SALE', ago: [8, 0], viewCount: 173, wantCount: 6, reportCount: 0, riskScore: 0, description: '毕业出，桌面有一处划痕，结构稳固。' },
  { id: 'i2005', title: '捷安特 ATX 山地车 24 速 26 寸', categoryId: 'ca501', price: 420, originalPrice: 1698, condition: 'C', campusId: 'cp2', meetPoint: '东校区·教学楼中庭', sellerId: 'u1013', sellerNick: '阿蓝', sellerCredit: 88, status: 'ON_SALE', ago: [12, 0], viewCount: 542, wantCount: 18, reportCount: 0, riskScore: 8, description: '链条新换，刹车灵敏，漆面有磨损。' },
  { id: 'i2006', title: '小熊电煮锅 1.8L 宿舍可用', categoryId: 'ca302', price: 42, originalPrice: 129, condition: 'B', campusId: 'cp3', meetPoint: '南苑宿舍区·快递驿站旁', sellerId: 'u1015', sellerNick: '糖糖', sellerCredit: 100, status: 'ON_SALE', ago: [4, 12], viewCount: 96, wantCount: 4, reportCount: 0, riskScore: 0, description: '功率 600W，宿舍不限电可用，配件齐全。' },
  { id: 'i2007', title: '雅马哈 FG800 民谣吉他 41 寸', categoryId: 'ca7', price: 780, originalPrice: 1899, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·体育馆南侧', sellerId: 'u1004', sellerNick: '果冻', sellerCredit: 100, status: 'PENDING', ago: [0, 9], viewCount: 61, wantCount: 3, reportCount: 0, riskScore: 24, description: '单板云杉，手感很好，买了新琴所以出。附赠琴包和变调夹。' },
  { id: 'i2008', title: 'Python 编程：从入门到实践 第 3 版', categoryId: 'ca6', price: 32, originalPrice: 89, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·图书馆东门', sellerId: 'u1011', sellerNick: '拾柒', sellerCredit: 100, status: 'PENDING', ago: [0, 5], viewCount: 28, wantCount: 2, reportCount: 0, riskScore: 0, description: '书角轻微磨损，无笔记无划线。' },
  { id: 'i2009', title: '【全新】AirPods Pro 2 代 正品带发票', categoryId: 'ca203', price: 299, originalPrice: 1899, condition: 'A', campusId: 'cp2', meetPoint: '东校区·三舍架空层', sellerId: 'u1007', sellerNick: '阿凯', sellerCredit: 58, status: 'PENDING', ago: [0, 20], viewCount: 1903, wantCount: 76, reportCount: 4, riskScore: 72, description: '全新未拆封，支持验机，可当面拆。数量有限，需要的加微信 xxxx 优先。' },
  { id: 'i2010', title: '机械键盘 87 键 红轴 客制化', categoryId: 'ca202', price: 155, originalPrice: 499, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·一食堂门口', sellerId: 'u1001', sellerNick: '阿泽', sellerCredit: 108, status: 'PENDING', ago: [0, 3], viewCount: 87, wantCount: 5, reportCount: 0, riskScore: 5, description: '佳达隆红轴，带 RGB，换过一套键帽。' },
  { id: 'i2011', title: '大学英语四级真题 全套 10 册', categoryId: 'ca102', price: 20, condition: 'C', campusId: 'cp3', meetPoint: '南苑宿舍区·校门内侧', sellerId: 'u1010', sellerNick: '一诺', sellerCredit: 100, status: 'PENDING', ago: [1, 14], viewCount: 44, wantCount: 1, reportCount: 0, riskScore: 0, description: '写过两本，其余空白。' },
  { id: 'i2012', title: '加微信 xxx 低价出学习资料 全网最全', categoryId: 'ca8', price: 1, condition: 'D', campusId: 'cp2', meetPoint: '东校区·三舍架空层', sellerId: 'u1007', sellerNick: '阿凯', sellerCredit: 58, status: 'PENDING', ago: [0, 26], viewCount: 312, wantCount: 0, reportCount: 6, riskScore: 88, description: '加微信免费送资料，另有各类代写代考，价格私聊。' },
  { id: 'i2013', title: 'Nike 空军一号 42.5 码 白色', categoryId: 'ca4', price: 210, originalPrice: 799, condition: 'C', campusId: 'cp1', meetPoint: '中心校区·体育馆南侧', sellerId: 'u1016', sellerNick: '风间', sellerCredit: 65, status: 'PENDING', ago: [1, 2], viewCount: 268, wantCount: 11, reportCount: 2, riskScore: 41, description: '穿过一季，鞋底轻微磨损，已清洗。' },
  { id: 'i2014', title: '显示器 27 寸 2K 165Hz 电竞屏', categoryId: 'ca202', price: 640, originalPrice: 2199, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·一食堂门口', sellerId: 'u1003', sellerNick: '老周', sellerCredit: 94, status: 'ON_SALE', ago: [6, 0], viewCount: 431, wantCount: 15, reportCount: 0, riskScore: 0, description: '无亮点无坏点，带原装箱子和电源线。' },
  { id: 'i2015', title: '考研英语词汇 红宝书', categoryId: 'ca103', price: 15, condition: 'C', campusId: 'cp1', meetPoint: '中心校区·图书馆东门', sellerId: 'u1008', sellerNick: '青禾', sellerCredit: 112, status: 'SOLD', ago: [15, 0], viewCount: 122, wantCount: 7, reportCount: 0, riskScore: 0, description: '前 30 页有笔记。' },
  { id: 'i2016', title: '电风扇 落地扇 遥控款', categoryId: 'ca302', price: 55, originalPrice: 199, condition: 'C', campusId: 'cp2', meetPoint: '东校区·三舍架空层', sellerId: 'u1014', sellerNick: '石头', sellerCredit: 100, status: 'OFFLINE', ago: [22, 0], viewCount: 78, wantCount: 2, reportCount: 0, riskScore: 0, description: '夏天用，入秋收起来了。' },
  { id: 'i2017', title: '【低价急出】iPhone 14 128G', categoryId: 'ca201', price: 1200, originalPrice: 5999, condition: 'B', campusId: 'cp2', meetPoint: '东校区·三舍架空层', sellerId: 'u1009', sellerNick: '胖虎', sellerCredit: 76, status: 'FORCED_OFFLINE', ago: [9, 0], viewCount: 1560, wantCount: 63, reportCount: 9, riskScore: 95, description: '急用钱低价出，不走平台验机，先款后货，加微信 xxxxx。' },
  { id: 'i2018', title: '大学物理实验报告册 全新', categoryId: 'ca102', price: 8, condition: 'A', campusId: 'cp1', meetPoint: '中心校区·图书馆东门', sellerId: 'u1011', sellerNick: '拾柒', sellerCredit: 100, status: 'REJECTED', ago: [7, 0], viewCount: 33, wantCount: 1, reportCount: 0, riskScore: 0, description: '买多了，全新。' },
  { id: 'i2019', title: '滑板 双翘板 专业板面', categoryId: 'ca502', price: 130, originalPrice: 480, condition: 'C', campusId: 'cp1', meetPoint: '中心校区·体育馆南侧', sellerId: 'u1004', sellerNick: '果冻', sellerCredit: 100, status: 'ON_SALE', ago: [2, 8], viewCount: 156, wantCount: 8, reportCount: 0, riskScore: 0, description: '板面有磕碰，轮子八成新。' },
  { id: 'i2020', title: '洗衣机 迷你 宿舍用 半自动', categoryId: 'ca302', price: 180, originalPrice: 599, condition: 'C', campusId: 'cp3', meetPoint: '南苑宿舍区·快递驿站旁', sellerId: 'u1015', sellerNick: '糖糖', sellerCredit: 100, status: 'ON_SALE', ago: [10, 0], viewCount: 201, wantCount: 5, reportCount: 1, riskScore: 0, description: '毕业出，脱水正常，容量 4.5kg。' },
  { id: 'i2021', title: '线性代数辅导讲义 李永乐', categoryId: 'ca102', price: 12, condition: 'D', campusId: 'cp2', meetPoint: '东校区·教学楼中庭', sellerId: 'u1010', sellerNick: '一诺', sellerCredit: 100, status: 'REJECTED', ago: [5, 0], viewCount: 41, wantCount: 0, reportCount: 0, riskScore: 0, description: '全书有笔记，介意勿拍。' },
  { id: 'i2022', title: '尤克里里 23 寸 入门款', categoryId: 'ca7', price: 95, originalPrice: 268, condition: 'B', campusId: 'cp3', meetPoint: '南苑宿舍区·快递驿站旁', sellerId: 'u1002', sellerNick: '小满', sellerCredit: 100, status: 'ON_SALE', ago: [3, 16], viewCount: 88, wantCount: 4, reportCount: 0, riskScore: 6, description: '买来没学会，几乎全新，送调音器。' },
  { id: 'i2023', title: '台灯 LED 护眼 三档调光', categoryId: 'ca303', price: 25, originalPrice: 89, condition: 'B', campusId: 'cp1', meetPoint: '中心校区·一食堂门口', sellerId: 'u1001', sellerNick: '阿泽', sellerCredit: 108, status: 'SOLD', ago: [18, 0], viewCount: 67, wantCount: 3, reportCount: 0, riskScore: 0, description: '夹灯款，可夹床沿。' },
  { id: 'i2024', title: '自行车 折叠车 20 寸 通勤', categoryId: 'ca501', price: 320, originalPrice: 899, condition: 'C', campusId: 'cp2', meetPoint: '东校区·教学楼中庭', sellerId: 'u1016', sellerNick: '风间', sellerCredit: 65, status: 'ON_SALE', ago: [7, 20], viewCount: 289, wantCount: 12, reportCount: 1, riskScore: 15, description: '折叠顺畅，车胎半新，刹车调过。' }
]

export const ITEMS: AdminItem[] = SEEDS.map((s) => ({
  id: s.id,
  title: s.title,
  categoryId: s.categoryId,
  price: s.price,
  originalPrice: s.originalPrice,
  condition: s.condition,
  campusId: s.campusId,
  meetPoint: s.meetPoint,
  sellerId: s.sellerId,
  sellerNick: s.sellerNick,
  sellerCredit: s.sellerCredit,
  status: s.status,
  publishAt: isoAgo(s.ago[0], s.ago[1]),
  auditAt: s.status === 'PENDING' ? undefined : isoAgo(Math.max(0, s.ago[0] - 1)),
  viewCount: s.viewCount,
  wantCount: s.wantCount,
  reportCount: s.reportCount,
  riskScore: s.riskScore,
  categoryAvgPrice: avgOf(s.categoryId),
  description: s.description,
  tints: tintFor(s.id)
}))

export const itemById = (id: string): AdminItem | undefined => ITEMS.find((i) => i.id === id)
