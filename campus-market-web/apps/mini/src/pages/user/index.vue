<script setup lang="ts">
/**
 * 个人主页（notes/22 S2 的宿主页面，S4 动态 Tab 在此落地）
 *
 * <p>资料头 + 关注按钮 + 两个 tab：TA 的闲置（双列瀑布流，复用首页 GoodsCard）/ TA 的动态。
 * 动态 tab 懒加载：首屏只拉商品，点开才请求动态，进主页不会一次发两串请求。
 *
 * <p>触底加载按当前 tab 分派，`finished` 各自独立——两个 tab 的分页游标不能共用，
 * 否则切 tab 后会跳页或漏页。
 *
 * <p>关注按钮与点赞都乐观更新，失败不动界面，下次进页回到服务端真值。
 */

import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import GoodsCard from '@/components/GoodsCard.vue'
import MomentCard from '@/components/MomentCard.vue'
import CmNav from '@/components/CmNav.vue'
import { userItemApi, type ApiItemVO } from '@/api/user'
import { userSocialApi as socialApi, type UserProfileVO } from '@/api/social'
import { momentApi, type MomentVO } from '@/api/moment'
import { userReviewApi, type MiniReviewVO, type ReviewSummaryVO } from '@/api/review'
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

/* ── Tab 与动态 ────────────────────────────────────────── */

const tab = ref<'items' | 'moments' | 'reviews' | 'favorites'>('items')

/* ── 收藏 tab（notes/24）：仅本人可见，接口只返回登录人自己的收藏 ── */
const favItems = ref<ApiItemVO[]>([])
const favPage = ref(1)
const favTotal = ref(0)
const favLoading = ref(false)
const favLoaded = ref(false)

async function fetchFavPage(reset: boolean): Promise<void> {
  if (favLoading.value) return
  if (!reset && favItems.value.length >= favTotal.value) return
  favLoading.value = true
  const p = reset ? 1 : favPage.value + 1
  try {
    const res = await userItemApi.favorites(p, PAGE)
    const rows: ApiItemVO[] = res.list ?? []
    favItems.value = reset ? rows : [...favItems.value, ...rows]
    favPage.value = p
    favTotal.value = Number(res.total ?? favItems.value.length)
    favLoaded.value = true
  } catch {
    if (reset) {
      favItems.value = []
      favTotal.value = 0
    }
  } finally {
    favLoading.value = false
  }
}

/* ── 评价 tab：汇总 + 列表，与动态同样懒加载 ── */
const reviews = ref<MiniReviewVO[]>([])
const reviewSummary = ref<ReviewSummaryVO | null>(null)
const reviewPage = ref(1)
const reviewTotal = ref(0)
const reviewLoading = ref(false)
const reviewsLoaded = ref(false)

async function fetchReviewPage(reset: boolean): Promise<void> {
  if (reviewLoading.value) return
  if (!reset && reviews.value.length >= reviewTotal.value) return
  reviewLoading.value = true
  const p = reset ? 1 : reviewPage.value + 1
  try {
    const res = await userReviewApi.list(userId.value, p, PAGE)
    const rows = res.list ?? []
    reviews.value = reset ? rows : [...reviews.value, ...rows]
    reviewPage.value = p
    reviewTotal.value = Number(res.total ?? reviews.value.length)
    if (reset) reviewSummary.value = await userReviewApi.summary(userId.value)
    reviewsLoaded.value = true
  } catch {
    if (reset) {
      reviews.value = []
      reviewTotal.value = 0
    }
  } finally {
    reviewLoading.value = false
  }
}

const moments = ref<MomentVO[]>([])
const momentPage = ref(1)
const momentTotal = ref(0)
const momentLoading = ref(false)
const momentsLoaded = ref(false)
const likePend = ref('')

