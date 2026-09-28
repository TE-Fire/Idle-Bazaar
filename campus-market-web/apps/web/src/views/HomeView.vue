<script setup lang="ts">
/**
 * P1 首页 / 列表（business/01 §三 F-W1 检索闭环）
 *
 * <p>筛选是「与」逻辑（AND），**成色多选取「或」（OR）**——这是文档明确的例外，
 * 所以 UI 上必须写清「成色任选其一」，否则用户会以为选了 A 和 B 是「既要 A 又要 B」。
 *
 * <p>结果为空时给「放宽条件的建议」，不能只显示"暂无数据"（F-W1 判据）。
 *
 * <p>数据来自 `mock/items.ts`：学生端接口未实现，等 `/api/item/list` 就位后只换数据源。
 */

import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CAMPUSES,
  CONDITION_GRADES,
  CONDITION_LABELS,
  formatPrice,
  type Campus,
  type ConditionGrade
} from '@campus/shared'
import { mockItems } from '@/mock/items'
import ProductCard from '@/components/ProductCard.vue'
import FilterGroup from '@/components/FilterGroup.vue'

type SortKey = 'default' | 'price-asc' | 'price-desc' | 'time' | 'hot'
type ViewMode = 'grid' | 'list'
type TimeRange = '' | '24h' | '3d' | '7d'

const route = useRoute()
const router = useRouter()

/* ── 筛选条件 ───────────────────────────────────────────── */

const keyword = ref('')
const category = ref('')
const priceMin = ref<number | null>(null)
const priceMax = ref<number | null>(null)
const conditions = ref<ConditionGrade[]>([])
const campuses = ref<Campus[]>([])
const verifiedOnly = ref(false)
const creditGood = ref(false)
/** 「只看可议价 / 包校园配送」挂在价格组里（原型侧栏如此），但它们是商品属性，不是卖家属性 */
const negotiableOnly = ref(false)
const deliveryOnly = ref(false)
const timeRange = ref<TimeRange>('')

/** 顶栏搜索与分类条是 URL 驱动的，换 URL 要同步进筛选状态 */
watch(
  () => [route.query.q, route.query.cat],
  ([q, cat]) => {
    keyword.value = (q as string) ?? ''
    category.value = (cat as string) ?? ''
    page.value = 1
  },
  { immediate: true }
)

const sort = ref<SortKey>('default')
const view = ref<ViewMode>('grid')
const page = ref(1)
const pageSize = 12

/* ── 结果集 ─────────────────────────────────────────────── */

const filtered = computed(() => {
  const kw = keyword.value.trim()
  const now = Date.now()

  return mockItems.filter((it) => {
    if (category.value && it.category !== category.value) return false
    if (kw && !`${it.title}${it.description}`.includes(kw)) return false
    if (priceMin.value !== null && it.price < priceMin.value) return false
    if (priceMax.value !== null && it.price > priceMax.value) return false
    // ★ 成色是「或」：勾了 A、B 表示 A 或 B 都行
    if (conditions.value.length && !conditions.value.includes(it.condition)) return false
    if (campuses.value.length && !campuses.value.includes(it.campus)) return false
    if (negotiableOnly.value && !it.negotiable) return false
    if (deliveryOnly.value && !it.delivery) return false
    if (verifiedOnly.value && !it.seller.verified) return false
    if (creditGood.value && it.seller.credit !== '极好') return false
    if (timeRange.value) {
      const hours = timeRange.value === '24h' ? 24 : timeRange.value === '3d' ? 72 : 168
      if (now - new Date(it.publishedAt).getTime() > hours * 3600_000) return false
    }
    return true
  })
})

const sorted = computed(() => {
  const list = [...filtered.value]
  switch (sort.value) {
    case 'price-asc':
      return list.sort((a, b) => a.price - b.price)
    case 'price-desc':
      return list.sort((a, b) => b.price - a.price)
    case 'time':
      return list.sort((a, b) => +new Date(b.publishedAt) - +new Date(a.publishedAt))
    case 'hot':
      return list.sort((a, b) => b.wantCount - a.wantCount)
    default:
      return list
  }
})

const paged = computed(() => {
  const start = (page.value - 1) * pageSize
  return sorted.value.slice(start, start + pageSize)
})

