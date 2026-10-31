<script setup lang="ts">
/**
 * 系统通知（notes/22 批次 3、design/02 §5.8）：平台写给个人的信。
 *
 * <p>两层结构——列表是信封、点开是信笺（底部抽屉）。列表只给两行摘要，
 * 正文上限 2000 字，全摊开会把十条通知变成两千行。
 *
 * <p>已读不自动发生：原「进页面即全部已读」的口径作废。点开某封标该封，
 * 未读 > 0 时头部才给「全部已读」；未读归零（或本来没有通知）就不该再有红点。
 */

import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { formatRelativeTime } from '@campus/shared'
import { noticeApi, type NoticeVO } from '@/api/social'
import { getSession } from '@/utils/session'

const PAGE_SIZE = 20

const list = ref<NoticeVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

/** 服务端未读总数：列表是分页的，本地数未读会随翻页漂移 */
const unreadCount = ref(0)

/** 当前展开的那封；null = 抽屉关着 */
const current = ref<NoticeVO | null>(null)

const finished = computed(() => list.value.length >= total.value)

/** 称谓：昵称过长会把这行挤成两行，信里不需要全名 */
const salute = computed(() => (getSession().nickname || '').trim().slice(0, 10) || '同学')

function load(reset = false): void {
  if (loading.value) return
  if (!getSession().tokenValue) {
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  loading.value = true
  if (reset) pageNum.value = 1
  noticeApi
    .list(pageNum.value, PAGE_SIZE)
    .then((p) => {
      list.value = reset ? p.list : [...list.value, ...p.list]
      total.value = Number(p.total)
    })
    .catch(() => undefined)
    .finally(() => {
      loading.value = false
    })
}

function loadUnread(): void {
  noticeApi
    .unread()
    .then((n) => {
      unreadCount.value = Number(n) || 0
    })
    .catch(() => undefined)
}

onLoad(() => {
  load(true)
  loadUnread()
})

onReachBottom(() => {
  if (finished.value || loading.value) return
  pageNum.value += 1
  load()
})

/** 点开一封：本地先置已读，再报服务端——不必等往返，红点当场落下 */
function openLetter(n: NoticeVO): void {
  current.value = n
  if (n.readFlag !== 0) return
  n.readFlag = 1
  if (unreadCount.value > 0) unreadCount.value -= 1
  noticeApi.readOne(n.id).catch(() => undefined)
}

function closeLetter(): void {
  current.value = null
}

function readAll(): void {
  noticeApi
    .readAll()
    .then(() => {
      list.value = list.value.map((n) => ({ ...n, readFlag: 1 }))
      unreadCount.value = 0
      uni.showToast({ title: '已全部标为已读', icon: 'none', duration: 1500 })
    })
    .catch(() => undefined)
}

const TYPE_TEXT: Record<number, string> = { 4: '系统', 5: '互动', 6: '公告' }

function typeText(type: number): string {
  return TYPE_TEXT[type] ?? '系统'
}

const pad = (n: number): string => String(n).padStart(2, '0')

function letterDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
}

