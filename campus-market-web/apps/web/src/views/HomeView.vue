<script setup lang="ts">
/**
 * P1 首页 / 列表（business/01 §三 F-W1 检索闭环）
 *
 * <p>筛选是「与」逻辑（AND），**成色多选取「或」（OR）**——这是文档明确的例外，
 * 所以 UI 上必须写清「成色任选其一」，否则用户会以为选了 A 和 B 是「既要 A 又要 B」。
 *
 * <p>结果为空时给「放宽条件的建议」，不能只显示"暂无数据"（F-W1 判据）。
 *
 * <p>数据来自 `/api/item/list`（`userItemApi.list`）：后端只返在售商品，发布时间倒序。
 * 一次拉满一页（pageSize=50，后端上限）后**筛选 / 排序 / 分页大多在客户端做**——
 * 例外是**分类**：必须由服务端 `categoryId` 过滤。原因是商品表存的是二级分类
 * （「手机平板」），而首页分类入口传的是一级（「数码电子」），后端 `expandCategory`
 * 会把一级展开成其下全部二级；前端做不到这件事，本地按名字等值比较永远不相等。
 * 曾经就是这么错的：只有「其他闲置」这种商品直接挂在一级上的分类能筛出东西。
 *
 * <p>分类与校区的**选项都来自 `/api/dict/all`**，不写死——字典可在管理端增删改，
 * 前端枚举一旦与库里对不上，选项点了没结果，且看不出是哪里错。
 * 其余条件（价格、成色、卖家、时间范围、排序）尚未参数化，在售量超过 50 后要回头补。
 *
 * <p>2026-09-30 按闲鱼 Web 版改版：
 * - 顶部分类条取消，分类收进左侧「全部分类」卡（闲鱼的分类入口在首页左上）；
 * - 纯首页态在内容区顶部铺**彩色促销卡位**（橙主推 + 黄/蓝/绿三张品类卡），
 *   这是闲鱼首页"一眼有货"的关键，也是旧版"简陋"观感的主要缺口；
 * - 「猜你喜欢」快捷分类 chips（胶囊，选中黄底）直接过滤当前列表；
 * - 排序从下拉换成闲鱼式的**排序胶囊组**（综合/最新/最热/价格）。
 */

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  CONDITION_GRADES,
  CONDITION_LABELS,
  formatPrice,
  type ConditionGrade,
  type GoodsItem
} from '@campus/shared'
import { userDictApi, userItemApi } from '@campus/api'
import ProductCard from '@/components/ProductCard.vue'
import FilterGroup from '@/components/FilterGroup.vue'
import { toGoodsItem } from '@/utils/item-map'

type SortKey = 'default' | 'price-asc' | 'price-desc' | 'time' | 'hot'
type ViewMode = 'grid' | 'list'
type TimeRange = '' | '24h' | '3d' | '7d'

/** 关键词长度上限 50（服务端同样截断，前端先截一刀避免多发无用字符） */
const KEYWORD_MAX = 50

const route = useRoute()
const router = useRouter()

/* ── 筛选条件 ───────────────────────────────────────────── */

const keyword = ref('')
/** 一级分类 id（字典下发）。商品挂二级分类，服务端按一级展开成其下全部二级 */
const category = ref('')
const priceMin = ref<number | null>(null)
const priceMax = ref<number | null>(null)
const conditions = ref<ConditionGrade[]>([])
/** 校区名（与商品 `campusName` 同名比较，字典驱动） */
const campuses = ref<string[]>([])
const verifiedOnly = ref(false)
const creditGood = ref(false)
/** 「只看可议价 / 包校园配送」挂在价格组里（原型侧栏如此），但它们是商品属性，不是卖家属性 */
const negotiableOnly = ref(false)
const deliveryOnly = ref(false)
const timeRange = ref<TimeRange>('')

/**
 * 视图状态必须声明在下面的 watch **之前**：那个 watch 是 `immediate: true`，
 * setup 执行到它时回调会当场跑一次，此时若 `page` 还在 TDZ 就抛
 * "Cannot access 'page' before initialization" —— 整个页面白屏，控制台只有这一行。
 */
