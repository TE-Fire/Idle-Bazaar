<script setup lang="ts">
/**
 * M6 我的（design/02 §六，P0）
 *
 * <p>五个入口：我发布的 / 我卖出的 / 我买到的 / 收藏 / 学号认证。
 * 骨架期都是占位——它们各自依赖订单与用户接口（均未实现），
 * 与其造一屏假数据，不如明确标出「待建」，避免误以为功能已完成。
 */

interface Entry {
  key: string
  label: string
  hint: string
}

const user = { nick: '我（未登录）', verified: false }

const entries: Entry[] = [
  { key: 'published', label: '我发布的', hint: '草稿 / 在售 / 审核中' },
  { key: 'sold', label: '我卖出的', hint: '已完成交易' },
  { key: 'bought', label: '我买到的', hint: '待面交 / 已完成' },
  { key: 'fav', label: '我的收藏', hint: '降价提醒' },
  { key: 'verify', label: '学号认证', hint: user.verified ? '已认证' : '未认证，认证后可提升信任' }
]

function open(e: Entry): void {
  uni.showToast({ title: `「${e.label}」待建`, icon: 'none' })
}
</script>

<template>
  <view class="page">
    <view class="head">
      <view class="head__avatar"><text class="head__t">我</text></view>
      <view class="head__meta">
        <text class="head__nick">{{ user.nick }}</text>
        <text class="head__sub">
          {{ user.verified ? '学号已认证' : '未认证' }}
        </text>
      </view>
    </view>

    <view class="list">
      <view v-for="e in entries" :key="e.key" class="entry" hover-class="entry--active" @click="open(e)">
        <text class="entry__label">{{ e.label }}</text>
        <text class="entry__hint">{{ e.hint }}</text>
        <text class="entry__arrow">›</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  background: $cm-color-bg;
  min-height: 100vh;
}

.head {
  display: flex;
  align-items: center;
  gap: $cm-space-4;
  padding: $cm-space-6 $cm-space-4;
  background: $cm-color-surface;
}

.head__avatar {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $cm-color-brand-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.head__t {
  font-size: 44rpx;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.head__meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.head__nick {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-text-1;
}

.head__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.list {
  margin-top: $cm-space-3;
  background: $cm-color-surface;
}

.entry {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  height: 112rpx;
  padding: 0 $cm-space-4;
  border-bottom: 1rpx solid $cm-color-border;
  transition: transform 120ms ease-out;
}

.entry--active {
  transform: scale(0.99);
}

.entry__label {
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.entry__hint {
  margin-left: auto;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.entry__arrow {
  font-size: $cm-font-size-md;
  color: $cm-color-text-3;
}
</style>