function letterClock(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${letterDate(iso)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <view class="page">
    <view class="bar">
      <text class="bar__t">{{ unreadCount > 0 ? `${unreadCount} 条未读` : '全部已读' }}</text>
      <view v-if="unreadCount > 0" class="bar__btn" hover-class="bar__btn--on" @click="readAll">
        <text>全部已读</text>
      </view>
    </view>

    <view v-if="!list.length && loading" class="state"><text class="state__t">加载中…</text></view>

    <view v-else-if="!list.length" class="state">
      <view class="state__env" />
      <text class="state__h">信箱是空的</text>
      <text class="state__t">平台有新的处置结果或公告时，会送到这里</text>
    </view>

    <view v-else class="list">
      <view
        v-for="n in list"
        :key="n.id"
        class="env"
        :class="['env--t' + n.type, { 'env--new': n.readFlag === 0 }]"
        hover-class="env--on"
        @click="openLetter(n)"
      >
        <view class="env__body">
          <view class="env__top">
            <text class="env__tag">{{ typeText(n.type) }}</text>
            <text class="env__time">{{ formatRelativeTime(n.createTime) }}</text>
            <view v-if="n.readFlag === 0" class="env__dot" />
          </view>
          <text v-if="n.title" class="env__title">{{ n.title }}</text>
          <text class="env__text">{{ n.content }}</text>
        </view>
      </view>
    </view>

    <view v-if="list.length" class="more">
      <text class="more__t">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</text>
    </view>

    <!-- 信笺：底部抽屉，点遮罩关闭 -->
    <view v-if="current" class="mask" @click="closeLetter">
      <view class="sheet" @click.stop>
        <view class="letter">
          <text class="letter__head">校 园 二 手 · 系 统 通 知</text>

          <view class="letter__meta">
            <text>{{ typeText(current.type) }}</text>
            <text>{{ letterClock(current.createTime) }}</text>
          </view>

          <text v-if="current.title" class="letter__title">{{ current.title }}</text>

          <text class="letter__salut">亲爱的 {{ salute }}：</text>

          <text class="letter__body">{{ current.content }}</text>

          <view class="letter__foot">
            <view class="letter__seal">
              <text class="letter__seal-t">校园</text>
              <text class="letter__seal-t">二手</text>
            </view>
            <view class="letter__sign">
              <text class="letter__line">校园二手 · 平台运营</text>
              <text class="letter__line">{{ letterDate(current.createTime) }}</text>
            </view>
          </view>
        </view>

        <view class="sheet__close" hover-class="sheet__close--on" @click="closeLetter">
          <text>收起</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-3 $cm-space-4 calc($cm-space-6 + env(safe-area-inset-bottom));
  background: $cm-color-bg;
}

.bar {
  display: flex;
  align-items: center;
  padding: 0 4rpx 16rpx;
}

.bar__t {
  flex: 1;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* 「全部已读」是次要动作：细描边胶囊，不抢主色 */
.bar__btn {
  height: 52rpx;
  padding: 0 22rpx;
  border: 1rpx solid $cm-color-border-strong;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  background: $cm-color-surface;
}

.bar__btn--on {
  background: $cm-color-gray-100;
}

.state {
  padding: $cm-space-8 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

/* 空状态的信封：线描，与列表的实心色块区分开 */
.state__env {
  width: 96rpx;
  height: 68rpx;
  border: 2rpx solid $cm-color-border-strong;
  border-radius: 8rpx;
  margin-bottom: 8rpx;
}

.state__h {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.state__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  padding: 0 40rpx;
  text-align: center;
}

.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

/* ── 信封卡片 ───────────────────────────────────────────── */

.env {
  position: relative;
  padding: $cm-space-4 $cm-space-4 $cm-space-4 96rpx;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
}

.env--on {
  background: $cm-color-gray-100;
}

/* 左侧色块＝信封封舌侧边，靠类型类名分色 */
.env::before {
  content: '';
  position: absolute;
  left: 32rpx;
  top: $cm-space-4;
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  background: $cm-color-border-strong;
  box-shadow: none;
}

.env--t6::before {
  background: $cm-color-brand;
  box-shadow: none;
}

.env--t5::before {
  background: $cm-color-brand-bg;
  box-shadow: inset 0 0 0 1rpx $cm-color-brand-dark;
}

.env__body {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.env__top {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.env__tag {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.env__time {
  margin-left: auto;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* 未读点只有未读才有，已读不留空心占位 */
.env__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $cm-color-err;
}

.env__title {
  font-size: $cm-font-size-base;
  font-weight: 600;
  line-height: 1.45;
  color: $cm-color-text-1;
  word-break: break-all;
}

/* 摘要两行：列表只负责"这是哪封信" */
.env__text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-2;
  word-break: break-all;
}

.env--new .env__text,
.env--new .env__title {
  color: $cm-color-text-1;
}

.env--new .env__tag {
  color: $cm-color-text-2;
}

.more {
  padding: $cm-space-4 0;
  text-align: center;
}

.more__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 信笺抽屉 ───────────────────────────────────────────── */

.mask {
  position: fixed;
  inset: 0;
  z-index: 900;
  background: rgba(20, 20, 30, 0.5);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  max-height: 82vh;
  padding: $cm-space-4 $cm-space-4 calc($cm-space-4 + env(safe-area-inset-bottom));
  background: $cm-color-bg-warm;
  border-radius: $cm-radius-3xl $cm-radius-3xl 0 0;
  display: flex;
  flex-direction: column;
  animation: sheet-in 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes sheet-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

/* 信纸自身可滚：正文 2000 字时抽屉不能把内容顶出屏幕 */
.letter {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 32rpx 28rpx 40rpx;
  background: $cm-color-bg-warm;
  border-radius: $cm-radius-xl;
}

.letter__head {
  display: block;
  padding-bottom: 20rpx;
  margin-bottom: 24rpx;
  border-bottom: 1rpx solid $cm-color-border;
  text-align: center;
  font-size: $cm-font-size-xs;
  letter-spacing: 6rpx;
  color: $cm-color-text-2;
}

.letter__meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 24rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.letter__title {
  display: block;
  margin-bottom: 16rpx;
  font-size: $cm-font-size-md;
  font-weight: 600;
  line-height: 1.45;
  color: $cm-color-text-1;
  word-break: break-all;
}

.letter__salut {
  display: block;
  margin-bottom: 12rpx;
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
}

/* 纸面阅读比正文松：行高 1.85，首行缩进两字，保留原有换行 */
.letter__body {
  display: block;
  font-size: 30rpx;
  line-height: 1.85;
  color: $cm-color-text-1;
  white-space: pre-wrap;
  text-indent: 2em;
  word-break: break-all;
}

/* 落款与印章同处右下：印章在名字左侧，像真实信件的签章关系 */
.letter__foot {
  margin-top: 40rpx;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: 20rpx;
}

.letter__sign {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6rpx;
}

.letter__line {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

/* 盖章：品牌橙描边双圈 */
.letter__seal {
  flex: none;
  width: 96rpx;
  height: 96rpx;
  border: 2rpx solid $cm-color-brand-dark;
  border-radius: 50%;
  box-shadow: inset 0 0 0 4rpx $cm-color-bg-warm, inset 0 0 0 6rpx $cm-color-brand-dark;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  opacity: 0.85;
  transform: rotate(-8deg);
}

.letter__seal-t {
  font-size: 22rpx;
  line-height: 1.2;
  color: $cm-color-brand-dark;
}

.sheet__close {
  margin-top: $cm-space-3;
  height: 84rpx;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $cm-color-surface;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.sheet__close--on {
  background: $cm-color-gray-100;
}
</style>
