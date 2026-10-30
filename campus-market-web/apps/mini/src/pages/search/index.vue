<script setup lang="ts">
/**
 * M7 搜索（notes/24 第二节）
 *
 * <p>没有独立搜索接口：结果直接走 `/api/item/list?keyword=`，与其他筛选共用一处分页与排序
 * （在售、发布时间倒序），所以这里搜到的与 Web 端 `/list?keyword=` 是同一份数据。
 *
 * <p>两态而不是三态：`active` 为空 = 未搜索（历史 + 热门分类），非空 = 结果态。清空输入
 * 会退回未搜索态而不是留在一张空列表上——空列表在搜索页是最没信息量的画面。
 *
 * <p>「热门分类」不是热搜：本期不做词频统计，这里展示的就是一级分类字典，
 * 点进去按分类浏览（分类页是 tab 页，走 reLaunch + 意图传递）。
 */

import { computed, nextTick, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import type { GoodsItem } from '@campus/shared'
import { userDictApi, userItemApi } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import { goTab } from '@/utils/tab-nav'
import { pendingCategoryId } from '@/utils/nav-intent'
import GoodsCard from '@/components/GoodsCard.vue'
import CmIcon from '@/components/CmIcon.vue'

/** 历史存本地，键名与 notes/24 一致 */
const HISTORY_KEY = 'searchHistory'
const MAX_HISTORY = 10
/** 与服务端 `ItemService.MAX_KEYWORD` 对齐，前端先截一刀少发无用字符 */
const MAX_KEYWORD = 50
const HOT_COUNT = 8
const PAGE_SIZE = 10

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight ?? 0

const kw = ref('')
/** 已提交的关键词；空 = 未搜索态 */
const active = ref('')
const focus = ref(true)
const history = ref<string[]>([])
const cats = ref<{ id: string; name: string }[]>([])

const items = ref<GoodsItem[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const loaded = ref(false)
const loadError = ref(false)

const hasMore = computed(() => items.value.length < total.value)
const leftColumn = computed(() => items.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => items.value.filter((_, i) => i % 2 === 1))

function cut(s: string): string {
  return s.trim().slice(0, MAX_KEYWORD)
}

/* ── 本地历史 ───────────────────────────────────────────── */

function loadHistory(): void {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY)
    history.value = Array.isArray(raw) ? raw.filter((x): x is string => typeof x === 'string') : []
  } catch {
    history.value = []
  }
}

/** 重复的词提到最前而不是新增一条 */
function pushHistory(word: string): void {
  history.value = [word, ...history.value.filter((x) => x !== word)].slice(0, MAX_HISTORY)
  uni.setStorageSync(HISTORY_KEY, history.value)
}

function removeWord(word: string): void {
  history.value = history.value.filter((x) => x !== word)
  uni.setStorageSync(HISTORY_KEY, history.value)
}

function clearHistory(): void {
  uni.showModal({
    title: '清空搜索历史',
    content: '只清本机记录，不影响搜索结果',
    success: (res) => {
      if (!res.confirm) return
      history.value = []
      uni.removeStorageSync(HISTORY_KEY)
    }
  })
}

/* ── 字典（热门分类） ───────────────────────────────────── */

async function fetchCats(): Promise<void> {
  try {
    const dict = await userDictApi.all()
    // 一级分类 parentId 置 null（库里是 0，Jackson non_null 后键消失）
    cats.value = dict.categories
      .filter((c) => !c.parentId)
      .slice(0, HOT_COUNT)
      .map((c) => ({ id: c.id, name: c.name }))
  } catch {
    // 热门区拿不到不影响搜索：只留历史与输入框
  }
}

/* ── 结果 ───────────────────────────────────────────────── */

