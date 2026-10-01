/**
 * A2 看板 mock 数据
 *
 * <p>后端统计接口（/admin/dashboard/*）还没写，先用本地固定数据把
 * 图表与排版跑通。数值是**写死的确定性数据**，不用 Math.random——
 * 否则每次刷新截图都不一样，论文里贴的图对不上，也让 UI 问题难以复现。
 *
 * <p>后端接口就绪后：删掉本文件，改为 `packages/api` 里由 OpenAPI 生成的
 * `dashboardApi`，视图层只换数据源，不改组件。
 */

export type RangeKey = '7d' | '30d' | 'term'

export interface KpiItem {
  key: string
  title: string
  /** 展示值：超过 1 万缩写，精确值放 exact 供 title 悬浮 */
  value: string
  exact: string
  trend: { ratio: number; label: string }
}

export interface TrendData {
  labels: string[]
  gmv: number[]
  orders: number[]
}

export interface CategorySlice {
  name: string
  value: number
}

export interface HeatCell {
  /** [品类下标, 学院下标, 成交量] —— ECharts heatmap 的三元组格式 */
  cell: [number, number, number]
}

export interface LoopPoint {
  month: string
  /** 循环件数 */
  reused: number
}

export interface PendingItem {
  key: string
  label: string
  count: number
  /**
   * 跳转的路由名（审核队列 / 订单管理已建好，所以这里不再是占位）。
   * 角标数字由 `pendingQueue()` 从真实队列数据算出，不写死——
   * 否则看板显示 23 单待审核、点进去队列里只有 7 条，前后对不上。
   */
  route?: string
}

/** 品类（列）—— 与业务字典 dict_category 同名，后端就绪后从 /api/dict/all 取 */
export const CATEGORIES = [
  '教材教辅',
  '数码电子',
  '生活用品',
  '服饰鞋包',
  '运动器材',
  '自行车',
  '美妆个护',
  '票务卡券',
  '其他'
]

/** 学院（行）—— 12 个学院取前 10 做矩阵，报表页可全量 */
export const COLLEGES = [
  '计算机学院',
  '机械学院',
  '经管学院',
  '外国语学院',
  '材料学院',
  '化工学院',
  '土木学院',
  '文法学院',
  '艺术学院',
  '医学院'
]

export const KPI_ITEMS: KpiItem[] = [
  { key: 'gmv', title: '今日 GMV', value: '¥ 8.6 万', exact: '¥ 86,412.50', trend: { ratio: 12.3, label: '较昨日' } },
  { key: 'items', title: '新增商品', value: '328 件', exact: '328 件', trend: { ratio: 5.6, label: '较昨日' } },
  { key: 'orders', title: '成交笔数', value: '142 笔', exact: '142 笔', trend: { ratio: -3.2, label: '较昨日' } },
  { key: 'users', title: '活跃用户', value: '1,864 人', exact: '1,864 人', trend: { ratio: 8.1, label: '较昨日' } }
]

/** 确定性伪随机：同一 seed 永远同一串数，截图可复现 */
function lcg(seed: number): () => number {
  let s = seed
  return () => {
    s = (s * 1103515245 + 12345) % 2147483648
    return s / 2147483648
  }
}

const rand = lcg(20260928)

function buildTrend(days: number, base: number): TrendData {
  const labels: string[] = []
  const gmv: number[] = []
  const orders: number[] = []
  const start = new Date('2026-09-27T00:00:00')
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(start.getTime() - i * 86400_000)
    labels.push(`${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`)
    // 周末抬升：校园二手在周末更活跃，这条规律要能从图上看出来
    const weekend = d.getDay() === 0 || d.getDay() === 6 ? 1.35 : 1
    const v = Math.round(base * weekend * (0.7 + rand() * 0.6))
    gmv.push(v)
    orders.push(Math.round(v / 620))
  }
  return { labels, gmv, orders }
}

/** 学期粒度：按月聚合，标签 YYYY-MM */
const TERM: TrendData = {
  labels: ['2026-03', '2026-04', '2026-05', '2026-06', '2026-07', '2026-08'],
  gmv: [512_000, 634_000, 705_000, 826_000, 418_000, 596_000],
  orders: [826, 1_022, 1_137, 1_332, 674, 961]
}

export const TREND: Record<RangeKey, TrendData> = {
  '7d': buildTrend(7, 86_000),
  '30d': buildTrend(30, 74_000),
  term: TERM
}

/** 品类分布环图 */
export const CATEGORY_SLICES: CategorySlice[] = [
  { name: '教材教辅', value: 1240 },
  { name: '数码电子', value: 860 },
  { name: '生活用品', value: 720 },
  { name: '服饰鞋包', value: 540 },
  { name: '运动器材', value: 380 },
  { name: '自行车', value: 260 },
  { name: '美妆个护', value: 210 },
  { name: '票务卡券', value: 150 },
  { name: '其他', value: 190 }
]

/** 学院 × 品类热力矩阵：三元组 [品类下标, 学院下标, 成交量] */
export const HEATMAP_CELLS: HeatCell[] = (() => {
  const cells: HeatCell[] = []
  for (let c = 0; c < CATEGORIES.length; c++) {
    for (let r = 0; r < COLLEGES.length; r++) {
      // 计算机/经管买数码多，医学院教材多：让矩阵看得出结构而不是一团噪声
      const bias = (c === 1 && (r === 0 || r === 2)) || (c === 0 && r === 9) ? 2.4 : 1
      cells.push({ cell: [c, r, Math.round(20 + rand() * 90 * bias)] })
    }
  }
  return cells
})()

/** 二手循环率：按月循环件数 */
export const LOOP_POINTS: LoopPoint[] = [
  { month: '2026-03', reused: 412 },
  { month: '2026-04', reused: 508 },
  { month: '2026-05', reused: 566 },
  { month: '2026-06', reused: 664 },
  { month: '2026-07', reused: 336 },
  { month: '2026-08', reused: 478 }
]

/**
 * 减碳估算系数与假设说明。
 * 「必须显示估算假设」是已拍板项，UI 上不能省——否则论文里这个数字站不住。
 */
export const CARBON_ASSUMPTION = '按每件二手商品平均减碳 2.3kg 估算（参考二手 3C 与教材类目均值，非实测）'

/**
 * 待处置队列：数字**从审核队列与订单的 mock 里实时算出**，不写死。
 * 看板说「23 单待审核」、点进去队列只有 7 条，是最伤可信度的一类 bug，
 * 所以在数据层就把两者绑在一起（后端就绪后同理：看板与队列同源于一张统计表）。
 *
 * <p>注意这里用的是当前**未处置**的队列长度，审核页处置掉一条后回到看板数字会变小——
 * 这正是要的效果。
 */
export const pendingQueue = (source: {
  item: number
  report: number
  appeal: number
  refund: number
}): PendingItem[] => [
  { key: 'review', label: '待审核商品', count: source.item, route: 'review' },
  { key: 'report', label: '举报待处理', count: source.report, route: 'review' },
  { key: 'appeal', label: '申诉待复核', count: source.appeal, route: 'review' },
  { key: 'refund', label: '退款待审', count: source.refund, route: 'orders' }
]
