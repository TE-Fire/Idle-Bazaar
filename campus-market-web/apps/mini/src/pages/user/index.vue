<script setup lang="ts">
/**
 * 个人主页（notes/22 S2 的宿主页面）
 *
 * <p>资料头 + 关注按钮 + TA 的在售商品（双列瀑布流，复用首页的 GoodsCard）。
 * 动态 Tab（S4）未实现，先不做占位——小程序屏窄，空 tab 只会添乱，S4 落地时一起加。
 *
 * <p>触底加载与首页同口径：`finished` 由「已加载条数 >= total」判定。
 * 关注按钮乐观更新，失败不动界面，下次进页回到服务端真值。
 */

import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import GoodsCard from '@/components/GoodsCard.vue'
import { userItemApi, type ApiItemVO } from '@/api/user'
import { userSocialApi as socialApi, type UserProfileVO } from '@/api/social'
import { toGoodsItem } from '@/utils/item-map'
import type { GoodsItem } from '@campus/shared'
import { getSession } from '@/utils/session'

const PAGE = 10

const userId = ref('')
const myId = ref('')

const profile = ref<UserProfileVO | null>(null)
const goods = ref<GoodsItem[]>([])
const pageNum = ref(1)
const finished = ref(false)
const loading = ref(false)
const acting = ref(false)

const isSelf = computed(() => !!profile.value?.self)

const leftColumn = computed(() => goods.value.filter((_, i) => i % 2 === 0))
const rightColumn = computed(() => goods.value.filter((_, i) => i % 2 === 1))

async function loadProfile(): Promise<void> {
  try {
    profile.value = await socialApi.profile(userId.value)
  } catch {
    profile.value = null
  }
}

