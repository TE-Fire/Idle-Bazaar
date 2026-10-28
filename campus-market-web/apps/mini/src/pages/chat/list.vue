<script setup lang="ts">
/**
 * M5 消息 · 会话列表（business/02 M-B4，2026-10-22 同步留言口径）
 *
 * <p>真实会话来自 /api/chat/conversations：按「商品 + 对方」一行，买家与卖家共用。
 * 无实时推送——进详情轮询拉取；未读红点来自服务端统计，进会话即清零。
 * 系统通知与交易通知合并为后续站内通知能力（已由 /api/notice 落地，入口在「我的」）。
 *
 * <p>2026-10-28 视觉改版：由「白底 + 分隔线」的列表改成灰底白卡；头像用昵称哈希
 * 取渐变底色（同一人每次颜色一致）；未读用胶囊角标压在头像右上角，比裸红字更醒目。
 */

import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { formatRelativeTime } from '@campus/shared'
import { userChatApi, type ChatConversationVO } from '@/api/user'
import CmIcon from '@/components/CmIcon.vue'
import TabBar from '@/components/TabBar.vue'

const conversations = ref<ChatConversationVO[]>([])
const loading = ref(true)

const unreadTotal = computed(() => conversations.value.reduce((s, c) => s + (c.unread ?? 0), 0))

async function load(): Promise<void> {
  try {
    conversations.value = await userChatApi.conversations()
  } catch {
    // request 层已提示
  } finally {
    loading.value = false
  }
}

/** 头像底色：昵称哈希取模，保证同一个人在不同会话里颜色稳定 */
const AVATARS = 6

function avClass(nick: string | undefined): string {
  const s = nick || '对方'
  let h = 0
  for (let i = 0; i < s.length; i += 1) h = (h * 31 + s.charCodeAt(i)) % 997
  return `av--${h % AVATARS}`
}

function timeOf(at: string | undefined): string {
  if (!at) return ''
  try {
    return formatRelativeTime(at)
  } catch {
    return ''
  }
}

/**
 * 打开会话：把列表项手里的商品摘要一并带上，详情页顶部商品卡片直接用，
 * 不必再拉一次商品详情（卡片数据两个入口都从跳转参数拿，见 detail.vue onLoad）。
 */
function open(c: ChatConversationVO): void {
  const q = [
    `itemId=${c.itemId}`,
    `peerId=${c.peerUserId}`,
    `nick=${encodeURIComponent(c.peerNick ?? '')}`,
    `itemTitle=${encodeURIComponent(c.itemTitle ?? '')}`,
    `itemImage=${c.itemMainImage ? encodeURIComponent(c.itemMainImage) : ''}`,
    `itemPrice=${c.price ?? ''}`,
    `itemStatus=${c.status ?? ''}`
  ].join('&')
  void uni.navigateTo({ url: `/pages/chat/detail?${q}` })
}

// 列表页 onShow 刷新：从详情返回后未读数要是新的
onShow(() => {
  void load()
})

onPullDownRefresh(() => {
  void load().then(() => uni.stopPullDownRefresh())
})
</script>

