/**
 * 商品 mock 数据（骨架期）
 *
 * <p>为什么用 LCG 而不是 `Math.random`：论文截图要**每次刷新都一致**，
 * 随机数据会导致同一段文字配不同的图/价。种子写死，序列就写死。
 *
 * <p>`tints` 是图片占位渐变——`packages/shared` 里保留这个字段只是为了让骨架
 * 能离线跑起来。MinIO 与上传接口就位后：`images` 换成真实 URL，`tints` 直接删掉，
 * 视图层不用跟着改。
 *
 * <p>后端 `/api/item/*` 就绪后删掉本文件，改由 `packages/api` 提供数据源。
 */

import {
  CAMPUSES,
  CONDITION_GRADES,
  CONDITION_LABELS,
  ITEM_STATUS,
  ITEM_TAGS,
  type Category,
  type ConditionGrade,
  type GoodsItem
} from '@campus/shared'

/** 相对时间的基准：固定在 2026-09-28，保证截图里的「3 小时前」不会漂 */
const NOW = new Date('2026-09-28T20:00:00+08:00').getTime()

/** 线性同余：种子固定 → 序列固定 */
function createRandom(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state * 1103515245 + 12345) % 2147483648
    return state / 2147483648
  }
}

const rand = createRandom(20260928)
const pick = <T>(arr: readonly T[]): T => arr[Math.floor(rand() * arr.length)]
const int = (min: number, max: number): number => min + Math.floor(rand() * (max - min + 1))

/** 标题池：按分类组织，避免生成「教材教辅」分类下出现自行车的笑话 */
const TITLE_POOL: Array<{ category: Category; titles: string[] }> = [
  { category: '教材教辅', titles: ['高等数学 第七版 上下册', '考研数学复习全书', '四级词汇 词根+联想', '计算机网络 谢希仁 第8版'] },
  { category: '数码电子', titles: ['iPad 9 64G WiFi 版', '罗技 K380 蓝牙键盘', 'Kindle Paperwhite 4', '小米移动电源 20000mAh'] },
  { category: '自行车', titles: ['捷安特 ATX 660 山地车', '雅迪 小电驴 48V', '折叠自行车 20 寸', '自行车锁 + 车筐套装'] },
  { category: '宿舍家具', titles: ['宿舍床头置物架', '折叠电脑桌 60cm', '宜家 马克杯 6 只装', '衣柜分层隔板'] },
  { category: '服饰鞋包', titles: ['Nike Air Force 1 42码', '双肩背包 15.6 寸电脑仓', '冬季加绒卫衣 L 码', '帆布鞋 38 码'] },
  { category: '运动器材', titles: ['瑜伽垫 8mm', '羽毛球拍 尤尼克斯', '哑铃 5kg 一对', '跳绳 计数款'] },
  { category: '乐器', titles: ['雅马哈 F310 吉他', '电子琴 61 键', '尤克里里 23 寸', '吉他变调夹 + 拨片'] },
  { category: '票务卡券', titles: ['校园健身房季卡', '游泳馆 10 次卡', '打印店充值卡 50 元', '电影兑换券 2 张'] },
  { category: '其他闲置', titles: ['台灯 护眼三档调光', '电热水壶 1.7L', '收纳箱 3 只装', '小风扇 静音款'] }
]

const SELLERS = ['阿蒙', '小林同学', '一只鹿', '老张', '橘子汽水', '夜航船', '青柠', '不吃香菜']
const CREDITS = ['极好', '极好', '良好', '良好', '一般']
const MEET_POINTS: Record<string, string[]> = {
  中心校区: ['图书馆东门', '三食堂门口', '教学楼 A 座大厅'],
  东校区: ['东苑超市旁', '东区操场看台', '东教 3 号楼下'],
  南苑宿舍区: ['南苑 5 舍楼下', '南苑快递驿站', '南苑食堂侧门']
}

/** 占位渐变（低饱和中性色，不与品牌黄抢注意力） */
const TINT_PAIRS: Array<[string, string]> = [
  ['#E8EEF6', '#C7D6E8'],
  ['#EFEAF7', '#D6CCEB'],
  ['#E7F2EC', '#C6E0D3'],
  ['#F6EEE6', '#E5D5C3'],
  ['#ECEFF3', '#D3D9E0'],
  ['#F2ECEF', '#DCCBD4']
]

const AVATAR_TINTS: Array<[string, string]> = [
  ['#BFD4EA', '#8FA9C4'],
  ['#D8C8E8', '#A88FC4'],
  ['#C9E3D4', '#94C3AA'],
  ['#EAD7C4', '#C4A183']
]

function buildItem(index: number): GoodsItem {
  const group = TITLE_POOL[index % TITLE_POOL.length]
  const category = group.category
  const title = group.titles[Math.floor(index / TITLE_POOL.length) % group.titles.length]
  const price = int(15, 680)
  const hasOriginal = rand() > 0.35
  const condition: ConditionGrade = pick(CONDITION_GRADES)
  const campus = pick(CAMPUSES)
  const nick = pick(SELLERS)
  const meetPoint = pick(MEET_POINTS[campus])

  return {
    id: String(1900000000000000000n + BigInt(index) * 7n),
    title,
    category,
    price,
    originalPrice: hasOriginal ? price + int(30, 260) : undefined,
    condition,
    campus,
    meetPoint,
    negotiable: rand() > 0.5,
    delivery: rand() > 0.7,
    status: ITEM_STATUS.ON_SALE,
    wantCount: int(0, 86),
    badge: rand() > 0.55 ? pick(ITEM_TAGS) : undefined,
    seller: {
      id: String(1800000000000000000n + BigInt(index) * 13n),
      nick,
      verified: rand() > 0.25,
      credit: pick(CREDITS),
      tints: pick(AVATAR_TINTS)
    },
    // 发布时间：0–9 天内随机，落在相对时间规则的两侧（7 天是分界）
    publishedAt: new Date(NOW - int(1, 9 * 24) * 3600_000).toISOString(),
    description:
      `${title}，${CONDITION_LABELS[condition]}，功能完好无暗病。\n` +
      `因毕业 / 换设备出闲置，可当面验货。\n面交点：${campus} ${meetPoint}。`,
    images: [],
    tints: pick(TINT_PAIRS)
  }
}

/** 36 件：够三页（每页 12 件，design/01 原则 3 要求一屏至少 12 件） */
export const mockItems: GoodsItem[] = Array.from({ length: 36 }, (_, i) => buildItem(i))

/** 详情/对比页按 id 取；找不到返回 undefined，由视图决定空态 */
export function findItem(id: string): GoodsItem | undefined {
  return mockItems.find((it) => it.id === id)
}

export { NOW as MOCK_NOW }