const sort = ref<SortKey>('default')
const view = ref<ViewMode>('grid')
const page = ref(1)
const pageSize = 12

/* ── 字典选项（分类 / 校区）────────────────────────────── */

/** 一级分类（parentId 缺省）。二级不出现在筛选入口，选一级时后端会展开 */
const catOptions = ref<Array<{ id: string; name: string }>>([])
const campusOptions = ref<string[]>([])

async function loadDict(): Promise<void> {
  try {
    const dict = await userDictApi.all()
    catOptions.value = dict.categories
      .filter((c) => !c.parentId)
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
      .map((c) => ({ id: c.id, name: c.name }))
    campusOptions.value = [...dict.campuses]
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
      .map((c) => c.name)
  } catch {
    // 字典拉不到时分类入口为空，商品仍可浏览——不要把整页拖进错误态
  }
}

/* ── 数据源（/api/item/list）────────────────────────────── */

const items = ref<GoodsItem[]>([])
const loading = ref(false)
const loadError = ref(false)

async function fetchItems(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    // 服务端只返在售、按发布时间倒序；50 是后端 pageSize 上限。
    // 关键词交给服务端 LIKE（title / description），前端不再本地二次过滤——
    // 本地过滤只能看到已加载的这一页，搜「教材」会漏掉第 51 件之后的结果。
    const res = await userItemApi.list({
      pageSize: 50,
      categoryId: category.value || undefined,
      keyword: keyword.value.trim().slice(0, KEYWORD_MAX) || undefined
    })
    items.value = res.list.map(toGoodsItem)
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadDict()
  void fetchItems()
})

/** 顶栏搜索与分类条是 URL 驱动的，换 URL 要同步进筛选状态；切分类要重拉（服务端过滤） */
watch(
  () => [route.query.q, route.query.cat],
  ([q, cat], old) => {
    keyword.value = (q as string) ?? ''
    category.value = (cat as string) ?? ''
    page.value = 1
    // 首次（old 为 undefined）由 onMounted 负责拉取，这里只处理后续切换
    if (old) void fetchItems()
  },
  { immediate: true }
)

/**
 * 顶栏校区下拉（`?campus=<校区名>`，'' = 全部）作为进页面时的初值。
 *
 * <p>单向：URL → 本地。侧栏多选无法用单个 query 表达，所以侧栏改动不回写 URL；
 * 两边都做同步会互相触发（侧栏清掉 query 后又被 URL 清空侧栏），得不偿失。
 * 实际生效条件以 chips 显示的为准。
 */
watch(
  () => route.query.campus,
  (v) => {
    campuses.value = v ? [v as string] : []
    page.value = 1
  },
  { immediate: true }
)

/* ── 闲鱼式首页装饰层 ───────────────────────────────────── */

/** 公告条：可关（闲鱼顶部的「网页版发布闲置功能升级啦！」） */
const notice = ref(true)

/** 纯首页（无分类 / 无关键词 / 无校区限制）才铺促销卡位；一进检索态就收掉，让位给结果 */
const isPureHome = computed(
  () => route.name === 'home' && !route.query.cat && !route.query.q && !route.query.campus
)

/** 左侧分类卡（顶栏分类条取消后的替代入口）：选项来自字典，不含「全部」——清空分类就是全部 */
const CAT_LIST = computed(() => catOptions.value)

/** 当前分类名：URL 里存的是 id，chips 与空态文案要显示人话 */
const categoryName = computed(
  () => catOptions.value.find((c) => c.id === category.value)?.name ?? ''
)

function pickCat(id: string): void {
  // 走 URL 而不是直改 category.value：与顶栏 / 促销卡的入口共用同一份 watch 同步。
  // 带着当前关键词跳，换分类不等于放弃刚才搜的词
  void router.push({
    name: 'list',
    query: { cat: id || undefined, q: keyword.value.trim() || undefined }
  })
}

/** 促销卡位按分类名跳转：卡面文案是写死的装饰层，名字要先映射回字典 id */
function pickCatByName(name: string): void {
  pickCat(catOptions.value.find((c) => c.name === name)?.id ?? '')
}

