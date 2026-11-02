<script setup lang="ts">
/**
 * M1 首页信息流（design/02 §4.1，本端最重要的一页）
 *
 * <p>原则 1「逛得下去」：用户第 3 秒内要看到至少 4 件商品，不能先看到搜索框和分类。
 * 所以瀑布流一上来就有数据，头部只占一屏的一小块，不做开屏弹窗。
 *
 * <p>瀑布流双列：左右两列**不等高是允许的**，但同一列内必须对齐——所以按索引奇偶
 * 静态分列（而不是按高度动态插入），否则上拉加载时已有卡片会跳列。
 *
 * <p>数据来自 `/api/item/list`（免登录，在售按发布时间倒序）。服务端分页 + 上拉加载；
 * 后端列表只支持 keyword/categoryId/campusId，价格与成色筛选未参数化之前不做（web 端同边界）。
 *
 * <p>2026-10-28 视觉改版：顶部改成「暖色渐变 + 问候」的完整头部，白色搜索框与金刚区
 * 各自成层压在渐变上；删掉右下发布 FAB（底部导航中间已有发布钮），改为滚动后浮现的
 * 「回到顶部」胶囊，避免两个发布入口打架。
 */

import { computed, ref } from 'vue'
import { onPageScroll, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import type { GoodsItem } from '@campus/shared'
import { userDictApi, userItemApi } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import { getSession } from '@/utils/session'
import { goTab } from '@/utils/tab-nav'
import GoodsCard from '@/components/GoodsCard.vue'
import CmIcon from '@/components/CmIcon.vue'
import CmNav from '@/components/CmNav.vue'
import TabBar from '@/components/TabBar.vue'
import DevLoginSwitcher from '@/components/DevLoginSwitcher.vue'
import { pendingCategoryId } from '@/utils/nav-intent'

/** dev 构建才挂载测试账号切换器（左侧停靠，避开底部导航） */
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
  票务卡券: 'amber',
  其他闲置: 'gray'
}

/**
 * 金刚区图标：用 emoji 而不是首字方块。
 *
 * <p>首字方块只能区分「教 / 数 / 生」这类同字头的分类，扫视时仍要读一遍字；
 * 图形是先认形再认字，2×4 的九宫格里一眼就能定位。键是分类名，字典里新增的
 * 分类回落成标签图形，不至于空一块。
 */
const EMOJI: Record<string, string> = {
  教材教辅: '📚',
  数码电子: '💻',
  生活用品: '🧺',
  服饰鞋包: '👗',
  运动器材: '🏀',
  美妆个护: '💄',
  票务卡券: '🎫',
  其他闲置: '📦'
}

const FALLBACK_EMOJI = '🏷️'

interface KkEntry {
  id: string
  name: string
  emoji: string
  hue: string
}

const cats = ref<{ id: string; name: string }[]>([])
/** 字典就绪（含失败）后才渲染金刚区：否则 8 个分类异步到达会分两波入场 */
const catsReady = ref(false)

/** 只有真实分类：底部导航已有「分类」入口、「发布」另有中央按钮，金刚区不再放重复入口 */
const kkEntries = computed<KkEntry[]>(() =>
  cats.value.map((c) => ({
    id: c.id,
    name: c.name,
    emoji: EMOJI[c.name] ?? FALLBACK_EMOJI,
    hue: HUE[c.name] ?? 'gray'
  }))
)

async function fetchCats(): Promise<void> {
  try {
    const dict = await userDictApi.all()
    // 后端契约：一级分类 parentId 置 null（库里是 0，Jackson non_null 后键消失）
    cats.value = dict.categories.filter((c) => !c.parentId)
  } catch {
    // 金刚区加载失败不阻塞信息流：整块隐藏，商品流照常
  } finally {
    catsReady.value = true
  }
}

function onKk(e: KkEntry): void {
  // reLaunch 不能带参，意图走模块级引用，分类页 onShow 消费后清空
  pendingCategoryId.value = e.id
  goTab('category')
}

const channel = ref(CHANNELS[0])
const pageSize = 10
const page = ref(1)
const total = ref(0)
const items = ref<GoodsItem[]>([])
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)

/** 滚动深度：只用来显隐「回到顶部」，不再驱动吸顶（顶部已无吸顶栏） */
const scrollTop = ref(0)

onPageScroll((e: { scrollTop: number }) => {
  scrollTop.value = e.scrollTop
})

const showTop = computed(() => scrollTop.value > 800)
const hasMore = computed(() => items.value.length < total.value)

