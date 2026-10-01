<script setup lang="ts">
/**
 * A8 数据报表 · 四张校园独有可视化（business/03 第五章）
 *
 * <p>这四张图是本毕设的差异化亮点，每张都对应一个能讲出来的「数据故事」：
 *   ① 学期时间轴 —— 三个峰值：学期初出教材、期末出笔记、毕业季出大件；
 *   ② 学院 × 品类 —— 计算机出数码、艺术出乐器、教材在所有学院都高；
 *   ③ 面交所热力 —— 哪个点在哪个时段过载，可反推是否要增设面交点；
 *   ④ 循环率 —— 循环经济 / ESG 角度，答辩最容易出挑。
 *
 * <p>⚠ 减碳系数是**估算假设**（business/03 已决：必须在论文中标注为假设值），
 * 所以页面带警告条 + 「系数与假设」弹窗，不把它呈现为精确结论。
 */

import { computed, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
import { Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  AXIS_LABEL,
  AXIS_LINE,
  LINE_SERIES_BASE,
  CHART_COLORS,
  GRID,
  SPLIT_LINE,
  TOOLTIP
} from '@/charts/theme'
import ChartCard from '@/components/ChartCard.vue'
import {
  CARBON_FACTORS,
  HEAT_DATA,
  HEAT_POINTS,
  HEAT_SLOTS,
  LOOP_SERIES,
  MATRIX_CATEGORIES,
  MATRIX_COLLEGES,
  MATRIX_METRICS,
  MATRIX_METRIC_LABELS,
  TERM_SERIES,
  WEEKS,
  loopStats
} from '@/mock/report'
import { DEFAULT_SLA } from '@/mock/settings'
import { reviewerSlaRows, slaSummary, targetMinutes, type ReviewerSlaRow } from '@/mock/sla'

/* ── 图 1 · 学期时间轴 ── */

const term = ref<'2026春' | '2026秋'>('2026春')

const termOption = computed<EChartsOption>(() => {
  const s = TERM_SERIES[term.value]
  return {
    color: [CHART_COLORS[0], CHART_COLORS[1]],
    tooltip: { ...TOOLTIP, trigger: 'axis' },
    legend: { data: ['发布量', '成交量'], right: 0, top: 0, textStyle: { color: '#6E6E73' } },
    grid: GRID,
    xAxis: {
      type: 'category',
      data: WEEKS,
      axisLine: AXIS_LINE,
      axisLabel: AXIS_LABEL,
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLabel: AXIS_LABEL,
      splitLine: SPLIT_LINE
    },
    series: [
      {
        name: '发布量',
        type: 'line',
        // smooth 固定 false：数据要精确，不做平滑（theme.ts 的约定）
        ...LINE_SERIES_BASE,
        data: s.publish,
        areaStyle: { opacity: 0.12 }
      },
      {
        name: '成交量',
        type: 'line',
        ...LINE_SERIES_BASE,
        data: s.deal,
        areaStyle: { opacity: 0.12 }
      }
    ]
  }
})

/* ── 图 2 · 学院 × 品类热力矩阵 ── */

type MetricKey = 'deal' | 'publish' | 'rate'
const metric = ref<MetricKey>('deal')

/** heatmap 的三元组：[列下标(品类), 行下标(学院), 值] —— 注意 x 在前 */
const matrixCells = computed<[number, number, number][]>(() => {
  const data = MATRIX_METRICS[metric.value]
  const cells: [number, number, number][] = []
  data.forEach((row, ci) => row.forEach((v, xi) => cells.push([xi, ci, v])))
  return cells
})

const matrixMax = computed(() => Math.max(...matrixCells.value.map((c) => c[2])))

const matrixOption = computed<EChartsOption>(() => ({
  tooltip: {
    ...TOOLTIP,
    formatter: (p: unknown) => {
      const d = (p as { data: [number, number, number] }).data
      const unit = metric.value === 'rate' ? '%' : ' 件'
      return `${MATRIX_CATEGORIES[d[0]]} · ${MATRIX_COLLEGES[d[1]]}<br/>${MATRIX_METRIC_LABELS[metric.value]}：<b>${d[2]}${unit}</b>`
    }
  },
  grid: { left: 96, right: 24, top: 16, bottom: 72 },
  xAxis: {
    type: 'category',
    data: MATRIX_CATEGORIES,
    axisLabel: { ...AXIS_LABEL, rotate: 30 },
    axisTick: { show: false },
    splitArea: { show: true }
  },
  yAxis: {
    type: 'category',
    data: MATRIX_COLLEGES,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    splitArea: { show: true }
  },
  visualMap: {
    min: 0,
    max: matrixMax.value,
    calculable: true,
    orient: 'horizontal',
    left: 'center',
    bottom: 0,
    itemWidth: 12,
    itemHeight: 90,
    inRange: { color: ['#F2F7FD', '#B5D4F4', '#378ADD', '#185FA5', '#0C447C'] }
  },
  series: [
    {
      type: 'heatmap',
      data: matrixCells.value,
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 1 },
      emphasis: { itemStyle: { borderColor: CHART_COLORS[0], borderWidth: 2 } }
    }
  ]
}))

