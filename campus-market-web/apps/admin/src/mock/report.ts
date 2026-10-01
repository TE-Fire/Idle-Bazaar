/**
 * A8 数据报表 · 四张校园独有可视化的 mock 数据（business/03 第五章）
 *
 * <p>这四张图是本毕设相对同类设计的**差异化亮点**，口径已在前置文档里定死，
 * 所以这里的数据结构严格按「图 → 维度」来组织，不要为了画图方便改形状。
 *
 * <p>数值同样是确定性的（不用 Math.random）：论文要贴图，刷新不能变。
 */

/* ── 图 1 · 学期时间轴对比 ── */

/** 18 个校历周 */
export const WEEKS = Array.from({ length: 18 }, (_, i) => `W${i + 1}`)

/**
 * 发布量 / 成交量：三个峰值对应三种校园场景
 * ① 学期初 W2–W3（教材教辅）② 期中 W9–W10（小电器、生活用品）
 * ③ 期末 W16–W17（笔记资料）；成交量滞后发布约一周。
 */
export const TERM_SERIES: Record<string, { publish: number[]; deal: number[] }> = {
  '2026春': {
    publish: [86, 214, 232, 158, 121, 104, 97, 118, 176, 189, 132, 110, 96, 102, 128, 208, 226, 141],
    deal: [52, 96, 168, 186, 142, 108, 92, 88, 104, 152, 171, 138, 104, 91, 98, 126, 192, 203]
  },
  '2026秋': {
    publish: [94, 238, 251, 172, 133, 112, 105, 126, 191, 203, 145, 121, 104, 111, 139, 226, 244, 152],
    deal: [58, 104, 182, 201, 154, 117, 99, 95, 112, 164, 185, 149, 112, 98, 106, 136, 207, 219]
  }
}

/* ── 图 2 · 学院 × 品类热力矩阵 ── */

/** 行 = 12 个学院，与 dict_college 同名同序 */
export const MATRIX_COLLEGES = [
  '计算机学院',
  '电子信息学院',
  '机械工程学院',
  '材料学院',
  '化学化工学院',
  '数学学院',
  '物理学院',
  '经济管理学院',
  '外国语学院',
  '艺术学院',
  '法学院',
  '医学院'
]

/** 列 = 8 个一级品类 */
export const MATRIX_CATEGORIES = [
  '教材教辅',
  '数码电子',
  '生活用品',
  '服饰鞋包',
  '运动户外',
  '图书笔记',
  '乐器',
  '其他闲置'
]

/**
 * 三种度量各一份矩阵（成交量 / 发布量 / 成交率）
 *
 * <p>数据里埋了三条「数据故事」，论文里可以直接讲：
 *   ① 计算机学院 × 数码电子 最高（出数码）；
 *   ② 艺术学院 × 乐器 突出；
 *   ③ 教材教辅在**所有学院**都高（大四集中出教材）。
 */
const DEAL: number[][] = [
  [42, 96, 18, 12, 9, 26, 4, 11],
  [38, 71, 22, 14, 11, 24, 6, 13],
  [31, 34, 47, 16, 26, 21, 3, 18],
  [28, 29, 42, 18, 21, 19, 5, 16],
  [33, 31, 38, 15, 18, 23, 4, 14],
  [46, 44, 21, 13, 12, 38, 5, 12],
  [41, 39, 24, 12, 14, 33, 6, 11],
  [52, 36, 33, 31, 17, 29, 8, 19],
  [48, 28, 29, 36, 13, 31, 11, 17],
  [36, 33, 27, 42, 15, 22, 58, 21],
  [49, 30, 24, 26, 14, 34, 7, 15],
  [44, 27, 31, 19, 12, 36, 5, 13]
]

const PUBLISH: number[][] = DEAL.map((row) => row.map((v) => Math.round(v * 1.9)))

