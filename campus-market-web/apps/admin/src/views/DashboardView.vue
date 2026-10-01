<script setup lang="ts">
/**
 * A2 数据看板（原型 prototypes/admin/dashboard.html，design/03 第五章）
 *
 * <p>信息层级严格按 design/03「A2 看板信息层级」：
 *   ① KPI×4 → ② 趋势（判断走向） → ③ 构成（判断结构）
 *   → ④ TOP-N/矩阵（找具体对象） → ⑤ 待处置队列（动作入口）
 *
 * <p>数据源现在是 `mock/dashboard.ts`（后端统计接口未实现）。
 * 换成真实接口时只改本文件的数据获取，组件与图表配置不动。
 *
 * <p>四张校园牌里这里做了三张：学院×品类热力矩阵、二手循环率（面积图）、
 * 学期粒度趋势（时间粒度切换里的「学期」）。面交所热力需要校园平面底图，
 * 单独做一页更合适（放 A8 报表）。
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
  CHART_COLORS,
  GRID,
  LINE_SERIES_BASE,
  SPLIT_LINE,
  TOOLTIP
} from '@/charts/theme'
import {
  CATEGORIES,
  CATEGORY_SLICES,
  COLLEGES,
  HEATMAP_CELLS,
  KPI_ITEMS,
  pendingQueue,
  TREND,
  type RangeKey
} from '@/mock/dashboard'
import { QUEUE_TASKS } from '@/mock/review'
import { ORDERS } from '@/mock/orders'
// 循环率 / 减碳与报表共用一份 mock（mock/report.ts）——同一个指标不能有两个数
import { CARBON_ASSUMPTION, LOOP_SERIES, loopStats } from '@/mock/report'

const router = useRouter()
const range = ref<RangeKey>('7d')
const trend = computed(() => TREND[range.value])

/*
 * ⑤ 待处置队列：角标从审核队列与订单数据实时算出，不再写死。
 * 看板是「动作入口」（design/03 A2 层级⑤），点一下要能直接进处置页，
 * 否则这个模块只是装饰。
 */
const pending = computed(() =>
  pendingQueue({
    item: QUEUE_TASKS.filter((t) => t.kind === 'item').length,
    report: QUEUE_TASKS.filter((t) => t.kind === 'report').length,
    appeal: QUEUE_TASKS.filter((t) => t.kind === 'appeal').length,
    refund: ORDERS.filter((o) => o.status === 'REFUNDING' || o.status === 'DISPUTE').length
  })
)

const goQueue = (item: { route?: string }): void => {
  if (item.route) void router.push({ name: item.route })
}

/* ── ② 趋势：GMV 走面积，成交笔数走折线，双 Y 轴 ───────── */
const trendOption = computed<EChartsOption>(() => ({
  color: CHART_COLORS,
  tooltip: TOOLTIP,
  legend: { data: ['GMV(元)', '成交笔数'], top: 0, textStyle: { color: '#9A9AA4', fontSize: 12 } },
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

/* ── ③ 构成：品类分布环图，内径 60%，中心显示总计 ───────── */
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
        style: { text: '总在售件数', textAlign: 'center', fontSize: 12, fill: '#9A9AA4' }
      }
    ]
  }
})

/* ── ④ 学院 × 品类热力矩阵（校园特色之一） ─────────────── */
const heatmapOption = computed<EChartsOption>(() => ({
  tooltip: {
    ...TOOLTIP,
    formatter: (p: unknown) => {
      const d = (p as { data: [number, number, number] }).data
      return `${COLLEGES[d[1]]} · ${CATEGORIES[d[0]]}<br/>成交量 ${d[2]} 件`
    }
  },
  grid: { left: 96, right: 24, top: 16, bottom: 72 },
  xAxis: { type: 'category', data: CATEGORIES, axisLine: AXIS_LINE, axisLabel: { ...AXIS_LABEL, rotate: 30 } },
  yAxis: { type: 'category', data: COLLEGES, axisLine: AXIS_LINE, axisLabel: AXIS_LABEL },
  visualMap: {
    min: 0,
    max: 240,
    calculable: true,
    orient: 'horizontal',
    left: 'center',
    bottom: 0,
    textStyle: { fontSize: 12 }
  },
  series: [
    {
      name: '成交量',
      type: 'heatmap',
      data: HEATMAP_CELLS.map((h) => h.cell),
      emphasis: { itemStyle: { borderColor: '#1677FF', borderWidth: 1 } }
    }
  ]
}))

/* ── 校园牌：二手循环率（面积图）+ 减碳估算 ─────────────── */
/*
 * 数据与 A8 报表共用 mock/report.ts：看板的「累计循环件数 / 减碳」
 * 必须等于报表页的「循环 1,548 件 / 9,084kg」，否则就是两个真相。
 * 减碳不再是「件数 × 2.3」拍脑袋，而是报表那套品类加权系数。
 */
const loopStatsData = loopStats()

const loopOption = computed<EChartsOption>(() => ({
  color: [CHART_COLORS[1]],
  tooltip: TOOLTIP,
  grid: { ...GRID, top: 16, bottom: 24 },
  xAxis: {
    type: 'category',
    data: LOOP_SERIES.map((p) => p.month),
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL
  },
  yAxis: { type: 'value', name: '件', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  series: [
    {
      ...LINE_SERIES_BASE,
      name: '循环件数',
      type: 'line',
      data: LOOP_SERIES.map((p) => p.reused),
      areaStyle: { opacity: 0.12 }
    }
  ]
}))

/** 累计循环件数与估算减碳：数字必须配假设说明，否则站不住 */
const loopTotal = computed(() => loopStatsData.reused)
const carbonKg = computed(() => loopStatsData.carbon)
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

    <a-row :gutter="[16, 16]" class="dashboard__row">
      <!-- ② 趋势 -->
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

      <!-- ③ 构成 -->
      <a-col :span="12">
        <ChartCard title="品类分布">
          <VChart :option="categoryOption" autoresize style="height: 240px" />
        </ChartCard>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]" class="dashboard__row">
      <!-- ④ 学院 × 品类矩阵（校园特色） -->
      <a-col :span="12">
        <ChartCard title="学院 × 品类成交量矩阵" :height="360">
          <VChart :option="heatmapOption" autoresize style="height: 296px" />
        </ChartCard>
      </a-col>

      <!-- 二手循环率 + 减碳估算（假设必须可见） -->
      <a-col :span="12">
        <ChartCard title="二手循环率" :height="360">
          <template #extra>
            <span class="dashboard__loop-total">{{ loopTotal.toLocaleString() }} 件</span>
          </template>
          <VChart :option="loopOption" autoresize style="height: 200px" />
          <div class="dashboard__carbon">
            <span class="dashboard__carbon-value">≈ {{ carbonKg.toLocaleString() }} kg</span>
            <span class="dashboard__carbon-label">估算减碳</span>
          </div>
          <a-alert type="warning" :message="CARBON_ASSUMPTION" banner />
        </ChartCard>
      </a-col>
    </a-row>

    <!-- ⑤ 待处置队列 -->
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
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.dashboard__row {
  margin-top: var(--cm-space-5);
}

.dashboard__queue {
  display: flex;
  gap: var(--cm-space-6);
}

.dashboard__loop-total {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  font-variant-numeric: tabular-nums;
}

.dashboard__carbon {
  padding: var(--cm-space-2) 0;
}

.dashboard__carbon-value {
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
  color: var(--cm-color-text-1);
  font-variant-numeric: tabular-nums;
}

.dashboard__carbon-label {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}
</style>