/** 问候语按时段切换，让首屏有一点「今天」的现场感 */
const greeting = computed(() => {
  const h = new Date().getHours()
  const who = getSession().nickname
  const hi = h < 6 ? '夜深了' : h < 11 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好'
  return who ? `${hi}，${who}` : `${hi}，同学`
})

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
  void uni.navigateTo({ url: '/pages/search/index' })
}

/** 顶栏「消息」进会话列表 */
function openChat(): void {
  goTab('chat')
}

/** 广场是普通页面（非 tab），用 navigateTo 保留首页返回栈 */
function openMoments(): void {
  void uni.navigateTo({ url: '/pages/moments/index' })
}

function goPublish(): void {
  goTab('publish')
}

function goTop(): void {
  void uni.pageScrollTo({ scrollTop: 0, duration: 320 })
}

void fetchPage(true)
void fetchCats()
</script>

<template>
  <view class="cm-page cm-page--tab">
    <!-- 原生导航栏已关闭：渐变头部自己顶到屏幕最上沿，只留状态栏占位 -->
    <CmNav variant="pad" />

    <!-- ① 暖色渐变头部：问候 + 搜索，白色搜索框是这一层唯一的高光 -->
    <view class="hero">
      <view class="hero__deco" />
      <view class="hero__row">
        <view class="hero__hi">
          <text class="hero__title">{{ greeting }}</text>
          <text class="hero__sub">校园里 {{ total }} 件闲置正在等新主人</text>
        </view>
        <view class="hero__acts">
          <view class="hero__act" hover-class="hero__act--on" @click="openMoments">
            <CmIcon name="chat" size="sm" tone="ink" />
            <text>广场</text>
          </view>
          <view class="hero__act" hover-class="hero__act--on" @click="openChat">
            <CmIcon name="bell" size="sm" tone="ink" />
            <text>消息</text>
          </view>
        </view>
      </view>

      <view class="search" hover-class="search--on" @click="openSearch">
        <CmIcon name="search" size="sm" tone="muted" />
        <text class="search__ph">搜教材、耳机、自行车…</text>
        <view class="search__go">搜索</view>
      </view>
    </view>

    <!-- ② 金刚区：白色圆角卡压在渐变上，形成第一层浮起 -->
    <view v-if="catsReady && kkEntries.length" class="kk cm-card cm-in">
      <view
        v-for="(e, i) in kkEntries"
        :key="e.id"
        class="kk__item"
        hover-class="kk__item--press"
        :hover-stay-time="60"
        :style="{ animationDelay: 60 + i * 35 + 'ms' }"
        @click="onKk(e)"
      >
        <view class="kk__tile" :class="'kk__tile--' + e.hue">
          <text class="kk__emoji">{{ e.emoji }}</text>
        </view>
        <text class="kk__label">{{ e.name }}</text>
      </view>
    </view>

    <!-- ③ 面交保障位：给「一屏全是商品」的单调加一段节奏 -->
    <view class="promo cm-card cm-in" style="animation-delay: 220ms">
      <view class="promo__main">
        <text class="promo__t">面交更安心</text>
        <view class="promo__tags">
          <text class="promo__tag">当面验货</text>
          <text class="promo__tag">免邮费</text>
          <text class="promo__tag">校内自提</text>
        </view>
      </view>
      <view class="promo__cta" hover-class="promo__cta--on" @click="goPublish">
        <text>去发布</text>
        <CmIcon name="arrow" size="sm" tone="white" />
      </view>
      <view class="promo__blur promo__blur--a" />
      <view class="promo__blur promo__blur--b" />
    </view>

    <!-- ④ 频道 chips：横向滑动，选中态渐变填充 -->
    <scroll-view class="chips" scroll-x :show-scrollbar="false">
      <view class="chips__in">
        <view
          v-for="ch in CHANNELS"
          :key="ch"
          class="chip"
          :class="{ 'chip--on': channel === ch }"
          hover-class="chip--press"
          @click="channel = ch"
        >
          <text>{{ ch }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- ⑤ 推荐区标题 -->
    <view class="cm-sect">
      <view class="cm-sect__bar" />
      <text class="cm-sect__t">为你推荐</text>
      <text class="cm-sect__sub">{{ channel === CHANNELS[0] ? '按发布时间排序' : channel }}</text>
    </view>

    <!-- ⑥ 双列瀑布流 -->
    <view v-if="!loaded && loading" class="falls">
      <view class="col">
        <view v-for="n in 3" :key="'ls' + n" class="cm-skel skel" />
      </view>
      <view class="col">
        <view v-for="n in 3" :key="'rs' + n" class="cm-skel skel" />
      </view>
    </view>

    <view v-else-if="loadError" class="cm-state">
      <CmIcon name="clock" size="lg" tone="muted" />
      <text class="cm-state__t">加载失败，拉一下试试</text>
    </view>

    <view v-else-if="!filtered.length" class="cm-state">
      <CmIcon name="box" size="lg" tone="muted" />
      <text class="cm-state__t">{{ channel === CHANNELS[0] ? '暂时没有在售商品' : '这个频道还没有商品' }}</text>
      <text class="cm-state__s">换个频道看看，或者自己发一件</text>
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
      <view v-if="loading" class="loadmore__dots">
        <view class="loadmore__dot" />
        <view class="loadmore__dot" />
        <view class="loadmore__dot" />
      </view>
      <text v-else class="loadmore__t">{{ hasMore ? '上拉加载更多' : '没有更多了' }}</text>
    </view>

    <!-- 回到顶部：滚动 800rpx 后才浮现，缩放入场 -->
    <view class="totop" :class="{ 'totop--on': showTop }" @click="goTop">
      <CmIcon name="arrow" size="sm" tone="ink" />
    </view>

    <DevLoginSwitcher v-if="isDev" position="left" />
    <TabBar active="home" />
  </view>
</template>

<style scoped lang="scss">
/* ── ① 渐变头部 ─────────────────────────────────────────── */
.hero {
  position: relative;
  padding: $cm-space-5 $cm-space-4 $cm-space-6;
  background-image: linear-gradient(165deg, #FFE3C9 0%, #FFF3E8 46%, $cm-color-bg 100%);
  overflow: hidden;
}

/* 右上角暖色光斑：纯装饰，让渐变不是一条直直的灰白 */
.hero__deco {
  position: absolute;
  right: -120rpx;
  top: -140rpx;
  width: 380rpx;
  height: 380rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 138, 61, 0.32), rgba(255, 138, 61, 0));
}