/** 三种度量共用一套下标：成交率由「成交量 ÷ 发布量」算出，不另造一份数，避免口径打架 */
export const MATRIX_METRICS: Record<'deal' | 'publish' | 'rate', number[][]> = {
  deal: DEAL,
  publish: PUBLISH,
  rate: DEAL.map((row, i) => row.map((v, j) => Math.round((v / PUBLISH[i][j]) * 100)))
}

export const MATRIX_METRIC_LABELS = {
  deal: '成交量',
  publish: '发布量',
  rate: '成交率'
} as const

/* ── 图 3 · 校内面交所热力 ── */

/** 行 = 面交点（取启用中的 6 个），列 = 时段 */
export const HEAT_POINTS = [
  '一食堂门口',
  '图书馆东门',
  '体育馆南侧',
  '三舍架空层',
  '教学楼中庭',
  '快递驿站旁'
]

export const HEAT_SLOTS = ['早 7–9', '上午 9–12', '中午 12–14', '下午 14–18', '晚 18–22']

/**
 * 交易笔数 × 时段：靠 peak 判断哪个点过载
 * 食堂门口中午爆满、图书馆晚上多、快递驿站下午多——这是能反推「是否要增设面交点」的数据。
 */
export const HEAT_DATA: number[][] = [
  [18, 26, 96, 41, 33],
  [9, 34, 28, 46, 88],
  [22, 11, 14, 38, 52],
  [31, 18, 24, 29, 61],
  [7, 42, 21, 57, 19],
  [12, 29, 47, 73, 26]
]

/* ── 图 4 · 二手循环率与减碳 ── */

export interface LoopPoint {
  month: string
  /** 发布件数 */
  published: number
  /** 成交（循环）件数 */
  reused: number
}

export const LOOP_SERIES: LoopPoint[] = [
  { month: '3月', published: 428, reused: 186 },
  { month: '4月', published: 512, reused: 231 },
  { month: '5月', published: 476, reused: 224 },
  { month: '6月', published: 693, reused: 352 },
  { month: '7月', published: 318, reused: 141 },
  { month: '8月', published: 262, reused: 118 },
  { month: '9月', published: 587, reused: 296 }
]

/**
 * 减碳估算系数（kg/件）——business/03 §五 明确要求**标注为假设值**，非实测。
 * 页面必须带警告条与「系数与假设」弹窗，论文中原样标注，不得作为精确结论。
 */
export const CARBON_FACTORS: { name: string; kg: number; share: number }[] = [
  { name: '教材教辅', kg: 0.8, share: 0.26 },
  { name: '数码电子', kg: 25, share: 0.14 },
  { name: '服饰鞋包', kg: 3, share: 0.12 },
  { name: '生活用品', kg: 5, share: 0.28 },
  { name: '其他', kg: 2, share: 0.2 }
]

/** 循环率与减碳量都从 LOOP_SERIES 派生，保证两张卡与面积图口径一致 */
export const loopStats = () => {
  const published = LOOP_SERIES.reduce((s, p) => s + p.published, 0)
  const reused = LOOP_SERIES.reduce((s, p) => s + p.reused, 0)
  const rate = (reused / published) * 100
  const carbon = CARBON_FACTORS.reduce((s, f) => s + reused * f.share * f.kg, 0)
  return {
    published,
    reused,
    rate: Math.round(rate * 10) / 10,
    carbon: Math.round(carbon)
  }
}

/**
 * 减碳假设说明——**看板与报表共用这一句**。
 * 之前看板写「2.3kg/件」、报表按品类系数加权，同一个指标两套口径两个数字，
 * 答辩时一眼就会被抓。现在两边都引用 loopStats() 与本条文案，只有一个真相来源。
 */
export const CARBON_ASSUMPTION =
  '减碳按品类系数加权估算（教材 0.8 / 数码 25 / 服饰 3 / 生活 5 / 其他 2 kg/件）；' +
  '系数为文献估值非实测，未计入物流与包装，不作为精确结论。'