<template>
  <view class="cm-page cm-page--tab">
    <!-- 头部：标题 + 未读汇总，给「全是会话列表」加一个明确的层级起点 -->
    <view class="head">
      <view class="head__main">
        <text class="head__t">消息</text>
        <text class="head__s">当面聊清楚，交易更放心</text>
      </view>
      <view v-if="unreadTotal" class="head__badge">
        <text>{{ unreadTotal > 99 ? '99+' : unreadTotal }} 条未读</text>
      </view>
    </view>

    <view v-if="loading" class="list">
      <view v-for="n in 4" :key="n" class="cm-skel chat-skel" />
    </view>

    <view v-else-if="!conversations.length" class="cm-state">
      <CmIcon name="chat" size="lg" tone="muted" />
      <text class="cm-state__t">还没有会话</text>
      <text class="cm-state__s">去商品详情页点「聊一聊」开始询价</text>
    </view>

    <view v-else class="list">
      <view
        v-for="(c, i) in conversations"
        :key="`${c.itemId}-${c.peerUserId}`"
        class="chat cm-card cm-in"
        :style="{ animationDelay: Math.min(i, 8) * 40 + 'ms' }"
        hover-class="chat--press"
        @click="open(c)"
      >
        <view class="av" :class="avClass(c.peerNick)">
          <text class="av__t">{{ (c.peerNick || '方')[0] }}</text>
          <text v-if="c.unread" class="av__badge">{{ c.unread > 99 ? '99+' : c.unread }}</text>
        </view>

        <view class="chat__body">
          <view class="chat__top">
            <text class="chat__nick cm-ell1">{{ c.peerNick || '对方' }}</text>
            <text class="chat__time">{{ timeOf(c.lastAt) }}</text>
          </view>
          <text class="chat__last cm-ell1" :class="{ 'chat__last--unread': c.unread }">
            {{ c.lastContent || '（暂无消息）' }}
          </text>
          <view class="chat__item">
            <CmIcon name="box" size="sm" tone="muted" />
            <text class="chat__item-t cm-ell1">{{ c.itemTitle || '商品' }}</text>
          </view>
        </view>
      </view>
    </view>

    <TabBar active="chat" />
  </view>
</template>

<style scoped lang="scss">
/* ── 头部 ───────────────────────────────────────────────── */
.head {
  display: flex;
  align-items: flex-end;
  gap: $cm-space-3;
  padding: $cm-space-5 $cm-space-4 $cm-space-3;
}

.head__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.head__t {
  font-size: $cm-font-size-2xl;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
  letter-spacing: 0.6rpx;
}

.head__s {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.head__badge {
  flex: none;
  height: 52rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  font-size: $cm-font-size-xs;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, #FF8A5B, $cm-color-brand-2);
  box-shadow: 0 6rpx 14rpx rgba(255, 77, 109, 0.28);
}

/* ── 会话卡 ─────────────────────────────────────────────── */
.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
  padding: $cm-space-1 $cm-space-4 $cm-space-4;
}

.chat {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-3;
  transition: transform 140ms ease-out;
}

.chat--press {
  transform: scale(0.98);
}

/* 头像：昵称哈希取色，6 套渐变覆盖常见会话量 */
.av {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.av--0 {
  background-image: linear-gradient(135deg, #FFB673, $cm-color-brand);
}

.av--1 {
  background-image: linear-gradient(135deg, #A98BFF, $cm-color-accent-violet);
}

.av--2 {
  background-image: linear-gradient(135deg, #4FD6A3, $cm-color-accent-teal);
}

.av--3 {
  background-image: linear-gradient(135deg, #6FB2FF, $cm-color-blue-500);
}

.av--4 {
  background-image: linear-gradient(135deg, #FFA6BE, $cm-color-brand-2);
}

.av--5 {
  background-image: linear-gradient(135deg, #FFB067, $cm-color-orange-500);
}

.av__t {
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
}

.av__badge {
  position: absolute;
  top: -4rpx;
  right: -8rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: $cm-radius-pill;
  border: 3rpx solid $cm-color-surface;
  background-image: linear-gradient(135deg, #FF6B6B, $cm-color-brand-2);
  color: #fff;
  font-size: 20rpx;
  line-height: 32rpx;
  text-align: center;
}

.chat__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.chat__top {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
}

.chat__nick {
  flex: 1;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-1;
}

.chat__time {
  flex: none;
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.chat__last {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

/* 有未读时预览文字加深，配合角标形成「这条没看」的强提示 */
.chat__last--unread {
  color: $cm-color-text-1;
  font-weight: $cm-font-weight-medium;
}

.chat__item {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 2rpx;
}

.chat__item-t {
  flex: 1;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.chat-skel {
  height: 150rpx;
}
</style>