/* ── 图 3 · 面交所热力 ── */

const heatCells = computed<[number, number, number][]>(() => {
  const cells: [number, number, number][] = []
  HEAT_DATA.forEach((row, pi) => row.forEach((v, si) => cells.push([si, pi, v])))
  return cells
})

const heatOption = computed<EChartsOption>(() => ({
  tooltip: {
    ...TOOLTIP,
    formatter: (p: unknown) => {
      const d = (p as { data: [number, number, number] }).data
      return `${HEAT_POINTS[d[1]]} · ${HEAT_SLOTS[d[0]]}<br/>成交 <b>${d[2]}</b> 笔`
    }
  },
  grid: { left: 96, right: 24, top: 16, bottom: 64 },
  xAxis: {
    type: 'category',
    data: HEAT_SLOTS,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    splitArea: { show: true }
  },
  yAxis: {
    type: 'category',
    data: HEAT_POINTS,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    splitArea: { show: true }
  },
  visualMap: {
    min: 0,
    max: Math.max(...heatCells.value.map((c) => c[2])),
    calculable: true,
    orient: 'horizontal',
    left: 'center',
    bottom: 0,
    itemWidth: 12,
    itemHeight: 90,
    inRange: { color: ['#FFF6E5', '#FAC775', '#EF9F27', '#BA7517'] }
  },
  series: [
    {
      type: 'heatmap',
      data: heatCells.value,
      itemStyle: { borderColor: '#FFFFFF', borderWidth: 1 }
    }
  ]
}))

/* ── 图 4 · 循环率 ── */

const stats = loopStats()

