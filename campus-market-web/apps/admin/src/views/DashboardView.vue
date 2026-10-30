<script setup lang="ts">
/**
 * A2 数据看板（原型 prototypes/admin/dashboard.html，design/03 第五章）
 *
 * <p>信息层级严格按 design/03「A2 看板信息层级」+ 原型页脚自述的顺序：
 *   ① KPI×4 → ② 审核时效 SLA（F-A9）→ ③ 趋势（判走向）→ ④ 构成（判结构）
 *   → ⑤ TOP-N（找具体对象）→ ⑥ 待处置队列（动作入口）→ ⑦ 异常预警（校园特色）
 *
 * <p><b>六块一次取回</b>（`/admin/stats/dashboard`，口径见 notes/25 §二）：
 * 拆成六个接口会让首屏出现「KPI 已经在了、图还在转」，前端还得自己拼加载态。
 * SLA 卡是本页第七块，与 A8 报表的「分审核员明细」口径不同——前者是实况，
 * 后者是分人明细，两者都不与对方的数字互相推导。
 *
 * <p><b>同比环比已经去掉</b>：库里只有当年数据，算环比会得到一堆除零。
 * 原型上那行「较上周 +12%」不再出现，宁缺毋滥。
 *
 * <p>异常预警只留三条可解释的规则（价格异常 / 高频发布 / 低信用在售），文案与跳转由本页维护，
 * 后端只回计数——判据写在演示能讲清的一处，不散在两边。
 */

import { computed, onMounted, ref } from 'vue'
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
import { statsApi, type DashboardVO } from '@campus/api'

const router = useRouter()

const loading = ref(false)
const days = ref<7 | 30>(7)
const data = ref<DashboardVO | null>(null)