/* ── 已选条件 chip（可单个移除 / 一键清空）───────────────── */

interface Chip {
  key: string
  label: string
  clear: () => void
}

const chips = computed<Chip[]>(() => {
  const out: Chip[] = []
  if (keyword.value.trim())
    out.push({ key: 'kw', label: `关键词：${keyword.value}`, clear: () => (keyword.value = '') })
  if (category.value)
    out.push({
      key: 'cat',
      label: category.value,
      clear: () => router.push({ name: 'home' })
    })
  if (priceMin.value !== null || priceMax.value !== null)
    out.push({
      key: 'price',
      label: `${formatPrice(priceMin.value ?? 0)} – ${formatPrice(priceMax.value ?? 0)}`,
      clear: () => {
        priceMin.value = null
        priceMax.value = null
      }
    })
  for (const c of conditions.value)
    out.push({
      key: `cond-${c}`,
      label: `成色 ${c}`,
      clear: () => (conditions.value = conditions.value.filter((x) => x !== c))
    })
  for (const c of campuses.value)
    out.push({ key: `campus-${c}`, label: c, clear: () => (campuses.value = campuses.value.filter((x) => x !== c)) })
  if (negotiableOnly.value) out.push({ key: 'nego', label: '可议价', clear: () => (negotiableOnly.value = false) })
  if (deliveryOnly.value) out.push({ key: 'delivery', label: '包配送', clear: () => (deliveryOnly.value = false) })
  if (verifiedOnly.value) out.push({ key: 'verified', label: '仅看学号已认证', clear: () => (verifiedOnly.value = false) })
  if (creditGood.value) out.push({ key: 'credit', label: '信用极好', clear: () => (creditGood.value = false) })
  if (timeRange.value)
    out.push({
      key: 'time',
      label: timeRange.value === '24h' ? '24 小时内' : timeRange.value === '3d' ? '3 天内' : '一周内',
      clear: () => (timeRange.value = '')
    })
  return out
})

function clearAll(): void {
  keyword.value = ''
  priceMin.value = null
  priceMax.value = null
  conditions.value = []
  campuses.value = []
  verifiedOnly.value = false
  creditGood.value = false
  negotiableOnly.value = false
  deliveryOnly.value = false
  timeRange.value = ''
  void router.push({ name: 'home' })
}

/** 空态的「放宽条件」：先摘掉最窄的两个条件——校区与价格 */
function relax(): void {
  campuses.value = []
  priceMin.value = null
  priceMax.value = null
}

watch([filtered], () => {
  if (page.value > 1 && (page.value - 1) * pageSize >= filtered.value.length) page.value = 1
})
</script>