const loopOption = computed<EChartsOption>(() => ({
  color: [CHART_COLORS[1]],
  tooltip: { ...TOOLTIP, trigger: 'axis' },
  grid: GRID,
  xAxis: {
    type: 'category',
    data: LOOP_SERIES.map((p) => p.month),
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    boundaryGap: false
  },
  yAxis: { type: 'value', axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  series: [
    {
      name: '循环件数',
      type: 'line',
      data: LOOP_SERIES.map((p) => p.reused),
      areaStyle: { opacity: 0.18 },
      // LINE_SERIES_BASE 里 smooth 固定 false（数据要精确，不做平滑）——展开放最后，别在前头又写 smooth
      ...LINE_SERIES_BASE
    }
  ]
}))

/** 系数与假设弹窗：business/03 已决，减碳必须标注为估算假设 */
const showAssumption = (): void => {
  Modal.info({
    title: '减碳系数与假设',
    width: 560,
    okText: '我知道了',
    content: `估算系数（kg/件）：${CARBON_FACTORS.map((f) => `${f.name} ${f.kg}`).join('、')}。
    循环件数按品类占比拆分后加权求和。
    三条已知局限：① 系数为文献估值，非实测；② 未计入物流与包装；
    ③ 假设每件二手交易真的替代了一次新购。论文中原样标注为假设值，不得作为精确结论。`
  })
}

/* ── 分审核员明细（F-A9 呈现点之三） ── */

const sla = DEFAULT_SLA
const targetMin = targetMinutes(sla)
// 合计行直接取 slaSummary()：与看板 SLA 卡同一出处，不在报表里重新加权
const reviewerRows = reviewerSlaRows(slaSummary())

const reviewerColumns: TableColumnsType = [
  { title: '审核员', dataIndex: 'name', width: 120 },
  { title: '处置量（件）', dataIndex: 'handled', width: 110, align: 'right' },
  { title: '平均首审（分钟）', dataIndex: 'avgMin', width: 140, align: 'right' },
  { title: '达标率', key: 'okRate', width: 100, align: 'right' },
  { title: 'P95（分钟）', dataIndex: 'p95Min', width: 110, align: 'right' },
  { title: '平均处置（秒）', dataIndex: 'handleSec', width: 130, align: 'right' },
  { title: '状态', key: 'verdict', width: 90 }
]

/** 达标判定：任一项越过 F-A9 阈值即「预警」，不看单项颜色自己脑补 */
const verdictOf = (row: { okRate: number; avgMin: number; p95Min: number; handleSec: number }): '达标' | '预警' =>
  row.okRate >= sla.targetRate &&
  row.avgMin <= targetMin &&
  row.p95Min <= sla.p95Hours * 60 &&
  row.handleSec <= sla.avgHandleSeconds
    ? '达标'
    : '预警'
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">数据报表</h1>
      <p class="cm-page__sub">四张校园独有可视化：学期节律、学院偏好、面交负载、循环减碳。</p>
    </header>

    <!-- 图 4 的大数值卡：循环率与减碳 -->
    <div class="report__stats">
      <div class="cm-card report__stat">
        <span class="report__stat-num">{{ stats.rate }}%</span>
        <span class="report__stat-label">二手循环率</span>
        <span class="report__stat-sub">循环 {{ stats.reused }} 件 / 发布 {{ stats.published }} 件</span>
      </div>
      <div class="cm-card report__stat">
        <span class="report__stat-num">{{ stats.carbon }}</span>
        <span class="report__stat-label">估算减碳（kg）</span>
        <span class="report__stat-sub">按品类系数加权，属估算假设</span>
      </div>
      <div class="cm-card report__stat report__warn">
        <span class="report__stat-label">减碳系数为估算假设值，非实测</span>
        <a-button type="link" size="small" @click="showAssumption">系数与假设</a-button>
      </div>
    </div>

    <div class="report__grid">
      <ChartCard title="学期时间轴对比" :height="320">
        <template #extra>
          <a-select v-model:value="term" size="small" style="width: 110px">
            <a-select-option value="2026春">2026 春</a-select-option>
            <a-select-option value="2026秋">2026 秋</a-select-option>
          </a-select>
        </template>
        <VChart :option="termOption" autoresize />
      </ChartCard>

      <ChartCard title="学院 × 品类矩阵" :height="320">
        <template #extra>
          <a-select v-model:value="metric" size="small" style="width: 110px">
            <a-select-option value="deal">成交量</a-select-option>
            <a-select-option value="publish">发布量</a-select-option>
            <a-select-option value="rate">成交率</a-select-option>
          </a-select>
        </template>
        <VChart :option="matrixOption" autoresize />
      </ChartCard>

      <ChartCard title="校内面交所热力" :height="320">
        <template #extra>
          <span class="report__note">面交点 × 时段 × 成交笔数</span>
        </template>
        <VChart :option="heatOption" autoresize />
      </ChartCard>

      <ChartCard title="二手循环率趋势" :height="320">
        <template #extra>
          <span class="report__note">按月循环件数</span>
        </template>
        <VChart :option="loopOption" autoresize />
      </ChartCard>
    </div>

    <div class="cm-card report__story">
      <h2 class="report__story-title">这几张图要讲的故事</h2>
      <ul class="report__story-list">
        <li><b>三个峰值</b>：学期初出教材教辅、期末出笔记资料与小电器、毕业季出大件生活用品。</li>
        <li><b>学院偏好</b>：计算机学院出数码、艺术学院出乐器，教材教辅在所有学院都高。</li>
        <li><b>面交负载</b>：一食堂门口中午最挤、图书馆东门晚间最挤——可反推是否需要增设面交点。</li>
        <li><b>循环率</b>：循环件数 ÷ 发布件数，减碳按品类系数加权，属估算假设。</li>
      </ul>
    </div>

    <!-- 分审核员明细：F-A9 的第三处呈现，答辩时用来说明「SLA 落到了人」 -->
    <div class="cm-card report__reviewer">
      <div class="cm-card__title">分审核员明细（SLA · 近 14 日）</div>
      <div class="cm-card__body">
        <a-table
          :columns="reviewerColumns"
          :data-source="reviewerRows"
          :pagination="false"
          size="middle"
          :row-class-name="({ key }: { key: string }) => (key === 'total' ? 'report__reviewer-total' : '')"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'okRate'">{{ (record as ReviewerSlaRow).okRate }}%</template>
            <template v-else-if="column.key === 'verdict'">
              <a-tag :color="verdictOf(record as ReviewerSlaRow) === '达标' ? 'green' : 'orange'">
                {{ verdictOf(record as ReviewerSlaRow) }}
              </a-tag>
            </template>
          </template>
        </a-table>
        <p class="report__reviewer-foot">
          口径与看板 SLA 卡同源（近 14 日已处置统计，阈值取自「系统设置 · SLA 配置」）；
          首审响应 ≤ {{ sla.firstResponseHours }} 小时、P95 ≤ {{ sla.p95Hours }} 小时、达标率 ≥ {{ sla.targetRate }}%、
          单条平均处置 ≤ {{ sla.avgHandleSeconds }} 秒，任一项越线即预警。
          不含当前未处置的积压（该实时量在看板与审核队列呈现）。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.report__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--cm-space-4);
  margin-bottom: var(--cm-space-5);
}

.report__stat {
  padding: var(--cm-space-5);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.report__stat-num {
  font-size: var(--cm-font-size-display);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.report__stat-label {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.report__stat-sub {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 假设声明单独一张卡：不能只写在弹窗里，页面上也必须看得见 */
.report__warn {
  justify-content: center;
  background: var(--cm-color-orange-50, #fff7e6);
  border-color: var(--cm-color-orange-200, #ffd591);
}

.report__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cm-space-4);
}

.report__note {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.report__story {
  margin-top: var(--cm-space-5);
  padding: var(--cm-space-5);
}

.report__story-title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.report__story-list {
  margin: 0;
  padding-left: var(--cm-space-5);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
  line-height: 1.9;
}

/* ── 分审核员明细 ── */
.report__reviewer {
  margin-top: var(--cm-space-5);
}

.report__reviewer-foot {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.8;
  color: var(--cm-color-text-3);
}

:deep(.report__reviewer-total td) {
  font-weight: 600;
  background: var(--cm-color-surface-muted) !important;
}

@media (max-width: 1279px) {
  .report__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .report__stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