async function fetchPage(reset: boolean): Promise<void> {
  if (!active.value || loading.value) return
  if (!reset && !hasMore.value) return
  loading.value = true
  loadError.value = false
  const p = reset ? 1 : pageNum.value + 1
  try {
    const res = await userItemApi.list({ keyword: active.value, pageNum: p, pageSize: PAGE_SIZE })
    const mapped = res.list.map(toGoodsItem)
    items.value = reset ? mapped : items.value.concat(mapped)
    pageNum.value = p
    total.value = res.total
    loaded.value = true
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/** 空词不进入结果态：留在本页，把焦点还回输入框 */
function submit(word?: string): void {
  const w = cut(word ?? kw.value)
  if (!w) {
    refocus()
    uni.showToast({ title: '输入关键词再搜', icon: 'none' })
    return
  }
  uni.hideKeyboard()
  kw.value = w
  active.value = w
  pushHistory(w)
  items.value = []
  total.value = 0
  loaded.value = false
  loadError.value = false
  pageNum.value = 0
  void fetchPage(true)
}

/** 清空输入 = 退回未搜索态；不保留一张空的「结果页」 */
function clearInput(): void {
  kw.value = ''
  active.value = ''
  items.value = []
  total.value = 0
  loaded.value = false
  loadError.value = false
  refocus()
}

/** `:focus` 已经是 true 时不会重新获取焦点，先置 false 再回 true */
function refocus(): void {
  focus.value = false
  void nextTick(() => {
    focus.value = true
  })
}

function cancel(): void {
  if (getCurrentPages().length > 1) {
    void uni.navigateBack()
    return
  }
  goTab('home')
}

function goHome(): void {
  goTab('home')
}

/** 热门分类：分类是 tab 页，reLaunch 过去并带上选中意图 */
function pickCat(c: { id: string; name: string }): void {
  pendingCategoryId.value = c.id
  goTab('category')
}

onLoad((query) => {
  loadHistory()
  void fetchCats()
  const q = query?.q ? cut(String(query.q)) : ''
  if (q) submit(q)
})

onPullDownRefresh(() => {
  if (!active.value) {
    uni.stopPullDownRefresh()
    return
  }
  void fetchPage(true).then(() => uni.stopPullDownRefresh())
})

onReachBottom(() => {
  if (!active.value) return
  void fetchPage(false)
})
</script>

<template>
  <view class="page">
    <!-- 搜索栏固定：结果很长时也要能改词，它是本页唯一的主控件 -->
    <view class="bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="bar__in">
        <view class="field">
          <CmIcon name="search" size="sm" tone="muted" />
          <input
            v-model="kw"
            class="field__in"
            :focus="focus"
            confirm-type="search"
            placeholder="搜教材、耳机、自行车…"
            placeholder-class="field__ph"
            @confirm="submit()"
            @blur="focus = false"
          />
          <view v-if="kw" class="field__x" @click="clearInput">
            <text>×</text>
          </view>
        </view>
        <text class="bar__cancel" @click="cancel">取消</text>
      </view>
    </view>
    <view class="ghost" :style="{ height: `calc(${statusBarHeight}px + 96rpx)` }" />

    <!-- ① 未搜索：历史 + 热门分类 -->
    <view v-if="!active" class="panel">
      <view v-if="history.length" class="blk cm-in">
        <view class="cm-sect">
          <view class="cm-sect__bar" />
          <text class="cm-sect__t">搜索历史</text>
          <text class="cm-sect__more" @click="clearHistory">清空</text>
        </view>
        <view class="words">
          <view v-for="w in history" :key="w" class="word" hover-class="word--on">
            <text class="word__t" @click="submit(w)">{{ w }}</text>
            <text class="word__x" @click.stop="removeWord(w)">×</text>
          </view>
        </view>
      </view>

      <view v-if="cats.length" class="blk cm-in" style="animation-delay: 90ms">
        <view class="cm-sect">
          <view class="cm-sect__bar" />
          <text class="cm-sect__t">热门分类</text>
          <text class="cm-sect__sub">不知道搜什么？按分类逛</text>
        </view>
        <view class="cats">
          <view v-for="c in cats" :key="c.id" class="cat" hover-class="cat--on" @click="pickCat(c)">
            <text class="cat__i">{{ c.name.slice(0, 1) }}</text>
            <text class="cat__t">{{ c.name }}</text>
          </view>
        </view>
      </view>

      <view v-if="!history.length && !cats.length" class="tip">
        <text class="tip__t">想看什么，直接搜标题或描述里的词</text>
      </view>
    </view>

    <!-- ② 结果态 -->
    <view v-else class="result">
      <view class="count">
        <text class="count__kw">「{{ active }}」</text>
        <text class="count__n">{{ loaded ? `找到 ${total} 件在售` : '正在搜索…' }}</text>
      </view>

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
        <text class="cm-state__t">搜索失败，下拉再试一次</text>
      </view>

      <view v-else-if="!items.length" class="cm-state">
        <CmIcon name="box" size="lg" tone="muted" />
        <text class="cm-state__t">没有「{{ active }}」相关的在售商品</text>
        <text class="cm-state__s">换个词试试，或者自己去发一件</text>
        <view class="btn" hover-class="cm-btn--on" @click="goHome">看看其他在售</view>
      </view>

      <view v-else class="falls">
        <view class="col">
          <GoodsCard v-for="it in leftColumn" :key="it.id" :item="it" />
        </view>
        <view class="col">
          <GoodsCard v-for="it in rightColumn" :key="it.id" :item="it" />
        </view>
      </view>

      <view v-if="items.length" class="loadmore">
        <view v-if="loading" class="loadmore__dots">
          <view class="loadmore__dot" />
          <view class="loadmore__dot" />
          <view class="loadmore__dot" />
        </view>
        <text v-else class="loadmore__t">{{ hasMore ? '上拉加载更多' : '没有更多了' }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding-bottom: calc(#{$cm-space-6} + env(safe-area-inset-bottom));
  background: $cm-color-bg;
  box-sizing: border-box;
}

/* ── 搜索栏 ─────────────────────────────────────────────── */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  z-index: 20;
  background: $cm-color-surface;
}

.bar__in {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  height: 96rpx;
  padding: 0 $cm-space-4;
  box-sizing: border-box;
}

.field {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  height: 72rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-pill;
  background: $cm-color-bg;
}

.field__in {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
}

.field__ph {
  color: $cm-color-text-3;
}

/* 清空钮：只在有词时出现，弱化到不与「取消」抢注意力 */
.field__x {
  flex: none;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  line-height: 1;
  color: #fff;
  background: $cm-color-gray-400;
}

.bar__cancel {
  flex: none;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

/* ── 未搜索态 ───────────────────────────────────────────── */
.panel {
  padding-bottom: $cm-space-5;
}

.blk {
  background: $cm-color-surface;
  margin: $cm-space-3 $cm-space-4 0;
  border-radius: $cm-radius-2xl;
  padding-bottom: $cm-space-3;
}

.words {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.word {
  display: flex;
  align-items: center;
  gap: 8rpx;
  max-width: 100%;
  height: 60rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-pill;
  background: $cm-color-bg;
}

.word--on {
  background: $cm-color-brand-bg;
}

.word__t {
  max-width: 380rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.word__x {
  font-size: 24rpx;
  line-height: 1;
  color: $cm-color-text-3;
}

.cats {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.cat {
  width: 48%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  height: 84rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-lg;
  background: $cm-color-bg;
  transition: transform 140ms ease-out;
}

.cat--on {
  transform: scale(0.96);
  background: $cm-color-brand-bg;
}

/* 首字块：字典里没有配色，用品牌浅底统一，不给分类编色 */
.cat__i {
  flex: none;
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  border-radius: $cm-radius-sm;
  font-size: 24rpx;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-brand-text;
  background: $cm-color-surface;
}

.cat__t {
  flex: 1;
  min-width: 0;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tip {
  padding: $cm-space-6 $cm-space-4 0;
  text-align: center;
}

.tip__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

/* ── 结果态 ─────────────────────────────────────────────── */
.count {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
  padding: $cm-space-3 $cm-space-4 0;
}

.count__kw {
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-1;
}

.count__n {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

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

.skel {
  height: 470rpx;
}

.btn {
  margin-top: 40rpx;
  padding: 0 48rpx;
  height: 76rpx;
  line-height: 76rpx;
  border-radius: $cm-radius-pill;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
}

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
</style>
