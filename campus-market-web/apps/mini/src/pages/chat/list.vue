<script setup lang="ts">
/**
 * M5 消息 · 会话列表（design/02 §六，P0）
 *
 * <p>骨架期只铺列表壳子：会话数据依赖 WebSocket（tech/02 §6.6：原生 WS + Sa-Token 握手），
 * 后端未实现，所以这里是静态占位。消息三分区（PRIVATE / SYSTEM / TRADE）由
 * 前端按 `zone` 字段分流渲染——接口就位后按 zone 分组，而不是按时间混排。
 */

interface Session {
  id: string
  nick: string
  last: string
  time: string
  unread: number
  zone: 'PRIVATE' | 'SYSTEM' | 'TRADE'
}

const sessions: Session[] = [
  { id: '1', nick: '小林同学', last: '这本书还在的，图书馆东门见？', time: '刚刚', unread: 2, zone: 'PRIVATE' },
  { id: '2', nick: '系统通知', last: '你发布的「iPad 9 64G」已通过审核', time: '2 小时前', unread: 0, zone: 'SYSTEM' },
  { id: '3', nick: '橘子汽水', last: '订单已确认，明天下午面交', time: '昨天', unread: 0, zone: 'TRADE' }
]

const ZONE_LABEL: Record<Session['zone'], string> = {
  PRIVATE: '私信',
  SYSTEM: '系统',
  TRADE: '交易'
}

function open(s: Session): void {
  uni.showToast({ title: `聊天页待建（${ZONE_LABEL[s.zone]}）`, icon: 'none' })
}
</script>

<template>
  <view class="page">
    <view v-for="s in sessions" :key="s.id" class="item" hover-class="item--active" @click="open(s)">
      <view class="avatar"><text class="avatar__t">{{ s.nick[0] }}</text></view>
      <view class="mid">
        <view class="mid__top">
          <text class="nick">{{ s.nick }}</text>
          <text class="time">{{ s.time }}</text>
        </view>
        <text class="last">{{ s.last }}</text>
      </view>
      <view v-if="s.unread" class="dot">{{ s.unread }}</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  background: $cm-color-surface;
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
  align-items: baseline;
  justify-content: space-between;
}

.nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
}

.time {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.last {
  display: block;
  margin-top: 4rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dot {
  min-width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  font-size: $cm-font-size-xs;
  color: #fff;
  background: $cm-color-err;
  border-radius: 18rpx;
  padding: 0 8rpx;
}
</style>