async function fetchMomentPage(reset: boolean): Promise<void> {
  if (momentLoading.value) return
  if (!reset && moments.value.length >= momentTotal.value) return
  momentLoading.value = true
  const p = reset ? 1 : momentPage.value + 1
  try {
    const res = await momentApi.userMoments(userId.value, p, PAGE)
    const rows = res.list ?? []
    moments.value = reset ? rows : [...moments.value, ...rows]
    momentPage.value = p
    momentTotal.value = rows.length < PAGE ? moments.value.length : Number(res.total)
    momentsLoaded.value = true
  } catch {
    if (reset) {
      moments.value = []
      momentTotal.value = 0
    }
  } finally {
    momentLoading.value = false
  }
}

function switchTab(next: 'items' | 'moments' | 'reviews' | 'favorites'): void {
  if (tab.value === next) return
  tab.value = next
  if (next === 'moments' && !momentsLoaded.value) void fetchMomentPage(true)
  if (next === 'reviews' && !reviewsLoaded.value) void fetchReviewPage(true)
  if (next === 'favorites' && !favLoaded.value) void fetchFavPage(true)
}

async function toggleLike(m: MomentVO): Promise<void> {
  if (likePend.value === m.id) return
  likePend.value = m.id
  const wasLiked = m.liked
  const before = Number(m.likeCount)
  m.liked = !wasLiked
  m.likeCount = String(Math.max(before + (wasLiked ? -1 : 1), 0))
  try {
    if (wasLiked) {
      await momentApi.unlike(m.id)
    } else {
      await momentApi.like(m.id)
    }
  } catch {
    m.liked = wasLiked
    m.likeCount = String(before)
    uni.showToast({ title: '操作失败', icon: 'none' })
  } finally {
    likePend.value = ''
  }
}

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
  // 换人或重进：动态与收藏全部作废，回到「未加载」按需再拉
  moments.value = []
  momentPage.value = 1
  momentTotal.value = 0
  momentsLoaded.value = false
  favItems.value = []
  favPage.value = 1
  favTotal.value = 0
  favLoaded.value = false
  if (tab.value === 'moments') void fetchMomentPage(true)
  if (tab.value === 'favorites') void fetchFavPage(true)
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

function goDetail(id: string): void {
  void uni.navigateTo({ url: `/pages/item/detail?id=${id}` })
}

