<script setup lang="ts">
/**
 * A8 数据报表 · 三张校园独有可视化（business/03 第五章，口径 notes/25 §三）
 *
 * <p>每张图都对应一个能讲出来的「数据故事」：
 *   ① 学期时间轴 —— 三个峰值：学期初出教材、期末出笔记、毕业季出大件；
 *   ② 学院 × 品类 —— 计算机出数码、艺术出乐器、教材在所有学院都高；
 *   ③ 面交所热力 —— 哪个点在哪个时段过载，可反推是否要增设面交点。
 *
 * <p><b>第四张「二手循环率与减碳」已于 2026-10-30 下线</b>（db/01 第六节）：
 * 系数无法用本校数据校准，图上多一个说不清的指标不如少一个。不要加回来。
 *
 * <p>数据全部实时聚合（`/admin/stats/report/*`），不建日统计中间表；
 * 时间一律用业务时间字段（`published_at` / `pay_time` / `meet_time`），不用 `create_time` 顶替。
 */

import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import VChart from 'vue-echarts'
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
  settingsApi,
  statsApi,
  type MatrixMetric,
  type ReportMeetHeat,
  type ReportMatrix,
  type ReportSlaTable,
  type ReportTimeline,
  type ReviewerSlaRow,
  type SlaThresholds
} from '@campus/api'

const loading = ref(false)
const timeline = ref<ReportTimeline | null>(null)
const matrix = ref<ReportMatrix | null>(null)
const heat = ref<ReportMeetHeat | null>(null)
const slaTable = ref<ReportSlaTable | null>(null)
const sla = ref<SlaThresholds | null>(null)
const metric = ref<MatrixMetric>('sold')