async function fetchPage(reset: boolean): Promise<void> {
  if (loading.value) return
  if (!reset && finished.value) return
  loading.value = true
  const p = reset ? 1 : pageNum.value + 1
  try {
    const res = await userItemApi.list({
      sellerId: userId.value,
      pageNum: p,
      pageSize: PAGE
    })
    const rows: ApiItemVO[] = res.list ?? []
    goods.value = reset ? rows.map(toGoodsItem) : [...goods.value, ...rows.map(toGoodsItem)]
    pageNum.value = p
    finished.value = goods.value.length >= Number(res.total)
  } catch {
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

async function toggleFollow(): Promise<void> {
  const p = profile.value
  if (!p || acting.value) return
  acting.value = true
  const wasFollowing = p.following
  try {
    if (wasFollowing) {
      await socialApi.unfollow(p.userId)
    } else {
      await socialApi.follow(p.userId)
    }
    p.following = !wasFollowing
    p.fansCount = String(Math.max(Number(p.fansCount) + (wasFollowing ? -1 : 1), 0))
    uni.showToast({ title: wasFollowing ? '已取关' : '已关注', icon: 'none', duration: 1200 })
  } catch {
    // 失败保持原态
  } finally {
    acting.value = false
  }
}

function goFollows(tab: 'following' | 'fans'): void {
  void uni.navigateTo({ url: `/pages/follows/index?tab=${tab === 'fans' ? 'fans' : 'following'}&userId=${userId.value}` })
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

onLoad((query) => {
  const q = query as { userId?: string }
  if (q.userId) {
    userId.value = q.userId
  }
  myId.value = getSession().userId
})

/** onShow 刷新：从关注列表 / 登录页回来都重拉 */
onShow(() => {
  if (!getSession().tokenValue) {
    void uni.navigateTo({ url: '/pages/login/index?redirect=user' })
    return
  }
  if (!userId.value) {
    uni.showToast({ title: '用户不存在', icon: 'none' })
    return
  }
  myId.value = getSession().userId
  void loadProfile()
  reload()
})

onReachBottom(() => {
  void fetchPage(false)
})
</script>

<template>
  <view class="page">
    <template v-if="profile">
      <!-- 资料头 -->
      <view class="who">
        <view class="who__row">
          <view class="who__avatar">
            <image v-if="profile.avatarUrl" class="who__img" :src="profile.avatarUrl" mode="aspectFill" />
            <text v-else class="who__t">{{ initial(profile.nickname) }}</text>
          </view>
          <view class="who__meta">
            <view class="who__top">
              <text class="who__nick">{{ profile.nickname || '校园用户' }}</text>
              <text v-if="profile.authStatus === 2" class="who__badge">已认证</text>
              <text v-if="isSelf" class="who__badge who__badge--me">我的主页</text>
            </view>
            <text class="who__sub">信用 {{ profile.creditScore ?? 100 }} 分</text>
          </view>
          <view
            v-if="!isSelf"
            class="who__btn"
            :class="{ 'who__btn--on': profile.following }"
            hover-class="who__btn--active"
            @click="toggleFollow"
          >
            {{ acting ? '处理中' : profile.following ? '已关注' : '+ 关注' }}
          </view>
        </view>

        <view class="counts">
          <view class="counts__item" hover-class="counts__item--active" @click="goFollows('following')">
            <text class="counts__num">{{ profile.followingCount }}</text>
            <text class="counts__label">关注</text>
          </view>
          <view class="counts__item" hover-class="counts__item--active" @click="goFollows('fans')">
            <text class="counts__num">{{ profile.fansCount }}</text>
            <text class="counts__label">粉丝</text>
          </view>
          <view class="counts__item">
            <text class="counts__num">{{ profile.onSaleCount }}</text>
            <text class="counts__label">在售</text>
          </view>
        </view>
      </view>

      <!-- 在售商品：双列瀑布流，复用首页卡片 -->
      <view v-if="!goods.length && loading" class="state"><text class="state__t">加载中…</text></view>
      <view v-else-if="!goods.length" class="state"><text class="state__t">TA 还没有在售的闲置</text></view>

      <view v-else class="falls">
        <view class="col">
          <GoodsCard v-for="it in leftColumn" :key="it.id" :item="it" />
        </view>
        <view class="col">
          <GoodsCard v-for="it in rightColumn" :key="it.id" :item="it" />
        </view>
      </view>

      <view v-if="goods.length" class="loadmore">
        <text>{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</text>
      </view>
    </template>

    <view v-else class="state"><text class="state__t">加载中…</text></view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: $cm-color-bg;
}

/* ── 资料头 ─────────────────────────────────────────────── */

.who {
  padding: $cm-space-5;
  background: $cm-color-surface;
  margin-bottom: $cm-space-3;
}

.who__row {
  display: flex;
  align-items: center;
  gap: $cm-space-4;
}

.who__avatar {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $cm-color-brand-bg;
  overflow: hidden;
}

.who__img {
  width: 100%;
  height: 100%;
}

.who__t {
  font-size: 52rpx;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.who__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.who__top {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.who__nick {
  font-size: $cm-font-size-lg;
  font-weight: 700;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.who__badge {
  flex: none;
  padding: 4rpx 12rpx;
  font-size: 20rpx;
  color: $cm-color-ok;
  background: #f0faf4;
  border-radius: 8rpx;
}

.who__badge--me {
  color: $cm-color-text-3;
  background: $cm-color-bg;
}

.who__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* 主色只落在关注按钮这一处 */
.who__btn {
  flex: none;
  min-width: 144rpx;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: 30rpx;
}

.who__btn--on {
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
}

.who__btn--active {
  opacity: 0.8;
}

/* ── 计数三连 ───────────────────────────────────────────── */

.counts {
  display: flex;
  gap: $cm-space-8;
  margin-top: $cm-space-4;
}

.counts__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.counts__item--active {
  opacity: 0.7;
}

.counts__num {
  font-size: 34rpx;
  font-weight: 700;
  color: $cm-color-text-1;
}

.counts__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 商品瀑布流：与首页同款容器 ────────────────────────── */

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

.state {
  padding: 160rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.loadmore {
  padding: $cm-space-4;
  text-align: center;
}

.loadmore text {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}
</style>
