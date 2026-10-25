<script setup lang="ts">
/**
 * M5 消息 · 会话列表（business/02 M-B4，2026-10-22 同步留言口径）
 *
 * <p>真实会话来自 /api/chat/conversations：按「商品 + 对方」一行，买家与卖家共用。
 * 无实时推送——进详情轮询拉取；未读红点来自服务端统计，进会话即清零。
 * 系统通知与交易通知合并为后续站内通知能力，本期不做（见 M-B10）。
 */

import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { userChatApi, type ChatConversationVO } from '@/api/user'

const conversations = ref<ChatConversationVO[]>([])
const loading = ref(true)

async function load(): Promise<void> {
  try {
    conversations.value = await userChatApi.conversations()
  } catch {
    // request 层已提示
  } finally {
    loading.value = false
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
  <view class="page">
    <view v-if="loading" class="tip">加载中…</view>
    <view v-else-if="!conversations.length" class="tip">
      还没有会话。去商品详情页点「聊一聊」开始询价。
    </view>
    <view
      v-for="c in conversations"
      :key="`${c.itemId}-${c.peerUserId}`"
      class="item"
      hover-class="item--active"
      @click="open(c)"
    >
      <view class="avatar"><text class="avatar__t">{{ (c.peerNick || '方')[0] }}</text></view>
      <view class="mid">
        <view class="mid__top">
          <text class="nick">{{ c.peerNick || '对方' }}</text>
          <text v-if="c.unread" class="dot">{{ c.unread }}</text>
        </view>
        <text class="last">{{ c.lastContent || '（暂无消息）' }}</text>
        <text class="item-of">问：{{ c.itemTitle || '商品' }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  background: $cm-color-surface;
  min-height: 100vh;
}

.tip {
  padding: $cm-space-8 $cm-space-4;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  line-height: 1.6;
}

.item {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-4;
  border-bottom: 1rpx solid $cm-color-border;
  transition: transform 120ms ease-out;
}

.item--active {
  transform: scale(0.99);
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: $cm-color-bg;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.avatar__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-text-2;
}

.mid {
  flex: 1;
  min-width: 0;
}

.mid__top {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
}

.dot {
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: #fff;
  background: $cm-color-err;
  border-radius: 16rpx;
  padding: 0 8rpx;
}

.last {
  display: block;
  margin-top: 4rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-of {
  display: block;
  margin-top: 2rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