async function load(): Promise<void> {
  loading.value = true
  try {
    data.value = await statsApi.dashboard(days.value)
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 切窗口只影响趋势区，其余块与窗口无关（notes/25 §二） */
function onRange(): void {
  void load()
}

const goQueue = (route?: string): void => {
  if (route) void router.push({ name: route })
}

/* ── ① KPI×4 ───────────────────────────────────── */

/** 金额缩写：≥1 万显示「x.x 万」，精确值仍进 title，不让缩写吞掉真数 */
function fmtMoney(amount: string): { value: string; exact: string } {
  const n = Number(amount)
  const exact = `¥ ${n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  if (n >= 10000) return { value: `¥ ${(n / 10000).toFixed(1)} 万`, exact }
  return { value: exact, exact }
}

const kpiItems = computed(() => {
  const k = data.value?.kpi
  if (!k) return []
  const gmv = fmtMoney(k.gmv)
  return [
    { key: 'gmv', title: '累计 GMV', value: gmv.value, exact: gmv.exact },
    { key: 'items', title: '今日新增商品', value: `${k.itemCount} 件`, exact: `${k.itemCount} 件` },
    { key: 'orders', title: '今日成交笔数', value: `${k.orderCount} 笔`, exact: `${k.orderCount} 笔` },
    { key: 'users', title: '今日活跃用户', value: `${k.activeUsers} 人`, exact: `${k.activeUsers} 人` }
  ]
})

/* ── ③ 趋势：GMV 走面积（右轴），发布/成交走折线（左轴） ── */
const trendOption = computed<EChartsOption>(() => {
  const t = data.value?.trend
  return {
    color: CHART_COLORS,
    tooltip: TOOLTIP,
    legend: { data: ['发布量', '成交量', 'GMV(元)'], top: 0, textStyle: { color: '#8E8E93', fontSize: 12 } },
    grid: GRID,
    xAxis: {
      type: 'category',
      data: t?.labels ?? [],
      boundaryGap: false,
      axisLine: AXIS_LINE,
      axisLabel: AXIS_LABEL
    },
    yAxis: [
      { type: 'value', name: '件/笔', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
      { type: 'value', name: '元', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: { show: false } }
    ],
    series: [
      { ...LINE_SERIES_BASE, name: '发布量', type: 'line', data: t?.published ?? [] },
      { ...LINE_SERIES_BASE, name: '成交量', type: 'line', data: t?.sold ?? [] },
      {
        ...LINE_SERIES_BASE,
        name: 'GMV(元)',
        type: 'line',
        yAxisIndex: 1,
        data: (t?.gmv ?? []).map((v) => Number(v)),
        areaStyle: { opacity: 0.12 }
      }
    ]
  }
})

/* ── ④ 构成：品类环图，内径 60%，中心显示在售总数 ───────── */
const categoryOption = computed<EChartsOption>(() => {
  const slices = data.value?.categorySlices ?? []
  const total = slices.reduce((s, i) => s + i.value, 0)
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
        data: slices
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
        style: { text: '在售件数', textAlign: 'center', fontSize: 12, fill: '#8E8E93' }
      }
    ]
  }
})

/* ── ⑤ TOP-N 明细：横向柱，yAxis 类目 + inverse 让最大值在顶部 ───── */
const topOption = (rows: Array<{ name: string; value: number }>, color: string): EChartsOption => ({
  tooltip: { ...TOOLTIP, trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 8, right: 32, top: 8, bottom: 8, containLabel: true },
  xAxis: { type: 'value', axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  yAxis: {
    type: 'category',
    data: rows.map((r) => r.name),
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false },
    inverse: true
  },
  series: [
    {
      type: 'bar',
      data: rows.map((r) => r.value),
      barMaxWidth: 14,
      itemStyle: { borderRadius: [0, 6, 6, 0], color }
    }
  ]
})

const hotCategoryOption = computed(() => topOption(data.value?.topCategories ?? [], CHART_COLORS[0]))
const activeCollegeOption = computed(() => topOption(data.value?.topColleges ?? [], CHART_COLORS[2]))

/* ── ② 审核时效 SLA（F-A9） ─────────────────────── */
const sla = computed(() => data.value?.sla ?? null)
/** 目标线分钟数从配置算，不写死 120——阈值设置页可调 */
const targetMin = computed(() => (sla.value ? sla.value.thresholds.firstResponseHours * 60 : 120))

const slaMetrics = computed(() => {
  const s = sla.value
  if (!s) return []
  const t = s.thresholds
  const p95Hour = Number(s.p95Min) / 60
  return [
    {
      key: 'avg',
      label: '平均首审时长',
      value: String(s.avgMin),
      unit: '分钟',
      target: `目标 ≤ ${targetMin.value} 分钟`,
      alert: s.avgMin > targetMin.value
    },
    {
      key: 'rate',
      label: 'SLA 达标率',
      value: Number(s.okRate).toFixed(1),
      unit: '%',
      target: `目标 ≥ ${t.targetRate}%`,
      alert: Number(s.okRate) < t.targetRate
    },
    {
      key: 'p95',
      label: 'P95 首审时长',
      value: `≈ ${p95Hour.toFixed(1)}`,
      unit: '小时',
      target: `目标 ≤ ${t.p95Hours} 小时`,
      alert: p95Hour > t.p95Hours
    },
    {
      key: 'backlog',
      label: `超时积压（>${t.backlogHours}h）`,
      value: String(s.backlog),
      unit: '件',
      target: '需当日清零',
      alert: s.backlog > 0
    },
    {
      key: 'handle',
      label: '单条平均处置',
      value: String(s.handleSec),
      unit: '秒',
      target: `目标 ≤ ${t.avgHandleSeconds} 秒`,
      alert: s.handleSec > t.avgHandleSeconds
    }
  ]
})

/* 近 14 日柱状：超目标线的那天标红——全绿的 SLA 图对运营没有价值 */
const slaOption = computed<EChartsOption>(() => ({
  tooltip: {
    ...TOOLTIP,
    formatter: (p: unknown) => {
      const first = (p as Array<{ name: string; value: number }>)[0]
      const over = first.value > targetMin.value ? '，超目标线' : ''
      return `${first.name}<br/>平均首审 ${first.value} 分钟${over}`
    }
  },
  grid: { left: 8, right: 24, top: 16, bottom: 8, containLabel: true },
  xAxis: {
    type: 'category',
    data: (sla.value?.daily ?? []).map((d) => d.day),
    axisLine: AXIS_LINE,
    axisLabel: AXIS_LABEL,
    axisTick: { show: false }
  },
  yAxis: { type: 'value', name: '分钟', axisLine: AXIS_LINE, axisLabel: AXIS_LABEL, splitLine: SPLIT_LINE },
  series: [
    {
      ...BAR_SERIES_BASE,
      type: 'bar',
      data: (sla.value?.daily ?? []).map((d) => ({
        value: Number(d.value),
        itemStyle: {
          color: Number(d.value) > targetMin.value ? CHART_COLORS[5] : CHART_COLORS[0],
          borderRadius: [6, 6, 0, 0] as number[]
        }
      })),
      markLine: {
        silent: true,
        symbol: 'none',
        /* 目标线用中灰（比网格线 #E8E8ED 深一档），既不抢戏也看得清 */
        lineStyle: { type: 'dashed', color: '#8E8E93' },
        label: {
          formatter: `目标 ${targetMin.value}min`,
          position: 'insideEndTop',
          fontSize: 11,
          color: '#8E8E93'
        },
        data: [{ yAxis: targetMin.value }]
      }
    }
  ]
}))

/* ── ⑥ 待处置队列：点一下直接进处置页，否则这个模块只是装饰 ── */
const pending = computed(() => {
  const p = data.value?.pending
  return [
    { key: 'review', label: '待审核商品', count: p?.audit ?? 0, route: 'review' },
    { key: 'report', label: '举报待处理', count: p?.report ?? 0, route: 'social-reports' },
    { key: 'appeal', label: '申诉待复核', count: p?.appeal ?? 0, route: 'review' },
    { key: 'refund', label: '退款/纠纷待处理', count: p?.refund ?? 0, route: 'orders' }
  ]
})

/* ── ⑦ 异常预警：三条判据（notes/25 §二），文案在本页维护 ── */
interface AlertItem {
  key: string
  level: 'error' | 'warn'
  label: string
  desc: string
  count: number
  route: string
}

const ALERT_META: Record<string, Omit<AlertItem, 'count'>> = {
  price: {
    key: 'price',
    level: 'error',
    label: '价格异常',
    desc: '售价比同分类在售均价低 30% 以上',
    route: 'items'
  },
  brush: {
    key: 'brush',
    level: 'warn',
    label: '高频发布',
    desc: '同一卖家 1 小时内发布 ≥ 5 件',
    route: 'users'
  },
  credit: {
    key: 'credit',
    level: 'warn',
    label: '低信用在售',
    desc: '信用分 < 60 且名下有在售商品',
    route: 'users'
  }
}

const alerts = computed<AlertItem[]>(() =>
  (data.value?.alerts ?? [])
    .filter((a) => ALERT_META[a.key])
    .map((a) => ({ ...ALERT_META[a.key], count: a.count }))
)
</script>

<template>
  <div class="dashboard cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">数据看板</h1>
      <p class="cm-page__sub">全校交易概况，指标按角色权限下发</p>
    </header>

    <div v-if="loading && !data" class="dashboard__state">加载中…</div>

    <template v-else>
      <!-- ① KPI×4 -->
      <a-row :gutter="[16, 16]">
        <a-col v-for="kpi in kpiItems" :key="kpi.key" :span="6">
          <KpiCard :title="kpi.title" :value="kpi.value" :exact="kpi.exact" />
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
              <p v-if="sla" class="dashboard__sla-foot">
                口径：从商品进入队列 → 首次处置完成；首审响应 ≤ {{ sla.thresholds.firstResponseHours }} 小时为达标，
                P95 ≤ {{ sla.thresholds.p95Hours }} 小时，达标率 ≥ {{ sla.thresholds.targetRate }}%，
                超 {{ sla.thresholds.backlogHours }} 小时未处置记为积压红牌、需当日清零。
                单条平均处置 ≤ {{ sla.thresholds.avgHandleSeconds }} 秒是操作效率指标，与响应时效是两个层级，不可混用；
                申诉另承诺 {{ sla.thresholds.appealHours }} 小时内给出复审结论。阈值可在「系统设置 · SLA 配置」调整。
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
              <a-radio-group v-model:value="days" size="small" @change="onRange">
                <a-radio-button :value="7">近 7 日</a-radio-button>
                <a-radio-button :value="30">近 30 日</a-radio-button>
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
          <ChartCard title="热销品类 TOP5">
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
              <a-badge v-for="item in pending" :key="item.key" :count="item.count" :overflow-count="99">
                <a-button shape="round" @click="goQueue(item.route)">{{ item.label }}</a-button>
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
                <a-button type="link" size="small" @click="goQueue(a.route)">查看</a-button>
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
          达到设计规范上限（3–5 张）；其余校园专题图（学期时间轴 / 学院×品类矩阵 / 面交所热力）
          收在「数据报表 A8」。数据为实时聚合，不建日统计中间表；同比环比因库中只有当年数据已去掉
          （算出来会是一堆除零）。异常预警只做三条可解释规则，不做模型打分。
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard__row {
  margin-top: var(--cm-space-5);
}

.dashboard__state {
  padding: var(--cm-space-8) 0;
  text-align: center;
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-sm);
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
