<script setup lang="ts">
/**
 * M1 首页信息流（design/02 §4.1，本端最重要的一页）
 *
 * <p>原则 1「逛得下去」：用户第 3 秒内要看到至少 4 件商品，不能先看到搜索框和分类。
 * 所以瀑布流一上来就有数据，金刚区只占一屏的头部，不做开屏弹窗。
 *
 * <p>瀑布流双列：左右两列**不等高是允许的**，但同一列内必须对齐——所以按索引奇偶
 * 静态分列（而不是按高度动态插入），否则上拉加载时已有卡片会跳列。
 *
 * <p>数据来自 `/api/item/list`（免登录，在售按发布时间倒序）。服务端分页 + 上拉加载；
 * 后端列表只支持 keyword/categoryId/campusId，价格与成色筛选未参数化之前不做（web 端同边界）。
 *
 * <p>金刚区（2026-10-24 参考美团重做，design/02 §5.3 已同步）：分类走字典接口不硬编码
 * （tech/02 §七），实色图标 + 白字，点击带意图跳分类页并选中。
 */

import { computed, onMounted, ref } from 'vue'
import { onPageScroll, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import type { GoodsItem } from '@campus/shared'
import { userDictApi, userItemApi } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import GoodsCard from '@/components/GoodsCard.vue'
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'
import { pendingCategoryId } from '@/utils/nav-intent'

/** dev 构建才挂载测试账号切换器（左侧停靠，避开右下发布 FAB） */
const isDev = import.meta.env.DEV

/** 频道 chips：原型给的兴趣频道，前端本地过滤标题（服务端无此维度） */
const CHANNELS = ['猜你喜欢', '教材专区', '数码好价', '毕业清仓', '同城面交', '女生闲置']

/** 金刚区色相：按分类名固定映射；字典里出现未登记的分类回落 gray，不引入令牌外色值 */
const HUE: Record<string, string> = {
  教材教辅: 'blue',
  数码电子: 'purple',
  生活用品: 'green',
  服饰鞋包: 'rose',
  运动器材: 'orange',
  美妆个护: 'red',
  票务卡券: 'blue',
  其他闲置: 'gray'
}

interface KkEntry {
  key: string
  name: string
  char: string
  hue: string
  kind: 'cat' | 'all' | 'publish'
  id?: string
}

const cats = ref<{ id: string; name: string }[]>([])
/** 字典就绪（含失败）后才渲染金刚区：否则 8 个分类异步到达会和固定入口分两波入场 */
const catsReady = ref(false)

/** 8 个真实分类 + 全部分类（描边弱化）+ 发布（品牌色强化）= 10 格，2×5 排满 */
const kkEntries = computed<KkEntry[]>(() => [
  ...cats.value.map((c) => ({
    key: `cat-${c.id}`,
    name: c.name,
    char: c.name.slice(0, 1),
    hue: HUE[c.name] ?? 'gray',
    kind: 'cat' as const,
    id: c.id
  })),
  { key: 'all', name: '全部分类', char: '类', hue: 'outline', kind: 'all' as const },
  { key: 'publish', name: '发布', char: '＋', hue: 'brand', kind: 'publish' as const }
])

async function fetchCats(): Promise<void> {
  try {
    const dict = await userDictApi.all()
    // 后端契约：一级分类 parentId 置 null（库里是 0，Jackson non_null 后键消失）
    cats.value = dict.categories.filter((c) => !c.parentId)
  } catch {
    // 金刚区加载失败不阻塞信息流：只剩「全部分类 / 发布」两个固定入口
  } finally {
    catsReady.value = true
  }
}

function onKk(e: KkEntry): void {
  if (e.kind === 'publish') {
    uni.switchTab({ url: '/pages/publish/index' })
    return
  }
  // switchTab 不能带参，意图走模块级引用，分类页 onShow 消费后清空
  pendingCategoryId.value = e.kind === 'cat' ? (e.id ?? '') : ''
  uni.switchTab({ url: '/pages/category/index' })
}

const channel = ref(CHANNELS[0])
const pageSize = 10
const page = ref(1)
const total = ref(0)
const items = ref<GoodsItem[]>([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)

/** 顶栏吸留分隔线 + FAB 显隐：滚动即隐、停 300ms 淡入（design/02 §5.5） */
const stuck = ref(false)
const fabShown = ref(true)
let fabTimer: ReturnType<typeof setTimeout> | undefined

onPageScroll((e: { scrollTop: number }) => {
  stuck.value = e.scrollTop > 4
  fabShown.value = false
  clearTimeout(fabTimer)
  fabTimer = setTimeout(() => {
    fabShown.value = true
  }, 300)
})

const hasMore = computed(() => items.value.length < total.value)

/** 频道是前端本地过滤（标题关键词），命中为空时回落全量提示 */
const filtered = computed(() => {
  if (channel.value === CHANNELS[0] || !CHANNELS.includes(channel.value)) return items.value
  const kw = channel.value.slice(0, 2)
  return items.value.filter((it) => `${it.title}${it.description}`.includes(kw))
})

/** 双列分列：奇偶切分，保证上拉加载时不重排 */
const leftColumn = computed(() => filtered.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => filtered.value.filter((_, i) => i % 2 === 1))

async function fetchPage(reset: boolean): Promise<void> {
  if (loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  loadError.value = false
  const p = reset ? 1 : page.value + 1
  try {
    const res = await userItemApi.list({ pageNum: p, pageSize })
    const mapped = res.list.map(toGoodsItem)
    if (reset) items.value = mapped
    else items.value.push(...mapped)
    page.value = p
    total.value = res.total
    loaded.value = true
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onReachBottom(() => {
  void fetchPage(false)
})

onPullDownRefresh(() => {
  void fetchPage(true).then(() => uni.stopPullDownRefresh())
})

function openSearch(): void {
  uni.showToast({ title: '搜索页待建（M7）', icon: 'none' })
}

/** 顶栏「消息」进会话列表（tabBar 页用 switchTab） */
function openChat(): void {
  uni.switchTab({ url: '/pages/chat/list' })
}

/** 广场是普通页面（非 tabBar），用 navigateTo 保留首页返回栈 */
function openMoments(): void {
  void uni.navigateTo({ url: '/pages/moments/index' })
}

function goPublish(): void {
  // 发布是 tabBar 页，必须用 switchTab
  uni.switchTab({ url: '/pages/publish/index' })
}

void fetchPage(true)
void fetchCats()
</script>

<template>
  <view class="page">
    <!-- 吸顶搜索栏：滚过阈值后出 hairline，与下方灰色内容区分割 -->
    <view class="searchbar" :class="{ 'searchbar--stuck': stuck }">
      <view class="searchbar__box" hover-class="searchbar__box--active" @click="openSearch">
        <view class="searchbar__icon" />
        <text class="searchbar__ph">搜闲置、教材、数码…</text>
      </view>
      <view class="searchbar__msg" hover-class="searchbar__msg--active" @click="openMoments">
        <text>广场</text>
      </view>
      <view class="searchbar__msg" hover-class="searchbar__msg--active" @click="openChat">
        <text>消息</text>
      </view>
    </view>

    <!-- 金刚区：2×5 = 8 个字典分类 + 全部分类 + 发布（design/02 §5.3） -->
    <view v-if="catsReady" class="kingkong">
      <view class="kingkong__in">
        <view
          v-for="(e, i) in kkEntries"
          :key="e.key"
          class="kk"
          hover-class="kk--press"
          :hover-stay-time="60"
          :style="{ animationDelay: i * 40 + 'ms' }"
          @click="onKk(e)"
        >
          <view class="kk__icon" :class="'kk__icon--' + e.hue">
            <text class="kk__char">{{ e.char }}</text>
          </view>
          <text class="kk__label">{{ e.name }}</text>
        </view>
      </view>
    </view>

    <!-- 兴趣频道 chips：横向滑动，不吸顶 -->
    <scroll-view class="chips" scroll-x>
      <view class="chips__in">
        <text
          v-for="ch in CHANNELS"
          :key="ch"
          class="chip"
          :class="{ 'chip--on': channel === ch }"
          @click="channel = ch"
        >
          {{ ch }}
        </text>
      </view>
    </scroll-view>

    <!-- 双列瀑布流 -->
    <view v-if="!loaded && loading" class="falls">
      <view class="col">
        <view v-for="n in 3" :key="'ls' + n" class="skel" />
      </view>
      <view class="col">
        <view v-for="n in 3" :key="'rs' + n" class="skel" />
      </view>
    </view>

    <view v-else-if="loadError" class="state">
      <text class="state__t">加载失败，拉一下试试</text>
    </view>

    <view v-else-if="!filtered.length" class="state">
      <text class="state__t">{{ channel === CHANNELS[0] ? '暂时没有在售商品' : '这个频道还没有商品' }}</text>
    </view>

    <view v-else class="falls">
      <view class="col">
        <GoodsCard v-for="it in leftColumn" :key="it.id" :item="it" />
      </view>
      <view class="col">
        <GoodsCard v-for="it in rightColumn" :key="it.id" :item="it" />
      </view>
    </view>

    <view class="loadmore">
      <text>{{ loading ? '加载中…' : hasMore ? '上拉加载更多' : '没有更多了' }}</text>
    </view>

    <!-- 悬浮发布按钮：56pt=112rpx，右下，避开安全区；滚动隐藏、停 300ms 淡入 -->
    <view class="fab" :class="{ 'fab--hide': !fabShown }" hover-class="fab--active" @click="goPublish">⊕</view>

    <DevLoginSwitcher v-if="isDev" position="left" />
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: 120rpx;
}

/* ── 吸顶搜索 ───────────────────────────────────────────── */

.searchbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-2 $cm-space-4;
  background: $cm-color-surface;
}

.searchbar__box {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
  background: $cm-color-bg;
  border-radius: $cm-radius-pill;
  transition: transform 120ms ease-out;
}

.searchbar__box--active {
  transform: scale(0.99);
}

/* 放大镜图标：纯 CSS 两笔画（圆 + 斜柄），不引图标库 */
.searchbar__icon {
  width: 26rpx;
  height: 26rpx;
  border: 3rpx solid $cm-color-text-3;
  border-radius: 50%;
  position: relative;
  flex: none;
}

.searchbar__icon::after {
  content: '';
  position: absolute;
  right: -10rpx;
  bottom: -6rpx;
  width: 12rpx;
  height: 3rpx;
  background: $cm-color-text-3;
  border-radius: 2rpx;
  transform: rotate(45deg);
}

.searchbar__ph {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.searchbar__msg {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  padding: $cm-space-1 $cm-space-2;
}

.searchbar__msg--active {
  opacity: 0.6;
}

/* 滚过阈值：白底搜索栏压在灰色内容上时需要一条 hairline 交代边界 */
.searchbar--stuck {
  border-bottom: 1rpx solid $cm-color-border;
}

/* ── 金刚区（美团式：实色圆角方 + 白字，色相按分类走令牌） ───── */

.kingkong {
  background: $cm-color-surface;
  padding: $cm-space-5 $cm-space-4 $cm-space-2;
}

.kingkong__in {
  display: flex;
  flex-wrap: wrap;
}

/* 入场：错峰上浮 + 淡入，每格晚 40ms。动画挂 .kk、按压 transform 在子节点，互不抢占 */
.kk {
  width: 20%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $cm-space-2;
  padding-bottom: $cm-space-4;
  animation: kkIn 320ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes kkIn {
  from {
    opacity: 0;
    transform: translateY(24rpx);
  }
}

.kk__icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: $cm-radius-xl;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 120ms ease-out;
}

/* 按下反馈：图标缩到 0.9 快速回弹（整格可点，label 不参与缩放） */
.kk--press .kk__icon {
  transform: scale(0.9);
}

.kk__char {
  font-size: $cm-font-size-lg;
  font-weight: 600;
  color: #fff;
  line-height: 1;
}

/* 色相：值全部取自设计令牌（编译期解析，mp-weixin 无运行时 var() 风险）；
   未登记分类在脚本层已回落 gray */
.kk__icon--blue {
  background: $cm-color-blue-500;
}

.kk__icon--purple {
  background: $cm-color-purple-500;
}

.kk__icon--green {
  background: $cm-color-green-500;
}

.kk__icon--orange {
  background: $cm-color-orange-500;
}

.kk__icon--red {
  background: $cm-color-red-500;
}

.kk__icon--rose {
  background: $cm-color-price;
}

.kk__icon--gray {
  background: $cm-color-gray-600;
}

.kk__icon--brand {
  background: $cm-color-brand;
}

/* 全部分类：白底描边，与实色分类拉开层级；发布走 brand 实色做终点强调 */
.kk__icon--outline {
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-gray-300;
}

.kk__icon--outline .kk__char {
  color: $cm-color-gray-600;
}

.kk__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ── chips ──────────────────────────────────────────────── */

.chips {
  padding: $cm-space-3 0;
  background: $cm-color-bg;
  white-space: nowrap;
}

.chips__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.chip {
  padding: 0 $cm-space-4;
  height: 56rpx;
  line-height: 54rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
  /* 透明边框占位：选中态加 1rpx 实线时不再撑动布局（以前选中会跳 2rpx） */
  border: 1rpx solid transparent;
  transition: background 160ms ease-out, color 160ms ease-out, border-color 160ms ease-out;
}

.chip--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-color: $cm-color-brand;
  font-weight: 600;
}

/* ── 瀑布流 ─────────────────────────────────────────────── */

.falls {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3 $cm-space-4;
  align-items: flex-start;
}

.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

/* 新加载卡片淡入：只动 opacity——transform 会与 .card--active 的按压缩放抢占层叠 */
.falls .card {
  animation: cardIn 240ms ease-out both;
}

@keyframes cardIn {
  from {
    opacity: 0;
  }
}

/* 骨架卡：图片区 + 两行文字的占位块 */
.skel {
  height: 480rpx;
  border-radius: $cm-radius-xl;
  background: linear-gradient(90deg, $cm-color-bg 25%, #fff 50%, $cm-color-bg 75%);
  background-size: 200% 100%;
  animation: skel 1.2s ease-in-out infinite;
}

@keyframes skel {
  0% {
    background-position: 200% 0;
  }

  100% {
    background-position: -200% 0;
  }
}

.state {
  padding: 120rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.loadmore {
  padding: $cm-space-5 0;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 悬浮发布按钮 ───────────────────────────────────────── */

.fab {
  position: fixed;
  right: $cm-space-5;
  /* 20pt + 安全区（design/02 §5.5） */
  bottom: calc(48rpx + env(safe-area-inset-bottom));
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: $cm-color-brand;
  color: #fff;
  font-size: 56rpx;
  line-height: 108rpx;
  text-align: center;
  /* 不用阴影：小程序语义层没有 shadow 令牌（tech/02 §5「小程序不用阴影」），
     悬浮感靠圆形 + 主色 + 与内容拉开间距来做 */
  transition: transform 120ms ease-out, opacity 200ms ease-out;
  pointer-events: auto;
}

.fab--active {
  transform: scale(0.97);
}

/* 滚动中隐藏：缩 + 淡出，且不可点（design/02 §5.5） */
.fab--hide {
  opacity: 0;
  transform: scale(0.6);
  pointer-events: none;
}
</style>