/** 价格去掉无意义的 .00：收藏里大多是整数价，带两位小数反而比别处长 */
function priceText(p: string): string {
  const n = Number(p)
  if (!Number.isFinite(n)) return p
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

function goFollows(tab: 'following' | 'fans'): void {
  void uni.navigateTo({ url: `/pages/follows/index?tab=${tab === 'fans' ? 'fans' : 'following'}&userId=${userId.value}` })
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

onLoad((query) => {
  const q = query as { userId?: string; tab?: string }
  if (q.userId) {
    userId.value = q.userId
  }
  // ?tab=moments / favorites 直接落在对应 Tab（「我的」页的快捷入口这么进）
  if (q.tab === 'moments' || q.tab === 'favorites') {
    tab.value = q.tab
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
  if (tab.value === 'items') {
    void fetchPage(false)
  } else if (tab.value === 'moments') {
    void fetchMomentPage(false)
  } else if (tab.value === 'reviews') {
    void fetchReviewPage(false)
  } else {
    void fetchFavPage(false)
  }
})
</script>

<template>
  <view class="page">
    <!-- 昵称与头像在资料头里，导航栏只留返回，不再重复写标题 -->
    <CmNav />

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

      <!-- Tab：闲置 / 动态 / 评价（后两个懒加载） -->
      <view class="tabs">
        <view class="tab" :class="{ 'tab--on': tab === 'items' }" @click="switchTab('items')">
          <text class="tab__t">{{ isSelf ? '我的闲置' : 'TA 的闲置' }}</text>
          <view class="tab__ink" />
        </view>
        <view class="tab" :class="{ 'tab--on': tab === 'moments' }" @click="switchTab('moments')">
          <text class="tab__t">动态</text>
          <view class="tab__ink" />
        </view>
        <view class="tab" :class="{ 'tab--on': tab === 'reviews' }" @click="switchTab('reviews')">
          <text class="tab__t">评价{{ reviewSummary?.total ? ` ${reviewSummary.total}` : '' }}</text>
          <view class="tab__ink" />
        </view>
        <view v-if="isSelf" class="tab" :class="{ 'tab--on': tab === 'favorites' }" @click="switchTab('favorites')">
          <text class="tab__t">收藏</text>
          <view class="tab__ink" />
        </view>
      </view>

      <block v-if="tab === 'items'">
        <!-- 在售商品：双列瀑布流，复用首页卡片 -->
        <view v-if="!goods.length && loading" class="state"><text class="state__t">加载中…</text></view>
        <view v-else-if="!goods.length" class="state">
          <text class="state__t">{{ isSelf ? '你还没有在售的闲置' : 'TA 还没有在售的闲置' }}</text>
        </view>

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
      </block>

      <block v-else-if="tab === 'moments'">
        <view v-if="!moments.length && momentLoading" class="state"><text class="state__t">加载中…</text></view>
        <view v-else-if="!moments.length" class="state"><text class="state__t">TA 还没发过动态</text></view>

        <view v-else class="feed">
          <MomentCard v-for="m in moments" :key="m.id" :moment="m" @toggle-like="toggleLike" />
        </view>

        <view v-if="moments.length" class="loadmore">
          <text>{{ momentLoading ? '加载中…' : moments.length >= momentTotal ? '没有更多了' : '上拉加载更多' }}</text>
        </view>
      </block>

      <!-- 评价：汇总 + 列表（一单一评，仅已完成订单可评） -->
      <block v-else-if="tab === 'reviews'">
        <view v-if="!reviews.length && reviewLoading" class="state"><text class="state__t">加载中…</text></view>
        <view v-else-if="!reviews.length" class="state"><text class="state__t">还没有人评价 TA</text></view>

        <block v-else>
          <view v-if="reviewSummary?.total" class="rvsum">
            <view class="rvsum__avg">
              <text class="rvsum__num">{{ reviewSummary.avg }}</text>
              <text class="rvsum__sub">平均分 · {{ reviewSummary.total }} 条</text>
            </view>
            <view class="rvsum__bars">
              <view v-for="star in [5, 4, 3, 2, 1]" :key="star" class="rvbar">
                <text class="rvbar__s">{{ star }} 星</text>
                <view class="rvbar__track">
                  <view
                    class="rvbar__fill"
                    :style="{
                      width: `${reviewSummary.total ? (Number(reviewSummary.counts[String(star)] ?? 0) / reviewSummary.total) * 100 : 0}%`
                    }"
                  />
                </view>
                <text class="rvbar__n">{{ reviewSummary.counts[String(star)] ?? 0 }}</text>
              </view>
            </view>
          </view>

          <view class="rvlist">
            <view v-for="r in reviews" :key="r.id" class="rvitem">
              <view class="rvitem__hd">
                <text class="rvitem__who">{{ r.reviewerNick || '校园用户' }}</text>
                <text class="rvitem__stars">
                  <text v-for="i in 5" :key="i" class="star" :class="{ 'star--on': i <= r.rating }">★</text>
                </text>
              </view>
              <view v-if="r.tags?.length" class="rvitem__tags">
                <text v-for="t in r.tags" :key="t" class="chip">{{ t }}</text>
              </view>
              <text v-if="r.comment" class="rvitem__text">{{ r.comment }}</text>
            </view>
          </view>
        </block>

        <view v-if="reviews.length" class="loadmore">
          <text>{{ reviewLoading ? '加载中…' : reviews.length >= reviewTotal ? '没有更多了' : '上拉加载更多' }}</text>
        </view>
      </block>

      <!-- 收藏：只在自己的主页出现；已售出/已下架照常展示，用户要知道「那件被买走了」 -->
      <block v-else>
        <view v-if="!favItems.length && favLoading" class="state"><text class="state__t">加载中…</text></view>
        <view v-else-if="!favItems.length" class="state">
          <text class="state__t">还没有收藏，看到喜欢的点一下收藏</text>
        </view>

        <view v-else class="favlist">
          <view v-for="it in favItems" :key="it.id" class="favrow" hover-class="favrow--active" @click="goDetail(it.id)">
            <image v-if="it.mainImage" class="favrow__img" :src="it.mainImage" mode="aspectFill" />
            <view v-else class="favrow__img favrow__img--empty"><text>无图</text></view>
            <view class="favrow__main">
              <text class="favrow__title">{{ it.title }}</text>
              <view class="favrow__meta">
                <text class="favrow__price">¥{{ priceText(it.price) }}</text>
                <text class="favrow__tag" :class="{ 'favrow__tag--off': it.status !== 'ON_SALE' }">
                  {{ it.status === 'ON_SALE' ? '在售' : '已下架' }}
                </text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="favItems.length" class="loadmore">
          <text>{{ favLoading ? '加载中…' : favItems.length >= favTotal ? '没有更多了' : '上拉加载更多' }}</text>
        </view>
      </block>
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

/* ── 收藏 Tab：单列横条，状态一眼可见 ─────────────────── */

.favlist {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
  padding: 0 $cm-space-4;
}

.favrow {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
}

.favrow--active {
  opacity: 0.85;
}

.favrow__img {
  width: 168rpx;
  height: 168rpx;
  flex: none;
  border-radius: $cm-radius-sm;
  background: $cm-color-bg;
}

.favrow__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.favrow__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 4rpx 0;
}

.favrow__title {
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.favrow__meta {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.favrow__price {
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-price;
}

.favrow__tag {
  padding: 2rpx 12rpx;
  border-radius: $cm-radius-pill;
  font-size: $cm-font-size-xs;
  color: $cm-color-ok;
  background: $cm-color-ok-bg;
}

.favrow__tag--off {
  color: $cm-color-text-3;
  background: $cm-color-bg;
}

/* ── Tab 条：闲置 / 动态 ───────────────────────────────── */
.tabs {
  display: flex;
  gap: $cm-space-8;
  margin-bottom: $cm-space-3;
  padding: 0 $cm-space-5;
  background: $cm-color-surface;
}

.tab {
  position: relative;
  height: 84rpx;
  display: flex;
  align-items: center;
}

.tab__t {
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
}

.tab--on .tab__t {
  font-weight: 700;
  color: $cm-color-text-1;
}

.tab__ink {
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 44rpx;
  height: 6rpx;
  border-radius: 3rpx;
  background: transparent;
  transform: translateX(-50%);
}

.tab--on .tab__ink {
  background: $cm-color-brand;
}

/* 动态流：单列，与广场同节奏 */
.feed {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
  padding: 0 $cm-space-4;
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

/* ── 评价 tab ─────────────────────────────────────────── */

.rvsum {
  display: flex;
  gap: $cm-space-4;
  padding: $cm-space-4;
  margin-bottom: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.rvsum__avg {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 140rpx;
}

.rvsum__num {
  font-size: 48rpx;
  font-weight: 600;
  color: $cm-color-text-1;
}

.rvsum__sub {
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.rvsum__bars {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.rvbar {
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.rvbar__s {
  width: 62rpx;
}

.rvbar__track {
  flex: 1;
  height: 10rpx;
  background: $cm-color-bg;
  border-radius: 999rpx;
  overflow: hidden;
}

.rvbar__fill {
  height: 100%;
  background: $cm-color-brand;
  border-radius: 999rpx;
}

.rvbar__n {
  width: 48rpx;
  text-align: right;
}

.rvlist {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.rvitem {
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.rvitem__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.rvitem__who {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
}

.rvitem__stars {
  display: flex;
  gap: 4rpx;
}

.star {
  font-size: 22rpx;
  color: $cm-color-border;
}

.star--on {
  color: $cm-color-brand;
}

.rvitem__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: $cm-space-2;
}

.chip {
  padding: 4rpx 14rpx;
  font-size: 20rpx;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border-radius: 999rpx;
}

.rvitem__text {
  margin-top: $cm-space-2;
  font-size: $cm-font-size-sm;
  line-height: 1.7;
  color: $cm-color-text-1;
}
</style>