/** 促销卡位：主推卡 + 三张品类卡。文案与配色写死（装饰层），点击进对应分类 */
interface HeroCard {
  cat: string
  title: string
  sub: string
  /** 品类卡右下角的价格钩子 */
  price?: string
  /** 主推卡的按钮文案 */
  btn?: string
  cls: string
}

const HERO: HeroCard[] = [
  { cat: '', title: '校园二手好物', sub: '超绝性价比 · 省到底', btn: '去逛逛', cls: 'hero--promo' },
  { cat: '数码电子', title: '手机数码', sub: '热门装备放心入', price: '¥99 起', cls: 'hero--blue' },
  { cat: '教材教辅', title: '教材教辅', sub: '一届传给一届', price: '¥1 起', cls: 'hero--green' },
  { cat: '运动器材', title: '运动器材', sub: '挥汗如雨', price: '¥59 起', cls: 'hero--pink' }
]

/** 「猜你喜欢」快捷分类：第一个是「全部」，其余按一级分类过滤（值是 id） */
const QUICK = computed(() => [
  { label: '猜你喜欢', value: '' },
  ...catOptions.value.map((c) => ({ label: c.name, value: c.id }))
])

/** 排序胶囊组：闲鱼把排序平铺而不是藏进下拉——扫一眼就能换 */
const SORTS: { key: SortKey; label: string }[] = [
  { key: 'default', label: '综合' },
  { key: 'time', label: '最新' },
  { key: 'hot', label: '最热' },
  { key: 'price-asc', label: '价格从低到高' },
  { key: 'price-desc', label: '价格从高到低' }
]

/* ── 结果集 ─────────────────────────────────────────────── */