.hero__row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: $cm-space-3;
}

.hero__hi {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.hero__title {
  font-size: $cm-font-size-xl;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
  letter-spacing: 0.6rpx;
}

.hero__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand-text;
}

.hero__acts {
  display: flex;
  gap: $cm-space-2;
  flex: none;
}

.hero__act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  width: 76rpx;
  padding: 8rpx 0;
  border-radius: $cm-radius-lg;
  font-size: 20rpx;
  color: $cm-color-text-2;
  background: rgba(255, 255, 255, 0.55);
  transition: transform 140ms ease-out, background 140ms ease-out;
}

.hero__act--on {
  transform: scale(0.92);
  background: rgba(255, 255, 255, 0.9);
}

/* 搜索框：白底 + 软阴影，头部唯一的实心亮面 */
.search {
  position: relative;
  margin-top: $cm-space-4;
  height: 84rpx;
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  padding: 0 $cm-space-2 0 $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
  box-shadow: $cm-shadow-md;
  transition: transform 140ms ease-out;
}

.search--on {
  transform: scale(0.985);
}

.search__ph {
  flex: 1;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.search__go {
  height: 60rpx;
  padding: 0 $cm-space-4;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
}

/* ── ② 金刚区 ───────────────────────────────────────────── */
.kk {
  position: relative;
  margin: -$cm-space-4 $cm-space-4 0;
  padding: $cm-space-4 $cm-space-1 $cm-space-1;
  display: flex;
  flex-wrap: wrap;
}

/* 4 列 × 2 行：8 个分类正好排满，不留半行空格 */
.kk__item {
  width: 25%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding-bottom: $cm-space-3;
  animation: cmInUp 380ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

/* 图标底：浅色瓦片托住 emoji。emoji 自带颜色，瓦片再上饱和渐变就会互相打架 */
.kk__tile {
  position: relative;
  width: 104rpx;
  height: 104rpx;
  border-radius: $cm-radius-2xl;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 140ms ease-out;
}

.kk__item--press .kk__tile {
  transform: scale(0.88);
}

/* emoji 走系统彩色字体，不继承文字色；行高给 1 避免上下抖动 */
.kk__emoji {
  font-size: 50rpx;
  line-height: 1;
  font-family: 'Apple Color Emoji', 'Segoe UI Emoji', 'Noto Color Emoji', sans-serif;
}

.kk__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kk__tile--blue {
  background: #EAF3FF;
}

.kk__tile--purple {
  background: #F2EDFF;
}

.kk__tile--green {
  background: #E6F8F0;
}

.kk__tile--orange {
  background: #FFF1E4;
}

.kk__tile--red {
  background: #FFECEC;
}

.kk__tile--rose {
  background: #FFEDF2;
}

.kk__tile--amber {
  background: #FFF6E3;
}

.kk__tile--gray {
  background: #F1F1F5;
}

/* ── ③ 面交保障位 ───────────────────────────────────────── */
.promo {
  position: relative;
  margin: $cm-space-3 $cm-space-4 0;
  padding: $cm-space-4;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  background-image: linear-gradient(120deg, #FFF4EA 0%, #FFECF0 100%);
  overflow: hidden;
}

.promo__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  position: relative;
  z-index: 1;
}

.promo__t {
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-heavy;
  color: #7A3B12;
}

.promo__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.promo__tag {
  height: 36rpx;
  line-height: 36rpx;
  padding: 0 12rpx;
  border-radius: $cm-radius-pill;
  font-size: 20rpx;
  color: $cm-color-brand-text;
  background: rgba(255, 255, 255, 0.8);
}

.promo__cta {
  position: relative;
  z-index: 1;
  flex: none;
  height: 62rpx;
  padding: 0 $cm-space-3 0 $cm-space-4;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: 0 6rpx 14rpx rgba(255, 106, 0, 0.28);
  transition: transform 140ms ease-out;
}

.promo__cta--on {
  transform: scale(0.95);
}

/* 两个虚化光斑：纯装饰，给浅色卡一点纵深 */
.promo__blur {
  position: absolute;
  border-radius: 50%;
  opacity: 0.55;
}

.promo__blur--a {
  right: -60rpx;
  top: -70rpx;
  width: 220rpx;
  height: 220rpx;
  background: radial-gradient(circle, rgba(255, 154, 77, 0.5), rgba(255, 154, 77, 0));
}

.promo__blur--b {
  right: 120rpx;
  bottom: -90rpx;
  width: 180rpx;
  height: 180rpx;
  background: radial-gradient(circle, rgba(255, 77, 109, 0.35), rgba(255, 77, 109, 0));
}

/* ── ④ chips ────────────────────────────────────────────── */
.chips {
  padding: $cm-space-4 0 $cm-space-1;
  white-space: nowrap;
}

.chips__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.chip {
  flex: none;
  white-space: nowrap;
  padding: 0 $cm-space-4;
  height: 60rpx;
  display: flex;
  align-items: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
  border: 1rpx solid $cm-color-border;
  transition: background 180ms ease-out, color 180ms ease-out, border-color 180ms ease-out,
    transform 140ms ease-out, box-shadow 180ms ease-out;
}

.chip--press {
  transform: scale(0.95);
}

/* 选中：渐变填充 + 轻微上浮 + 同色投影，是页面上第二个「亮起来」的元素 */
.chip--on {
  color: #fff;
  font-weight: $cm-font-weight-bold;
  border-color: transparent;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: 0 8rpx 16rpx -8rpx rgba(255, 106, 0, 0.8);
  transform: translateY(-2rpx);
}

/* ── ⑥ 瀑布流 ───────────────────────────────────────────── */
.falls {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3 $cm-space-4 $cm-space-1;
  align-items: flex-start;
}

.col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

/* 新加载卡片淡入：只动 opacity——transform 会与卡片按压缩放抢占层叠 */
.falls .card {
  animation: cmInUp 300ms ease-out both;
}

.skel {
  height: 470rpx;
}

/* ── 加载更多 ───────────────────────────────────────────── */
.loadmore {
  padding: $cm-space-5 0 $cm-space-3;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loadmore__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.loadmore__dots {
  display: flex;
  gap: 8rpx;
}

/* 三点呼吸：比「加载中…」更轻，不打断浏览 */
.loadmore__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $cm-color-gray-400;
  animation: dotPulse 1s ease-in-out infinite;
}

.loadmore__dot:nth-child(2) {
  animation-delay: 0.15s;
}

.loadmore__dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dotPulse {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

/* ── 回到顶部 ───────────────────────────────────────────── */
.totop {
  position: fixed;
  right: $cm-space-4;
  /* 底部导航 116rpx + 安全区再往上抬一点，避免压住中央发布钮 */
  bottom: calc(#{$cm-layout-tabbar-h} + 40rpx + env(safe-area-inset-bottom));
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: $cm-shadow-md;
  opacity: 0;
  transform: translateY(24rpx) scale(0.7);
  pointer-events: none;
  transition: opacity 220ms ease-out, transform 260ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* 图标本身是朝右的折线，转 -90° 变成朝上 */
.totop :deep(.i) {
  transform: rotate(-90deg) scale(0.86);
}

.totop--on {
  opacity: 1;
  transform: translateY(0) scale(1);
  pointer-events: auto;
}
</style>
