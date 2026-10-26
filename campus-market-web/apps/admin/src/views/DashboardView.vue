<script setup lang="ts">
/**
 * A2 数据看板（原型 prototypes/admin/dashboard.html，design/03 第五章）
 *
 * <p>信息层级严格按 design/03「A2 看板信息层级」+ 原型页脚自述的顺序：
 *   ① KPI×4 → ② 审核时效 SLA（F-A9）→ ③ 趋势（判走向）→ ④ 构成（判结构）
 *   → ⑤ TOP-N（找具体对象）→ ⑥ 待处置队列（动作入口）→ ⑦ 异常预警（校园特色）
 *
 * <p>图表数量压在 5 张（design/03 允许 3–5 张）：趋势 / 构成 / 热销品类 / 活跃学院 / SLA 近 14 日。
 * 四张校园牌（学期时间轴、学院×品类矩阵、面交所热力、二手循环率）按原型页脚收在 A8 数据报表，
 * 这里不再重复摆放——重复摆既超了图表上限，也会让「循环件数 / 减碳」这类指标出现两个出处。
 *
 * <p>数据源现在是 `mock/*.ts`（后端统计接口未实现）。换成真实接口时只改本文件的数据获取，
 * 组件与图表配置不动。
 *
 * <p>所有派生数字（队列角标、SLA 实况、预警条数）都从队列 / 商品 / 订单的同一份 mock 算出，
 * 不写死常量：否则看板说 23 单待审核、点进去队列只有 7 条，前后对不上是毕设答辩时的硬伤。
 */

import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
import KpiCard from '@/components/KpiCard.vue'
import ChartCard from '@/components/ChartCard.vue'
import {
  AXIS_LABEL,
  AXIS_LINE,
  BAR_SERIES_BASE,
  CHART_COLORS,
  GRID,
  LINE_SERIES_BASE,
  SPLIT_LINE,
  TOOLTIP
} from '@/charts/theme'
import { CATEGORY_SLICES, KPI_ITEMS, pendingQueue, TREND, type RangeKey } from '@/mock/dashboard'
import { QUEUE_TASKS } from '@/mock/review'
import { ORDERS, snapshotDrifted } from '@/mock/orders'
// TOP-N 与 A8 报表同源（成交矩阵按列/行求和），不要另造一份排名，否则两处排名不一致
import { MATRIX_CATEGORIES, MATRIX_COLLEGES, MATRIX_METRICS } from '@/mock/report'
import { DEFAULT_SLA } from '@/mock/settings'
import { SLA_DAILY, slaSummary, targetMinutes } from '@/mock/sla'
import { ITEMS } from '@/mock/items'
import { hoursBetween } from '@/mock/now'
import { isPriceAbnormal, slaLevel, type SlaLevel } from '@campus/shared'

const router = useRouter()
const range = ref<RangeKey>('7d')
const trend = computed(() => TREND[range.value])

/*
 * ⑥ 待处置队列：角标从审核队列与订单数据实时算出，不再写死。
 * 看板是「动作入口」（design/03 A2 层级⑤），点一下要能直接进处置页，
 * 否则这个模块只是装饰。
 */
const pending = computed(() =>
  pendingQueue({
    item: QUEUE_TASKS.filter((t) => t.kind === 'item').length,
    report: QUEUE_TASKS.filter((t) => t.kind === 'report').length,
    appeal: QUEUE_TASKS.filter((t) => t.kind === 'appeal').length,
    refund: ORDERS.filter((o) => o.status === 'REFUNDING' || o.status === 'DISPUTING').length
  })
)

const goQueue = (item: { route?: string }): void => {
  if (item.route) void router.push({ name: item.route })
}

