<script setup lang="ts">
/**
 * 系统通知（notes/22 批次 3）：平台单向广播，只有「看」这一种动作。
 *
 * <p>与私信分开：私信按会话聊，通知按事件流读，混在一起两边未读数都算不清。
 * 进页面先拉列表、拉到后再清未读——反过来的话请求失败等于未读被无声清空。
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

const finished = computed(() => list.value.length >= total.value)

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
      if (reset && list.value.some((n) => n.readFlag === 0)) {
        void noticeApi.readAll().then(() => {
          list.value = list.value.map((n) => ({ ...n, readFlag: 1 }))
        })
      }
    })
    .catch(() => undefined)
    .finally(() => {
      loading.value = false
    })
}

onLoad(() => load(true))

onReachBottom(() => {
  if (finished.value || loading.value) return
  pageNum.value += 1
  load()
})
</script>

<template>
  <view class="page">
    <view v-if="!list.length && loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="!list.length" class="state"><text class="state__t">还没有通知</text></view>

    <view v-else class="list">
      <view v-for="n in list" :key="n.id" class="row">
        <view class="row__dot" :class="{ 'row__dot--on': n.readFlag === 0 }" />
        <view class="row__body">
          <view class="row__tag" :class="{ 'row__tag--i': n.type === 5 }">
            <text>{{ n.type === 5 ? '互动' : '系统' }}</text>
          </view>
          <text class="row__text">{{ n.content }}</text>
          <text class="row__time">{{ formatRelativeTime(n.createTime) }}</text>
        </view>
      </view>
    </view>

    <view v-if="list.length" class="more">
      <text class="more__t">{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-3 $cm-space-4 calc($cm-space-6 + env(safe-area-inset-bottom));
  background: $cm-color-bg;
}

.state {
  padding: $cm-space-8 0;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.row {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.row__dot {
  flex: none;
  width: 12rpx;
  height: 12rpx;
  margin-top: 14rpx;
  border-radius: 50%;
  background: transparent;
}

.row__dot--on {
  background: $cm-color-err;
}

.row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

/* 类型标：互动用品牌色，系统处置用中性灰——处置结果带压力，不该和点赞同色 */
.row__tag {
  display: inline-flex;
  align-items: center;
  height: 32rpx;
  padding: 0 10rpx;
  margin-bottom: 8rpx;
  font-size: 20rpx;
  color: $cm-color-text-3;
  background: $cm-color-bg;
  border-radius: 6rpx;
}

.row__tag--i {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
}

.row__text {
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-1;
  word-break: break-all;
}

.row__time {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.more {
  padding: $cm-space-4 0;
  text-align: center;
}

.more__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