const METRIC_LABELS: Record<MatrixMetric, string> = {
  sold: '成交量',
  published: '发布量',
  rate: '成交率'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [t, m, h, s, cfg] = await Promise.all([
      statsApi.timeline(18),
      statsApi.matrix(metric.value),
      statsApi.meetPoint(),
      statsApi.sla(),
      settingsApi.sla()
    ])
    timeline.value = t
    matrix.value = m
    heat.value = h
    slaTable.value = s
    sla.value = cfg
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 换度量只重取矩阵，其余两张图与阈值不受影响 */
async function onMetric(): Promise<void> {
  try {
    matrix.value = await statsApi.matrix(metric.value)
  } catch {
    // 请求层已提示
  }
}

/* ── 图 1 · 学期时间轴 ── */
const termOption = computed<EChartsOption>(() => ({
  color: [CHART_COLORS[0], CHART_COLORS[1]],
  tooltip: { ...TOOLTIP, trigger: 'axis' },
  legend: { data: ['发布量', '成交量'], right: 0, top: 0, textStyle: { color: '#6E6E73' } },
  grid: GRID,
  xAxis: {
    type: 'category',
    data: timeline.value?.labels ?? [],
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false }
  },
  yAxis: { type: 'value', axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  series: [
    {
      name: '发布量',
      type: 'line',
      // smooth 固定 false：数据要精确，不做平滑（theme.ts 的约定）
      ...LINE_SERIES_BASE,
      data: timeline.value?.published ?? [],
      areaStyle: { opacity: 0.12 }
    },
    {
      name: '成交量',
      type: 'line',
      ...LINE_SERIES_BASE,
      data: timeline.value?.sold ?? [],
      areaStyle: { opacity: 0.12 }
    }
  ]
}))

/* ── 图 2 · 学院 × 品类热力矩阵 ── */

/** heatmap 的三元组：[列下标(品类), 行下标(学院), 值] —— 注意 x 在前 */
const matrixCells = computed<[number, number, number][]>(() => {
  const cells: [number, number, number][] = []
  const m = matrix.value
  if (!m) return cells
  m.cells.forEach((row, ri) => row.forEach((v, ci) => cells.push([ci, ri, v])))
  return cells
})

const matrixMax = computed(() => Math.max(1, ...matrixCells.value.map((c) => c[2])))

const matrixOption = computed<EChartsOption>(() => {
  const m = matrix.value
  return {
    tooltip: {
      ...TOOLTIP,
      formatter: (p: unknown) => {
        const d = (p as { data: [number, number, number] }).data
        const unit = metric.value === 'rate' ? '%' : ' 件'
        return `${m?.categories[d[0]] ?? ''} · ${m?.colleges[d[1]] ?? ''}<br/>${
          METRIC_LABELS[metric.value]
        }：<b>${d[2]}${unit}</b>`
      }
    },
    grid: { left: 96, right: 24, top: 16, bottom: 72 },
    xAxis: {
      type: 'category',
      data: m?.categories ?? [],
      axisLabel: { ...AXIS_LABEL, rotate: 30 },
      axisTick: { show: false },
      splitArea: { show: true }
    },
    yAxis: {
      type: 'category',
      data: m?.colleges ?? [],
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
  }
})

/* ── 图 3 · 面交所热力 ── */

const heatCells = computed<[number, number, number][]>(() => {
  const cells: [number, number, number][] = []
  const h = heat.value
  if (!h) return cells
  h.cells.forEach((row, pi) => row.forEach((v, si) => cells.push([si, pi, v])))
  return cells
})

const heatOption = computed<EChartsOption>(() => {
  const h = heat.value
  return {
    tooltip: {
      ...TOOLTIP,
      formatter: (p: unknown) => {
        const d = (p as { data: [number, number, number] }).data
        return `${h?.points[d[1]] ?? ''} · ${h?.slots[d[0]] ?? ''}<br/>成交 <b>${d[2]}</b> 笔`
      }
    },
    grid: { left: 96, right: 24, top: 16, bottom: 64 },
    xAxis: {
      type: 'category',
      data: h?.slots ?? [],
      axisLabel: AXIS_LABEL,
      axisTick: { show: false },
      splitArea: { show: true }
    },
    yAxis: {
      type: 'category',
      data: h?.points ?? [],
      axisLabel: AXIS_LABEL,
      axisTick: { show: false },
      splitArea: { show: true }
    },
    visualMap: {
      min: 0,
      max: Math.max(1, ...heatCells.value.map((c) => c[2])),
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
  }
})

/* ── 分审核员明细（F-A9 呈现点之三） ── */

const reviewerColumns: TableColumnsType = [
  { title: '审核员', dataIndex: 'name', width: 120 },
  { title: '处置量（件）', dataIndex: 'handled', width: 110, align: 'right' },
  { title: '平均首审（分钟）', dataIndex: 'avgMin', width: 140, align: 'right' },
  { title: '达标率', key: 'okRate', width: 100, align: 'right' },
  { title: 'P95（分钟）', dataIndex: 'p95Min', width: 110, align: 'right' },
  { title: '平均处置（秒）', dataIndex: 'handleSec', width: 130, align: 'right' },
  { title: '状态', key: 'verdict', width: 90 }
]

/** 明细 + 合计行：合计由后端给（与看板 SLA 卡同源，不在前端重新加权） */
const reviewerRows = computed(() => {
  const t = slaTable.value
  if (!t) return [] as Array<ReviewerSlaRow & { key: string }>
  return [
    ...t.rows.map((r, i) => ({ ...r, key: `r${i}` })),
    { ...t.total, key: 'total' }
  ]
})

/** 达标判定：任一项越过 F-A9 阈值即「预警」，不看单项颜色自己脑补 */
const verdictOf = (row: ReviewerSlaRow): '达标' | '预警' => {
  const cfg = sla.value
  if (!cfg) return '达标'
  return Number(row.okRate) >= cfg.targetRate &&
    row.avgMin <= cfg.firstResponseHours * 60 &&
    Number(row.p95Min) <= cfg.p95Hours * 60 &&
    row.handleSec <= cfg.avgHandleSeconds
    ? '达标'
    : '预警'
}
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">数据报表</h1>
      <p class="cm-page__sub">三张校园独有可视化：学期节律、学院偏好、面交负载。</p>
    </header>

    <div v-if="loading && !timeline" class="report__state">加载中…</div>

    <template v-else>
      <div class="report__grid">
        <ChartCard title="学期时间轴（近 18 周）" :height="320">
          <template #extra>
            <span class="report__note">按自然周聚合，缺数据补 0</span>
          </template>
          <VChart :option="termOption" autoresize />
        </ChartCard>

        <ChartCard title="学院 × 品类矩阵" :height="320">
          <template #extra>
            <a-select v-model:value="metric" size="small" style="width: 110px" @change="onMetric">
              <a-select-option value="sold">成交量</a-select-option>
              <a-select-option value="published">发布量</a-select-option>
              <a-select-option value="rate">成交率</a-select-option>
            </a-select>
          </template>
          <VChart :option="matrixOption" autoresize />
        </ChartCard>

        <ChartCard title="校内面交所热力" :height="320">
          <template #extra>
            <span class="report__note">面交点 × 五档时段 × 成交笔数</span>
          </template>
          <VChart :option="heatOption" autoresize />
        </ChartCard>

        <ChartCard title="这三张图怎么读" :height="320">
          <ul class="report__story-list">
            <li><b>时间轴</b>：学期初出教材教辅、期末出笔记与小电器、毕业季出大件，成交量滞后发布约一周。</li>
            <li><b>矩阵</b>：行 = 学院（含「未指定」兜住空学院），列 = 一级品类；换度量可看供给与成交的落差。</li>
            <li><b>面交热力</b>：横轴五档时段、纵轴面交点，颜色深即过载，可反推是否要增设面交点。</li>
            <li><b>口径</b>：发布看 <code>published_at</code>、成交看 <code>pay_time</code>、面交看 <code>meet_time</code>，不用创建时间顶替。</li>
          </ul>
        </ChartCard>
      </div>

      <!-- 分审核员明细：F-A9 的第三处呈现，答辩时用来说明「SLA 落到了人」 -->
      <div class="cm-card report__reviewer">
        <div class="cm-card__title">分审核员明细（SLA · 已处置全量）</div>
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
          <p v-if="sla" class="report__reviewer-foot">
            口径与看板 SLA 卡同源（已处置审核单的统计，阈值取自「系统设置 · SLA 配置」）；
            首审响应 ≤ {{ sla.firstResponseHours }} 小时、P95 ≤ {{ sla.p95Hours }} 小时、达标率 ≥ {{ sla.targetRate }}%、
            单条平均处置 ≤ {{ sla.avgHandleSeconds }} 秒，任一项越线即预警。
            不含当前未处置的积压（该实时量在看板与审核队列呈现）。P95 取排序后第 95 百分位，不插值。
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.report__state {
  padding: var(--cm-space-8) 0;
  text-align: center;
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-sm);
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
}
</style>