/* ── ③ 趋势：GMV 走面积，成交笔数走折线，双 Y 轴 ───────── */
const trendOption = computed<EChartsOption>(() => ({
  color: CHART_COLORS,
  tooltip: TOOLTIP,
  legend: { data: ['GMV(元)', '成交笔数'], top: 0, textStyle: { color: '#8E8E93', fontSize: 12 } },
  grid: GRID,
  xAxis: {
    type: 'category',
    data: trend.value.labels,
    boundaryGap: false,
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL
  },
  yAxis: [
    { type: 'value', name: '元', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
    { type: 'value', name: '笔', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: { show: false } }
  ],
  series: [
    {
      ...LINE_SERIES_BASE,
      name: 'GMV(元)',
      type: 'line',
      data: trend.value.gmv,
      areaStyle: { opacity: 0.12 }
    },
    {
      ...LINE_SERIES_BASE,
      name: '成交笔数',
      type: 'line',
      yAxisIndex: 1,
      data: trend.value.orders
    }
  ]
}))

/* ── ④ 构成：品类分布环图，内径 60%，中心显示总计 ───────── */
const categoryOption = computed<EChartsOption>(() => {
  const total = CATEGORY_SLICES.reduce((s, i) => s + i.value, 0)
  return {
    color: CHART_COLORS,
    tooltip: { ...TOOLTIP, trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
    legend: { type: 'scroll', orient: 'vertical', right: 0, top: 'middle', textStyle: { fontSize: 12 } },
    series: [
      {
        name: '品类分布',
        type: 'pie',
        radius: ['60%', '82%'],
        center: ['38%', '50%'],
        avoidLabelOverlap: true,
        label: { show: false },
        data: CATEGORY_SLICES
      }
    ],
    graphic: [
      {
        type: 'text',
        left: '38%',
        top: '46%',
        style: { text: String(total), textAlign: 'center', fontSize: 24, fontWeight: 600, fill: '#1F1F26' }
      },
      {
        type: 'text',
        left: '38%',
        top: '58%',
        style: { text: '总在售件数', textAlign: 'center', fontSize: 12, fill: '#8E8E93' }
      }
    ]
  }
})

/* ── ⑤ TOP-N 明细：横向柱，yAxis 类目 + inverse 让最大值在顶部 ───── */
const deal = MATRIX_METRICS.deal

const hotCategories = MATRIX_CATEGORIES.map((name, j) => ({
  name,
  value: deal.reduce((sum, row) => sum + row[j], 0)
})).sort((a, b) => b.value - a.value)

const activeColleges = MATRIX_COLLEGES.map((name, i) => ({
  name,
  value: deal[i].reduce((sum, v) => sum + v, 0)
}))
  .sort((a, b) => b.value - a.value)
  .slice(0, 10)

const topOption = (names: string[], values: number[], color: string): EChartsOption => ({
  tooltip: { ...TOOLTIP, trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 32, top: 8, bottom: 8, containLabel: true },
  xAxis: { type: 'value', axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  yAxis: {
    type: 'category',
    data: names,
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    inverse: true
  },
  series: [
    {
      type: 'bar',
      data: values,
      barMaxWidth: 14,
      itemStyle: { borderRadius: [0, 6, 6, 0], color }
    }
  ]
})

const hotCategoryOption = topOption(
  hotCategories.map((c) => c.name),
  hotCategories.map((c) => c.value),
  CHART_COLORS[0]
)

const activeCollegeOption = topOption(
  activeColleges.map((c) => c.name),
  activeColleges.map((c) => c.value),
  CHART_COLORS[2]
)

/* ── ② 审核时效 SLA（F-A9）：阈值来自设置页，历史曲线 + 实况各取一路 ── */
const sla = DEFAULT_SLA
/** 目标线的分钟数：阈值可在设置页调，所以从配置算，不写死 120 */
const targetMin = targetMinutes(sla)

/*
 * 积压 / 申诉临期是「现在还没处理完」的量，只能从实时队列算；
 * 平均首审、达标率、P95 是「已处置」的统计，走历史序列 slaSummary()。
 * 两者口径不同，不要互相推导——混用会让看板的达标率永远算不对。
 */
const waitLevel = (submitAt: string): SlaLevel =>
  slaLevel(Math.max(0, hoursBetween(submitAt)), sla)

const backlog = computed(() => QUEUE_TASKS.filter((t) => waitLevel(t.submitAt) === 'overdue').length)

const appealDue = computed(
  () =>
    QUEUE_TASKS.filter(
      (t) => t.kind === 'appeal' && hoursBetween(t.submitAt) >= sla.appealHours - 6
    ).length
)

const slaData = computed(() => slaSummary())

const slaMetrics = computed(() => {
  const s = slaData.value
  const p95Hour = Math.round((s.p95Min / 60) * 100) / 100
  const p95Text = p95Hour.toFixed(1)
  return [
    {
      key: 'avg',
      label: '平均首审时长',
      value: String(s.avgMin),
      unit: '分钟',
      target: `目标 ≤ ${targetMin} 分钟`,
      alert: s.avgMin > targetMin
    },
    {
      key: 'rate',
      label: 'SLA 达标率',
      value: s.okRate.toFixed(1),
      unit: '%',
      target: `目标 ≥ ${sla.targetRate}%`,
      alert: s.okRate < sla.targetRate
    },
    {
      key: 'p95',
      label: 'P95 首审时长',
      value: `≈ ${p95Text}`,
      unit: '小时',
      target: `目标 ≤ ${sla.p95Hours} 小时`,
      alert: p95Hour > sla.p95Hours
    },
    {
      key: 'backlog',
      label: `超时积压（>${sla.backlogHours}h）`,
      value: String(backlog.value),
      unit: '件',
      target: '需当日清零',
      alert: backlog.value > 0
    },
    {
      key: 'handle',
      label: '单条平均处置',
      value: String(s.handleSec),
      unit: '秒',
      target: `目标 ≤ ${sla.avgHandleSeconds} 秒`,
      alert: s.handleSec > sla.avgHandleSeconds
    }
  ]
})

/* 近 14 日柱状：超目标线的那天标红——全绿的 SLA 图对运营没有价值 */
const slaOption = computed<EChartsOption>(() => ({
  tooltip: {
    ...TOOLTIP,
    formatter: (p: unknown) => {
      const first = (p as Array<{ name: string; value: number }>)[0]
      const over = first.value > targetMin ? '，超目标线' : ''
      return `${first.name}<br/>平均首审 ${first.value} 分钟${over}`
    }
  },
  grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    data: SLA_DAILY.map((d) => d.day),
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false }
  },
  yAxis: { type: 'value', name: '分钟', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  series: [
    {
      ...BAR_SERIES_BASE,
      type: 'bar',
      data: SLA_DAILY.map((d) => ({
        value: d.avgMin,
        itemStyle: {
          color: d.avgMin > targetMin ? CHART_COLORS[5] : CHART_COLORS[0],
          borderRadius: [6, 6, 0, 0] as number[]
        }
      })),
      markLine: {
        silent: true,
        symbol: 'none',
        /* 目标线用中灰（比网格线 #E8E8ED 深一档），既不抢戏也看得清 */
        lineStyle: { type: 'dashed', color: '#8E8E93' },
        label: {
          formatter: `目标 ${targetMin}min`,
          position: 'insideEndTop',
          fontSize: 11,
          color: '#8E8E93'
        },
        data: [{ yAxis: targetMin }]
      }
    }
  ]
}))

/* ── ⑦ 异常预警：四条口径全部从商品 / 订单 / 队列派生，点得进处置页 ── */
/*
 * 口径与 design/03 A2 层级⑥「异常交易、刷量、价格异常」对齐，
 * 再加一条原型里的「申诉临期」（F-A9：申诉另承诺 48 小时）。
 */
interface AlertItem {
  key: string
  level: 'error' | 'warn'
  label: string
  desc: string
  count: number
  route: string
}

/** 疑似刷量：同一账号 60 分钟内连续发布 ≥ 3 件（窗口与阈值写在注释里，避免魔法值散落） */
const BRUSH_WINDOW_MIN = 60
const BRUSH_MIN_COUNT = 3

const brushCount = computed(() => {
  const bySeller = new Map<string, number[]>()
  for (const item of ITEMS) {
    const list = bySeller.get(item.sellerId) ?? []
    list.push(new Date(item.publishAt).getTime())
    bySeller.set(item.sellerId, list)
  }

  let sellers = 0
  for (const times of bySeller.values()) {
    const sorted = [...times].sort((a, b) => a - b)
    const span = BRUSH_WINDOW_MIN * 60_000
    for (let head = 0; head + BRUSH_MIN_COUNT - 1 < sorted.length; ) {
      if (sorted[head + BRUSH_MIN_COUNT - 1] - sorted[head] <= span) {
        sellers += 1
        break
      }
      head += 1
    }
  }
  return sellers
})

const alerts = computed<AlertItem[]>(() => [
  {
    key: 'trade',
    level: 'error',
    label: '异常交易',
    desc: '纠纷 / 退款中订单，或成交快照已被卖家改动',
    count: ORDERS.filter(
      (o) => o.status === 'DISPUTING' || o.status === 'REFUNDING' || snapshotDrifted(o)
    ).length,
    route: 'orders'
  },
  {
    key: 'brush',
    level: 'warn',
    label: '疑似刷量',
    desc: `同一账号 ${BRUSH_WINDOW_MIN} 分钟内连续发布 ≥ ${BRUSH_MIN_COUNT} 件`,
    count: brushCount.value,
    route: 'users'
  },
  {
    key: 'price',
    level: 'error',
    label: '价格异常',
    desc: '标价偏离同类目成交均价 3 倍以上（在售）',
    count: ITEMS.filter((i) => i.status === 'ON_SALE' && isPriceAbnormal(i)).length,
    route: 'items'
  },
  {
    key: 'appeal',
    level: 'warn',
    label: '申诉临期',
    desc: `距 ${sla.appealHours} 小时复审承诺不足 6 小时`,
    count: appealDue.value,
    route: 'review'
  }
])
</script>

<template>
  <div class="dashboard cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">数据看板</h1>
      <p class="cm-page__sub">全校交易概况，指标按角色权限下发</p>
    </header>

    <!-- ① KPI×4 -->
    <a-row :gutter="[16, 16]">
      <a-col v-for="kpi in KPI_ITEMS" :key="kpi.key" :span="6">
        <KpiCard
          :title="kpi.title"
          :value="kpi.value"
          :exact="kpi.exact"
          :trend="kpi.trend"
        />
      </a-col>
    </a-row>

    <!-- ② 审核时效 SLA（F-A9） -->
    <a-row class="dashboard__row">
      <a-col :span="24">
        <div class="cm-card">
          <div class="cm-card__title">审核时效 SLA</div>
          <div class="cm-card__body">
            <div class="dashboard__sla">
              <div
                v-for="m in slaMetrics"
                :key="m.key"
                class="dashboard__sla-item"
                :class="{ 'is-alert': m.alert }"
              >
                <div class="dashboard__sla-label">{{ m.label }}</div>
                <div class="dashboard__sla-value">
                  {{ m.value }}<small>{{ m.unit }}</small>
                </div>
                <div class="dashboard__sla-target">{{ m.target }}</div>
              </div>
            </div>
            <div class="dashboard__sla-cap">近 14 日平均首审时长（分钟）</div>
            <VChart :option="slaOption" autoresize style="height: 180px" />
            <p class="dashboard__sla-foot">
              口径：从商品进入队列 → 首次处置完成；首审响应 ≤ {{ sla.firstResponseHours }} 小时为达标，
              P95 ≤ {{ sla.p95Hours }} 小时，达标率 ≥ {{ sla.targetRate }}%，
              超 {{ sla.backlogHours }} 小时未处置记为积压红牌、需当日清零。
              单条平均处置 ≤ {{ sla.avgHandleSeconds }} 秒是操作效率指标，与响应时效是两个层级，不可混用；
              申诉另承诺 {{ sla.appealHours }} 小时内给出复审结论。阈值可在「系统设置 · SLA 配置」调整。
            </p>
          </div>
        </div>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" class="dashboard__row">
      <!-- ③ 趋势 -->
      <a-col :span="12">
        <ChartCard title="交易趋势">
          <template #extra>
            <a-radio-group v-model:value="range" size="small">
              <a-radio-button value="7d">近 7 日</a-radio-button>
              <a-radio-button value="30d">近 30 日</a-radio-button>
              <a-radio-button value="term">学期</a-radio-button>
            </a-radio-group>
          </template>
          <VChart :option="trendOption" autoresize style="height: 240px" />
        </ChartCard>
      </a-col>

      <!-- ④ 构成 -->
      <a-col :span="12">
        <ChartCard title="品类分布">
          <VChart :option="categoryOption" autoresize style="height: 240px" />
        </ChartCard>
      </a-col>
    </a-row>

    <!-- ⑤ TOP-N 明细：找具体对象 -->
    <a-row :gutter="[16, 16]" class="dashboard__row">
      <a-col :span="12">
        <ChartCard title="热销品类 TOP">
          <VChart :option="hotCategoryOption" autoresize style="height: 240px" />
        </ChartCard>
      </a-col>
      <a-col :span="12">
        <ChartCard title="活跃学院 TOP10">
          <VChart :option="activeCollegeOption" autoresize style="height: 240px" />
        </ChartCard>
      </a-col>
    </a-row>

    <!-- ⑥ 待处置队列 -->
    <a-row class="dashboard__row">
      <a-col :span="24">
        <div class="cm-card">
          <div class="cm-card__title">待处置队列</div>
          <div class="cm-card__body dashboard__queue">
            <a-badge
              v-for="item in pending"
              :key="item.key"
              :count="item.count"
              :overflow-count="99"
            >
              <a-button shape="round" @click="goQueue(item)">{{ item.label }}</a-button>
            </a-badge>
            <span class="dashboard__queue-note">按权重排序，非按时间</span>
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- ⑦ 异常预警 -->
    <a-row class="dashboard__row">
      <a-col :span="24">
        <div class="cm-card">
          <div class="cm-card__title">异常预警</div>
          <div class="cm-card__body dashboard__alerts">
            <div
              v-for="a in alerts"
              :key="a.key"
              class="dashboard__alert"
              :class="[`is-${a.level}`, { 'is-ok': a.count === 0 }]"
            >
              <span class="dashboard__alert-tag">{{ a.label }}</span>
              <span class="dashboard__alert-desc">{{ a.desc }}</span>
              <span class="dashboard__alert-count">{{ a.count > 0 ? `${a.count} 条` : '正常' }}</span>
              <a-button type="link" size="small" @click="goQueue(a)">查看</a-button>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>

    <!-- 本页说明：论文里要解释图表选型，先在这里写清楚 -->
    <div class="cm-card dashboard__note">
      <div class="cm-card__body">
        <b>本页说明</b>
        信息层级严格按「KPI → SLA → 趋势 → 构成 → TOP-N → 待处置 → 异常」排列，共 5 张图表，
        达到设计规范上限（3–5 张）；其余校园专题图（学期时间轴 / 学院×品类矩阵 / 面交所热力 /
        二手循环率）收在「数据报表 A8」。数据为 mock 演示值；上升用红、下降用绿（中国股市惯例），
        且均配 ▲▼ 符号，不靠颜色单独表意。
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard__row {
  margin-top: var(--cm-space-5);
}

/* ── ② SLA 卡 ──────────────────────────────────── */
.dashboard__sla {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--cm-space-4);
}

.dashboard__sla-item {
  padding: var(--cm-space-4);
  background: var(--cm-color-surface-muted);
  border-radius: var(--cm-radius-md);
}

.dashboard__sla-item.is-alert {
  background: color-mix(in srgb, var(--cm-color-red-500) 8%, var(--cm-color-surface));
  border: 1px solid color-mix(in srgb, var(--cm-color-red-500) 30%, transparent);
}

.dashboard__sla-label {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.dashboard__sla-value {
  margin-top: var(--cm-space-2);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--cm-color-text-1);
}

.dashboard__sla-value small {
  margin-left: 2px;
  font-size: var(--cm-font-size-sm);
  font-weight: 400;
  color: var(--cm-color-text-3);
}

.dashboard__sla-target {
  margin-top: var(--cm-space-1);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.dashboard__sla-cap {
  margin: var(--cm-space-5) 0 var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.dashboard__sla-foot {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.8;
  color: var(--cm-color-text-3);
}

/* ── ⑥ 待处置队列 ──────────────────────────────── */
.dashboard__queue {
  display: flex;
  align-items: center;
  gap: var(--cm-space-6);
}

.dashboard__queue-note {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* ── ⑦ 异常预警 ────────────────────────────────── */
.dashboard__alerts {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.dashboard__alert {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  padding: var(--cm-space-3) var(--cm-space-4);
  border-radius: var(--cm-radius-md);
  border: 1px solid transparent;
  font-size: var(--cm-font-size-sm);
}

.dashboard__alert.is-error {
  background: color-mix(in srgb, var(--cm-color-red-500) 8%, var(--cm-color-surface));
  border-color: color-mix(in srgb, var(--cm-color-red-500) 24%, transparent);
}

/* 计数为 0 时降级为中性灰：橙色空告警会让运营误以为出了问题 */
.dashboard__alert.is-ok {
  background: var(--cm-color-surface-muted);
  border-color: var(--cm-color-border);
}

.dashboard__alert.is-warn {
  background: color-mix(in srgb, var(--cm-color-orange-500) 10%, var(--cm-color-surface));
  border-color: color-mix(in srgb, var(--cm-color-orange-500) 26%, transparent);
}

.dashboard__alert-tag {
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.dashboard__alert-desc {
  color: var(--cm-color-text-2);
}

.dashboard__alert-count {
  margin-left: auto;
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.dashboard__note {
  margin-top: var(--cm-space-5);
}

.dashboard__note .cm-card__body {
  font-size: var(--cm-font-size-xs);
  line-height: 1.8;
  color: var(--cm-color-text-3);
}

.dashboard__note b {
  color: var(--cm-color-text-2);
  margin-right: var(--cm-space-2);
}
</style>