<template>
  <div class="cm-shell home">
    <!-- 面包屑（design/01 §3.2） -->
    <div class="crumb">
      <RouterLink :to="{ name: 'home' }">首页</RouterLink>
      <span v-if="category"> / {{ category }}</span>
    </div>

    <div class="body">
      <!-- ── 左侧常驻筛选 240px，sticky ────────────────── -->
      <aside class="side">
        <FilterGroup
          title="价格区间"
          clearable
          @clear="((priceMin = null), (priceMax = null), (negotiableOnly = false), (deliveryOnly = false))"
        >
          <div class="prange">
            <el-input-number v-model="priceMin" :min="0" :step="10" size="small" placeholder="最低" controls-position="right" />
            <i>—</i>
            <el-input-number v-model="priceMax" :min="0" :step="10" size="small" placeholder="最高" controls-position="right" />
          </div>
          <label><input v-model="negotiableOnly" type="checkbox" /> 只看可议价</label>
          <label><input v-model="deliveryOnly" type="checkbox" /> 包校园配送</label>
        </FilterGroup>

        <FilterGroup title="成色" clearable @clear="conditions = []">
          <p class="hint">成色任选其一（多选为「或」）</p>
          <label v-for="g in CONDITION_GRADES" :key="g">
            <input v-model="conditions" type="checkbox" :value="g" /> {{ g }} 级 · {{ CONDITION_LABELS[g] }}
          </label>
        </FilterGroup>

        <FilterGroup title="校区 / 面交点" clearable @clear="campuses = []">
          <label v-for="c in CAMPUSES" :key="c"><input v-model="campuses" type="checkbox" :value="c" /> {{ c }}</label>
        </FilterGroup>

        <FilterGroup title="卖家" clearable @clear="((verifiedOnly = false), (creditGood = false))">
          <label><input v-model="verifiedOnly" type="checkbox" /> 仅看学号已认证</label>
          <label><input v-model="creditGood" type="checkbox" /> 信用极好</label>
        </FilterGroup>

        <FilterGroup title="发布时间" clearable @clear="timeRange = ''">
          <label><input v-model="timeRange" type="radio" value="" /> 不限</label>
          <label><input v-model="timeRange" type="radio" value="24h" /> 24 小时内</label>
          <label><input v-model="timeRange" type="radio" value="3d" /> 3 天内</label>
          <label><input v-model="timeRange" type="radio" value="7d" /> 一周内</label>
        </FilterGroup>
      </aside>

      <!-- ── 内容区 ────────────────────────────────────── -->
      <section class="content">
        <div v-if="chips.length" class="chips">
          <span v-for="c in chips" :key="c.key" class="cm-chip" @click="c.clear()">
            {{ c.label }}<i class="cm-chip__x">×</i>
          </span>
          <button type="button" class="chips__clear" @click="clearAll">清空全部</button>
        </div>

        <div class="toolbar">
          <span class="count">共 {{ filtered.length }} 件</span>
          <div class="toolbar__right">
            <el-radio-group v-model="view" size="small">
              <el-radio-button value="grid">网格</el-radio-button>
              <el-radio-button value="list">列表</el-radio-button>
            </el-radio-group>
            <el-select v-model="sort" size="small" class="sort">
              <el-option label="默认排序" value="default" />
              <el-option label="价格从低到高" value="price-asc" />
              <el-option label="价格从高到低" value="price-desc" />
              <el-option label="最新发布" value="time" />
              <el-option label="最多人想要" value="hot" />
            </el-select>
          </div>
        </div>

        <!-- 空态：给可执行的放宽建议，不放一句「暂无数据」就完事 -->
        <el-empty v-if="!paged.length" description="没有符合条件的商品">
          <el-button type="primary" @click="relax">放宽条件（去掉校区与价格限制）</el-button>
        </el-empty>

        <div v-else class="grid" :class="`grid--${view}`">
          <ProductCard v-for="it in paged" :key="it.id" :item="it" :mode="view" />
        </div>

        <el-pagination
          v-if="filtered.length > pageSize"
          v-model:current-page="page"
          :page-size="pageSize"
          :total="filtered.length"
          layout="prev, pager, next"
          class="pager"
        />
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.home {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.crumb {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.crumb a:hover {
  color: var(--cm-color-text-1);
}

.body {
  display: flex;
  gap: var(--cm-space-5);
  align-items: flex-start;
}

/* ── 侧栏 240px sticky ──────────────────────────────────── */

.side {
  width: var(--cm-layout-side-w);
  flex: none;
  /* design/01 §4.6：top = nav-h + cat-h + 16px */
  position: sticky;
  top: calc(var(--cm-layout-nav-h) + var(--cm-layout-cat-h) + var(--cm-space-4));
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  padding: var(--cm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

.prange {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.prange :deep(.el-input-number) {
  width: 88px;
}

.hint {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 内容区 ─────────────────────────────────────────────── */

.content {
  flex: 1;
  min-width: 0;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.chips__clear {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0 var(--cm-space-1);
}

.chips__clear:hover {
  color: var(--cm-color-text-1);
}

.toolbar {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 var(--cm-space-3);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-md);
  margin-bottom: var(--cm-space-4);
}

.count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.toolbar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.sort {
  width: 130px;
}

/* 网格：auto-fill + minmax 让 lg 出 4 列、xl 出 5–6 列（design/01 §3.1） */
.grid {
  display: grid;
  gap: var(--cm-space-4);
}

.grid--grid {
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
}

.grid--list {
  grid-template-columns: 1fr;
}

.pager {
  margin-top: var(--cm-space-6);
  justify-content: center;
}

/* 侧栏在窄屏下让位：design/01 只要求 ≥1024 常驻侧栏 */
@media (max-width: 1023px) {
  .side {
    display: none;
  }
}
</style>
