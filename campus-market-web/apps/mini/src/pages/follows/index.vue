<script setup lang="ts">
/**
 * 关注 / 粉丝列表（notes/22 S2）
 *
 * <p>「我的」页进来默认看关注；`?tab=fans` 直接落到粉丝。`userId` 留着看他人（本期没有个人主页，
 * 所以没有主动入口，参数先接上，主页落地即用）。
 *
 * <p>分页是触底追加：`finished` 由「已加载条数 >= total」判定，不靠「本页不满」——
 * 最后一页刚好满 20 条时后者会多打一次空请求。
 *
 * <p>关注 / 取关幂等，成功后本地翻转该行；关注列表里取关直接把行移除（它已不属于这个列表）。
 */

import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { userSocialApi, type FollowListType, type FollowUserVO } from '@/api/social'
import { getSession } from '@/utils/session'

const PAGE = 20

const tab = ref<FollowListType>('following')
const targetId = ref('')
const myId = ref('')

const list = ref<FollowUserVO[]>([])
const total = ref(0)
const followingCount = ref(0)
const fansCount = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

async function fetchPage(reset: boolean): Promise<void> {
  if (loading.value) return
  loading.value = true
  try {
    const res = await userSocialApi.list(tab.value, pageNum.value, PAGE, targetId.value || undefined)
    const rows = res.list ?? []
    list.value = reset ? rows : [...list.value, ...rows]
    // 计数是 long 转的字符串
    total.value = Number(res.total)
    followingCount.value = Number(res.followingCount)
    fansCount.value = Number(res.fansCount)
    finished.value = list.value.length >= total.value
  } catch {
    // request 层已 toast；未登录会 reLaunch 到登录页
    finished.value = true
  } finally {
    loading.value = false
  }
}

function reload(): void {
  pageNum.value = 1
  finished.value = false
  void fetchPage(true)
}

function switchTab(next: FollowListType): void {
  if (tab.value === next) return
  tab.value = next
  list.value = []
  reload()
}

async function toggle(u: FollowUserVO): Promise<void> {
  const wasFollowing = !!u.following
  try {
    if (wasFollowing) {
      await userSocialApi.unfollow(u.userId)
    } else {
      await userSocialApi.follow(u.userId)
    }
    u.following = !wasFollowing
    if (tab.value === 'following' && wasFollowing) {
      list.value = list.value.filter((x) => x.userId !== u.userId)
      total.value = Math.max(total.value - 1, 0)
    }
    // 只有看自己的页才动「关注」计数
    if (!targetId.value || targetId.value === myId.value) {
      followingCount.value = Math.max(followingCount.value + (wasFollowing ? -1 : 1), 0)
    }
    uni.showToast({ title: wasFollowing ? '已取关' : '已关注', icon: 'none', duration: 1200 })
  } catch {
    // 失败保持原态：下次进页面回到服务端真值
  }
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

function metaText(u: FollowUserVO): string {
  const parts: string[] = []
  if (u.creditScore != null) parts.push(`信用 ${u.creditScore}`)
  if (u.followTime) {
    parts.push(`${u.followTime.replace('T', ' ').slice(0, 16)} ${tab.value === 'fans' ? '关注了你' : '关注'}`)
  }
  return parts.join(' · ')
}

onLoad((query) => {
  const q = query as { tab?: string; userId?: string }
  if (q.tab === 'fans') tab.value = 'fans'
  if (q.userId) targetId.value = q.userId
  myId.value = getSession().userId
})

/** onShow 刷新：从登录页回来、或关注别人后返回，都要看到新状态 */
onShow(() => {
  if (!getSession().tokenValue) {
    void uni.navigateTo({ url: '/pages/login/index?redirect=follows' })
    return
  }
  myId.value = getSession().userId
  reload()
})

onReachBottom(() => {
  if (finished.value || loading.value) return
  pageNum.value += 1
  void fetchPage(false)
})

onPullDownRefresh(() => {
  reload()
  uni.stopPullDownRefresh()
})
</script>

<template>
  <view class="page">
    <view class="counts">
      <view class="counts__item" :class="{ 'counts__item--on': tab === 'following' }" @click="switchTab('following')">
        <text class="counts__num">{{ followingCount }}</text>
        <text class="counts__label">关注</text>
      </view>
      <view class="counts__item" :class="{ 'counts__item--on': tab === 'fans' }" @click="switchTab('fans')">
        <text class="counts__num">{{ fansCount }}</text>
        <text class="counts__label">粉丝</text>
      </view>
    </view>

    <view v-if="!list.length && loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="!list.length" class="state">
      <text class="state__t">{{ tab === 'following' ? '还没有关注任何人' : '还没有粉丝' }}</text>
    </view>

    <view v-else class="list">
      <view v-for="u in list" :key="u.userId" class="row">
        <image v-if="u.avatarUrl" class="row__avatar" :src="u.avatarUrl" mode="aspectFill" />
        <view v-else class="row__avatar row__avatar--t"><text class="row__t">{{ initial(u.nickname) }}</text></view>

        <view class="row__bd">
          <view class="row__top">
            <text class="row__nick">{{ u.nickname || '校园用户' }}</text>
            <text v-if="u.authStatus === 2" class="row__badge">已认证</text>
          </view>
          <text class="row__meta">{{ metaText(u) }}</text>
        </view>

        <view
          v-if="u.userId !== myId"
          class="row__btn"
          :class="{ 'row__btn--on': u.following }"
          hover-class="row__btn--active"
          @click.stop="toggle(u)"
        >
          {{ u.following ? '已关注' : tab === 'fans' ? '回关' : '关注' }}
        </view>
      </view>

      <view v-if="finished" class="end"><text class="end__t">没有更多了</text></view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: $cm-color-bg;
}

/* 两个大数字并排：先看到「多少人」，再决定翻列表 */
.counts {
  display: flex;
  gap: $cm-space-8;
  padding: $cm-space-5;
  background: $cm-color-surface;
  margin-bottom: $cm-space-3;
}

.counts__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
  padding-bottom: 6rpx;
  border-bottom: 4rpx solid transparent;
}

.counts__item--on {
  border-bottom-color: $cm-color-brand;
}

.counts__num {
  font-size: 40rpx;
  font-weight: 700;
  color: $cm-color-text-1;
}

.counts__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.list {
  padding: 0 $cm-space-3 $cm-space-6;
}

.row {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-3;
  margin-bottom: $cm-space-2;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.row__avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  flex: none;
  background: $cm-color-brand-bg;
}

.row__avatar--t {
  display: flex;
  align-items: center;
  justify-content: center;
}

.row__t {
  font-size: 36rpx;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.row__bd {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.row__top {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.row__nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__badge {
  flex: none;
  padding: 2rpx 8rpx;
  font-size: 20rpx;
  color: $cm-color-ok;
  background: #f0faf4;
  border-radius: 6rpx;
}

.row__meta {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* 未关注 = 黄底黑字；已关注降级成白底描边 */
.row__btn {
  flex: none;
  min-width: 132rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: 28rpx;
}

.row__btn--on {
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
}

.row__btn--active {
  opacity: 0.8;
}

.state {
  padding: 160rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.end {
  padding: $cm-space-4;
  text-align: center;
}

.end__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