const filtered = computed(() => {
  const now = Date.now()

  return items.value.filter((it) => {
    // 关键词与分类不在前端比：分类的前端拿不到父级关系，关键词要在全量数据上 LIKE，
    // 两者都已由服务端过滤（见 fetchItems），这里再比一遍只会和搜索结果对不上
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

/**
 * 摘掉关键词：URL 上的 q 也要摘。
 *
 * <p>`keyword` 由 URL 单向驱动（见上面的 watch），只改本地变量列表不会重拉，
 * 用户会看到「关键词标签没了、结果还是旧词的结果」。地址栏本来就干净时直接重拉。
 */
function clearKeyword(): void {
  keyword.value = ''
  if (!route.query.q) {
    void fetchItems()
    return
  }
  const next = { ...route.query }
  delete next.q
  void router.replace({ path: route.path, query: next })
}

const chips = computed<Chip[]>(() => {
  const out: Chip[] = []
  if (keyword.value.trim())
    out.push({ key: 'kw', label: `关键词：${keyword.value}`, clear: clearKeyword })
  if (category.value)
    out.push({
      key: 'cat',
      label: categoryName.value || '已选分类',
      // 只摘分类，不顺手丢掉关键词（关键词在 URL 上，回首页会一起没）
      clear: () => router.push({ name: 'list', query: { q: keyword.value.trim() || undefined } })
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
    <!-- 公告条：白卡胶囊，可关闭 -->
    <div v-if="notice && isPureHome" class="notice cm-card">
      <span class="notice__txt">📢 网页版发布闲置功能升级啦！拖拽 9 张图 + 实时预览，快去试试</span>
      <button type="button" class="notice__x" aria-label="关闭公告" @click="notice = false">×</button>
    </div>

    <div class="body">
      <!-- ── 左侧常驻 240px：分类卡 + 筛选，sticky ──────── -->
      <aside class="side">
        <nav class="cats">
          <h4 class="cats__hd">全部分类</h4>
          <a
            v-for="c in CAT_LIST"
            :key="c.id"
            :class="{ on: category === c.id }"
            href="javascript:void(0)"
            @click="pickCat(c.id)"
          >
            {{ c.name }}
          </a>
        </nav>

        <div class="filters cm-card">
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
            <!-- 「全部」= 不限校区（campuses 为空即全部），选项来自字典，不写死 -->
            <label>
              <input type="checkbox" :checked="campuses.length === 0" @change="campuses = []" />
              全部校区
            </label>
            <label v-for="c in campusOptions" :key="c">
              <input v-model="campuses" type="checkbox" :value="c" /> {{ c }}
            </label>
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
        </div>
      </aside>

      <!-- ── 内容区 ────────────────────────────────────── -->
      <section class="content">
        <!-- 促销卡位：只在纯首页铺，检索态收掉 -->
        <div v-if="isPureHome" class="hero">
          <div
            v-for="h in HERO"
            :key="h.title"
            class="hero__card"
            :class="h.cls"
            @click="pickCatByName(h.cat)"
          >
            <div class="hero__txt">
              <strong>{{ h.title }}</strong>
              <span>{{ h.sub }}</span>
              <span v-if="h.price" class="hero__price">{{ h.price }}</span>
            </div>
            <span v-if="h.btn" class="hero__btn">{{ h.btn }}</span>
          </div>
        </div>

        <div v-if="chips.length" class="chips">
          <span v-for="c in chips" :key="c.key" class="cm-chip" @click="c.clear()">
            {{ c.label }}<i class="cm-chip__x">×</i>
          </span>
          <button type="button" class="chips__clear" @click="clearAll">清空全部</button>
        </div>

        <!-- 猜你喜欢：快捷分类胶囊，选中黄底黑字 -->
        <div class="quick">
          <button
            v-for="q in QUICK"
            :key="q.value"
            type="button"
            class="quick__item"
            :class="{ on: category === q.value }"
            @click="pickCat(q.value)"
          >
            {{ q.label }}
          </button>
        </div>

        <!-- 排序胶囊组 + 视图切换（闲鱼的筛选条形态） -->
        <div class="toolbar cm-card">
          <div class="sorts">
            <button
              v-for="s in SORTS"
              :key="s.key"
              type="button"
              class="sorts__item"
              :class="{ on: sort === s.key }"
              @click="sort = s.key"
            >
              {{ s.label }}
            </button>
          </div>

          <div class="toolbar__right">
            <span class="count">共 {{ filtered.length }} 件</span>
            <el-radio-group v-model="view" size="small">
              <el-radio-button value="grid">网格</el-radio-button>
              <el-radio-button value="list">列表</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <!-- 加载态：骨架卡占位，避免刷新时「暂无数据」闪烁 -->
        <div v-if="loading" class="grid grid--grid">
          <el-skeleton v-for="n in 8" :key="n" class="card-skeleton" :rows="4" animated />
        </div>

        <!-- 失败态：给重试，不放一句「暂无数据」让用户以为真没货 -->
        <div v-else-if="loadError" class="load-error cm-card">
          <p>商品列表加载失败</p>
          <el-button type="primary" @click="fetchItems">重新加载</el-button>
        </div>

        <!-- 空态：给可执行的放宽建议，不放一句「暂无数据」就完事 -->
        <el-empty v-else-if="!paged.length" description="没有符合条件的商品">
          <el-button type="primary" @click="relax">去掉校区与价格限制</el-button>
          <!-- 分类是服务端条件，只放宽本地条件解不开，要给「清空全部」的出口 -->
          <el-button @click="clearAll">清空全部条件</el-button>
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
  padding-top: var(--cm-space-4);
}

/* ── 公告条 ─────────────────────────────────────────────── */

.notice {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  height: 40px;
  padding: 0 var(--cm-space-4);
  margin-bottom: var(--cm-space-4);
}

.notice__txt {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.notice__x {
  margin-left: auto;
  font-size: var(--cm-font-size-lg);
  line-height: 1;
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 双栏 ───────────────────────────────────────────────── */

.body {
  display: flex;
  gap: var(--cm-space-4);
  align-items: flex-start;
}

/* ── 侧栏 240px sticky ──────────────────────────────────── */

.side {
  width: var(--cm-layout-side-w);
  flex: none;
  /* 顶栏分类条取消后，吸附点只留 nav 高度 */
  position: sticky;
  top: calc(var(--cm-layout-nav-h) + var(--cm-space-4));
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

/* 分类卡：与筛选卡分开一张，标题区是黑底黄字的闲鱼式强调 */
.cats {
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-xl);
  box-shadow: var(--cm-shadow-1);
  padding: var(--cm-space-4);
  display: flex;
  flex-direction: column;
}

.cats__hd {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-base);
  font-weight: 800;
}

.cats a {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  line-height: 2;

  &:hover {
    color: var(--cm-color-text-1);
    font-weight: 600;
  }

  &.on {
    color: var(--cm-color-text-1);
    font-weight: 700;
  }
}

.filters {
  padding: var(--cm-space-4);
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

/* ── 促销卡位 ───────────────────────────────────────────── */

.hero {
  display: grid;
  grid-template-columns: 1.4fr 1fr 1fr 1fr;
  gap: var(--cm-space-3);
  margin-bottom: var(--cm-space-4);
}

.hero__card {
  position: relative;
  min-height: 120px;
  border-radius: var(--cm-radius-2xl);
  padding: var(--cm-space-4);
  cursor: pointer;
  overflow: hidden;
  transition: transform 180ms ease-out, box-shadow 180ms ease-out;

  &:hover {
    transform: translateY(-3px);
    box-shadow: var(--cm-shadow-2);
  }
}

.hero__txt {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);

  strong {
    font-size: var(--cm-font-size-md);
    font-weight: 800;
  }

  span {
    font-size: var(--cm-font-size-xs);
    opacity: 0.82;
  }
}

.hero__price {
  font-weight: 800;
  opacity: 1 !important;
}

.hero__btn {
  position: absolute;
  left: var(--cm-space-4);
  bottom: var(--cm-space-3);
  height: 26px;
  padding: 0 var(--cm-space-3);
  line-height: 26px;
  font-size: var(--cm-font-size-xs);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-pill);
}

/* 主推卡：橙红渐变 + 白字，是整排里唯一的高饱和块（主色纪律：黄留给按钮） */
.hero--promo {
  background: linear-gradient(135deg, #ff8f1f, #ff5000);
  color: #fff;

  .hero__txt strong {
    font-size: var(--cm-font-size-lg);
  }
}

.hero--blue {
  background: linear-gradient(135deg, var(--cm-color-tag-blue-bg), #d2e9ff);
  color: var(--cm-color-tag-blue);

  .hero__txt span {
    color: var(--cm-color-text-2);
  }
}

.hero--green {
  background: linear-gradient(135deg, var(--cm-color-tag-green-bg), #d0f2e0);
  color: var(--cm-color-ok);

  .hero__txt span {
    color: var(--cm-color-text-2);
  }
}

.hero--pink {
  background: linear-gradient(135deg, var(--cm-color-tag-red-bg), #ffd6cf);
  color: var(--cm-color-price);

  .hero__txt span {
    color: var(--cm-color-text-2);
  }
}

/* ── 已选条件 chips ─────────────────────────────────────── */

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

/* ── 猜你喜欢胶囊 ───────────────────────────────────────── */

.quick {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.quick__item {
  height: 30px;
  padding: 0 var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: var(--cm-color-surface);
  border: none;
  border-radius: var(--cm-radius-pill);
  box-shadow: var(--cm-shadow-1);
  cursor: pointer;
  transition: background-color 180ms ease-out, color 180ms ease-out;

  &:hover {
    color: var(--cm-color-text-1);
  }

  &.on {
    font-weight: 700;
    color: var(--cm-color-text-1);
    background: var(--cm-color-brand);
  }
}

/* ── 排序工具条 ─────────────────────────────────────────── */

.toolbar {
  display: flex;
  align-items: center;
  height: 48px;
  padding: 0 var(--cm-space-4);
  margin-bottom: var(--cm-space-4);
}

.sorts {
  display: flex;
  align-items: center;
  gap: var(--cm-space-1);
}

.sorts__item {
  height: 28px;
  padding: 0 var(--cm-space-3);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }

  &.on {
    font-weight: 700;
    color: var(--cm-color-text-1);
    background: var(--cm-color-bg);
  }
}

.count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.toolbar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

/* ── 结果网格 ───────────────────────────────────────────── */

/* 网格：auto-fill + minmax 让 lg 出 4 列、xl 出 5–6 列（design/01 §3.1） */
.grid {
  display: grid;
  gap: var(--cm-space-4);
}

.card-skeleton {
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-xl);
  padding: var(--cm-space-4);
}

.load-error {
  padding: var(--cm-space-8);
  text-align: center;
  color: var(--cm-color-text-2);
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

  .hero {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
