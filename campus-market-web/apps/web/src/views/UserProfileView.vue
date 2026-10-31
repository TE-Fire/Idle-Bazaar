<script setup lang="ts">
/**
 * 个人主页（notes/22 S2 的宿主页面，S4 动态 Tab 在此落地）
 *
 * <p>资料卡 + 关注按钮 + 两个 Tab：TA 的在售闲置 / TA 的动态。
 * 动态 Tab 懒加载——首屏只拉商品，点开才请求动态，避免进主页发两串请求。
 *
 * <p>商品网格复用 ProductCard（经 toGoodsItem 映射），与首页同一张卡：
 * 主页商品不允许长出第二套视觉，否则改卡片要改两处。动态同理复用 MomentCard。
 *
 * <p>关注按钮乐观更新：成功即翻转并同步粉丝数，失败不动界面（http 层已提示），
 * 下次进页回到服务端真值。自己的主页不显示关注按钮，粉丝数照常展示。
 */

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userItemApi, userMomentApi, userReviewApi, userSocialApi, REPORT_TARGET, type MomentVO, type ReviewSummaryVO, type UserProfileVO, type UserReviewVO } from '@campus/api'
import { formatRelativeTime, type GoodsItem } from '@campus/shared'
import { toGoodsItem } from '@/utils/item-map'
import ProductCard from '@/components/ProductCard.vue'
import MomentCard from '@/components/MomentCard.vue'
import FavoritesPanel from '@/components/FavoritesPanel.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

const PAGE_SIZE = 12

const userId = computed(() => route.params.id as string)

const profile = ref<UserProfileVO | null>(null)
const loading = ref(false)

/**
 * Tab：闲置 / 动态 / 评价 / 收藏。
 *
 * <p>收藏是私有数据（`/api/item/favorites` 只返回登录人自己的），所以这个 Tab
 * **只在自己的主页出现**；靠 `profile.self` 判断，不靠前端拿 id 比对。
 */
type ProfileTab = 'items' | 'moments' | 'reviews' | 'favorites'

const tab = ref<ProfileTab>('items')

const items = ref<GoodsItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const itemsLoading = ref(false)
/** 逐行 loading：关注请求在途时按钮不重复点 */
const acting = ref(false)

const MOMENT_SIZE = 10
const moments = ref<MomentVO[]>([])
const momentTotal = ref(0)
const momentPage = ref(1)
const momentsLoading = ref(false)
/** 懒加载：切过一次才有值，之后切回不重复请求 */
const momentsLoaded = ref(false)
const likePending = ref<string | null>(null)

const isSelf = computed(() => !!profile.value?.self)
const nickname = computed(() => profile.value?.nickname || '校园用户')

/* ── 评价 Tab（notes/13 O18）：汇总与列表都免登录，懒加载 ── */
const REVIEW_SIZE = 10
const reviewSummary = ref<ReviewSummaryVO | null>(null)
const reviews = ref<UserReviewVO[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewLoading = ref(false)
const reviewsLoaded = ref(false)

async function loadReviews(reset = false): Promise<void> {
  if (reviewLoading.value) return
  reviewLoading.value = true
  try {
    if (reset) reviewPage.value = 1
    const p = await userReviewApi.list(userId.value, reviewPage.value, REVIEW_SIZE)
    reviews.value = reset ? p.list : [...reviews.value, ...p.list]
    reviewTotal.value = Number(p.total)
    if (reset) {
      // 汇总与列表同源（同一个 target_id），一次拉到位，切 Tab 不再闪
      reviewSummary.value = await userReviewApi.summary(userId.value)
    }
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

/** 自己的主页：直接在这里编辑资料（与顶栏下拉共用弹层） */
const editSelfOpen = ref(false)

/** 举报弹窗（只这个主页的动态流里有入口） */
const reportOpen = ref(false)
const reportId = ref('')

function openReport(m: MomentVO): void {
  reportId.value = m.id
  reportOpen.value = true
}

async function loadProfile(): Promise<void> {
  loading.value = true
  try {
    profile.value = await userSocialApi.profile(userId.value)
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function loadItems(): Promise<void> {
  itemsLoading.value = true
  try {
    const page = await userItemApi.list({
      sellerId: userId.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    items.value = page.list.map(toGoodsItem)
    total.value = Number(page.total)
  } catch {
    items.value = []
    total.value = 0
  } finally {
    itemsLoading.value = false
  }
}

async function loadMoments(reset = false): Promise<void> {
  if (momentsLoading.value) return
  momentsLoading.value = true
  const next = reset ? 1 : momentPage.value + 1
  try {
    const page = await userMomentApi.userMoments(userId.value, next, MOMENT_SIZE)
    const rows = page.list ?? []
    moments.value = reset ? rows : [...moments.value, ...rows]
    momentPage.value = next
    momentTotal.value = Number(page.total)
    if (rows.length < MOMENT_SIZE) momentTotal.value = moments.value.length
    momentsLoaded.value = true
  } catch {
    if (reset) {
      moments.value = []
      momentTotal.value = 0
    }
  } finally {
    momentsLoading.value = false
  }
}

function applyTab(next: ProfileTab): void {
  if (tab.value === next) return
  tab.value = next
  if (next === 'moments' && !momentsLoaded.value) void loadMoments(true)
  if (next === 'reviews' && !reviewsLoaded.value) void loadReviews(true)
}

/** `?tab=` 是唯一入口约定：旧 `/favorites` 路由重定向过来，链接也能直接落到收藏 */
function applyQueryTab(): void {
  const v = route.query.tab
  const next: ProfileTab = v === 'moments' || v === 'reviews' || v === 'favorites' ? v : 'items'
  // 别人的主页没有收藏 Tab：URL 里残留下来的参数不能把页面切到一个不存在的内容区
  if (next === 'favorites' && !isSelf.value) {
    applyTab('items')
    return
  }
  applyTab(next)
}

function switchTab(next: ProfileTab): void {
  if (tab.value === next) return
  applyTab(next)
  void router.replace({
    query: { ...route.query, tab: next === 'items' ? undefined : next }
  })
}

/** 点赞：乐观翻转，失败按原值回滚 */
async function toggleMomentLike(m: MomentVO): Promise<void> {
  if (likePending.value === m.id) return
  likePending.value = m.id
  const wasLiked = m.liked
  const before = Number(m.likeCount)
  m.liked = !wasLiked
  m.likeCount = String(Math.max(before + (wasLiked ? -1 : 1), 0))
  try {
    if (wasLiked) {
      await userMomentApi.unlike(m.id)
    } else {
      await userMomentApi.like(m.id)
    }
  } catch {
    m.liked = wasLiked
    m.likeCount = String(before)
    ElMessage.error('操作失败，请重试')
  } finally {
    likePending.value = null
  }
}

function goMomentDetail(m: MomentVO): void {
  void router.push({ name: 'moment-detail', params: { id: m.id } })
}

/** 关注 / 取关：幂等接口，本地只做乐观翻转 */
async function toggleFollow(): Promise<void> {
  const p = profile.value
  if (!p || acting.value) return
  acting.value = true
  const wasFollowing = p.following
  try {
    if (wasFollowing) {
      await userSocialApi.unfollow(p.userId)
    } else {
      await userSocialApi.follow(p.userId)
    }
    p.following = !wasFollowing
    p.fansCount = String(Math.max(Number(p.fansCount) + (wasFollowing ? -1 : 1), 0))
  } finally {
    acting.value = false
  }
}

function goFollows(type: 'following' | 'fans'): void {
  void router.push({ name: 'user-follow', params: { id: userId.value }, query: { tab: type === 'fans' ? 'fans' : undefined } })
}

function gotoLogin(): void {
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
}

watch(
  () => userId.value,
  () => {
    if (!userId.value) return
    pageNum.value = 1
    // 换人不保留上一个主页的 Tab，否则会看到「别人的收藏」
    tab.value = 'items'
    // 换人：动态与评价全部作废，重新按需拉
    moments.value = []
    momentTotal.value = 0
    momentPage.value = 1
    momentsLoaded.value = false
    reviews.value = []
    reviewTotal.value = 0
    reviewPage.value = 1
    reviewsLoaded.value = false
    reviewSummary.value = null
    // Tab 的应用要等 profile 回来（收藏 Tab 依赖 self）
    void loadProfile().then(applyQueryTab)
    void loadItems()
  }
)

watch(() => route.query.tab, applyQueryTab)

onMounted(async () => {
  if (!session.isLogin) {
    gotoLogin()
    return
  }
  await loadProfile()
  applyQueryTab()
  void loadItems()
})
</script>

<template>
  <div v-if="profile" class="cm-shell profile">
    <!-- 资料卡 -->
    <section class="who">
      <div class="who__avatar">
        <img v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="" />
        <span v-else class="who__t">{{ (profile.nickname || '?').slice(0, 1) }}</span>
      </div>

      <div class="who__meta">
        <p class="who__top">
          <span class="who__nick">{{ nickname }}</span>
          <span v-if="profile.authStatus === 2" class="who__badge">已认证</span>
          <span v-if="isSelf" class="who__badge who__badge--me">这是你的主页</span>
        </p>
        <p class="who__sub">信用 {{ profile.creditScore ?? 100 }} 分</p>

        <div class="counts">
          <button type="button" class="counts__item" @click="goFollows('following')">
            <b class="counts__num">{{ profile.followingCount }}</b>
            <span class="counts__label">关注</span>
          </button>
          <button type="button" class="counts__item" @click="goFollows('fans')">
            <b class="counts__num">{{ profile.fansCount }}</b>
            <span class="counts__label">粉丝</span>
          </button>
          <span class="counts__item counts__item--flat">
            <b class="counts__num">{{ profile.onSaleCount }}</b>
            <span class="counts__label">在售</span>
          </span>
        </div>
      </div>

      <button
        v-if="!isSelf"
        type="button"
        class="who__btn"
        :class="{ 'who__btn--on': profile.following }"
        :disabled="acting"
        @click="toggleFollow"
      >
        {{ acting ? '处理中' : profile.following ? '已关注' : '+ 关注' }}
      </button>
      <button v-else type="button" class="who__edit" @click="editSelfOpen = true">
        编辑资料
      </button>
    </section>

    <!-- Tab：闲置 / 动态 / 评价 / 收藏（收藏仅本人） -->
    <nav class="tabs">
      <button type="button" class="tabs__item" :class="{ on: tab === 'items' }" @click="switchTab('items')">
        {{ isSelf ? '我的闲置' : 'TA 的闲置' }}（{{ profile.onSaleCount }}）
      </button>
      <button type="button" class="tabs__item" :class="{ on: tab === 'moments' }" @click="switchTab('moments')">
        动态
      </button>
      <button type="button" class="tabs__item" :class="{ on: tab === 'reviews' }" @click="switchTab('reviews')">
        评价{{ reviewSummary?.total ? `（${reviewSummary.total}）` : '' }}
      </button>
      <button
        v-if="isSelf"
        type="button"
        class="tabs__item"
        :class="{ on: tab === 'favorites' }"
        @click="switchTab('favorites')"
      >
        收藏
      </button>
    </nav>

    <template v-if="tab === 'items'">
      <p v-if="!itemsLoading && !items.length" class="empty">
        {{ isSelf ? '你还没有在售的闲置，去发布一件吧' : 'TA 还没有在售的闲置' }}
      </p>
      <div v-loading="itemsLoading" class="grid">
        <ProductCard v-for="it in items" :key="it.id" :item="it" />
      </div>
      <el-pagination
        v-if="total > PAGE_SIZE"
        class="pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="PAGE_SIZE"
        :current-page="pageNum"
        @current-change="(p: number) => { pageNum = p; loadItems() }"
      />
    </template>

    <template v-else-if="tab === 'moments'">
      <p v-if="!momentsLoading && !moments.length" class="empty">TA 还没发过动态</p>
      <div class="feed">
        <MomentCard
          v-for="m in moments"
          :key="m.id"
          :moment="m"
          @toggle-like="toggleMomentLike"
          @comment="goMomentDetail"
          @report="openReport"
        />
      </div>
      <div v-if="moments.length < momentTotal" class="more">
        <el-button :loading="momentsLoading" @click="loadMoments(false)">加载更多</el-button>
      </div>
    </template>

    <!-- 评价：汇总卡 + 列表（一单一评，仅已完成订单可评） -->
    <template v-else-if="tab === 'reviews'">
      <p v-if="!reviewLoading && !reviews.length" class="empty">还没有人评价 TA</p>

      <div v-if="reviewSummary?.total" class="rvsum">
        <div class="rvsum__avg">
          <b>{{ reviewSummary.avg }}</b>
          <span>平均分 · {{ reviewSummary.total }} 条</span>
        </div>
        <div class="rvsum__bars">
          <div v-for="star in [5, 4, 3, 2, 1]" :key="star" class="rvbar">
            <span class="rvbar__star">{{ star }} 星</span>
            <span class="rvbar__track">
              <i
                class="rvbar__fill"
                :style="{
                  width: `${reviewSummary.total ? (Number(reviewSummary.counts[star] ?? 0) / reviewSummary.total) * 100 : 0}%`
                }"
              />
            </span>
            <span class="rvbar__n">{{ reviewSummary.counts[star] ?? 0 }}</span>
          </div>
        </div>
      </div>

      <ul class="rvlist">
        <li v-for="r in reviews" :key="r.id" class="rvitem">
          <div class="rvitem__hd">
            <span class="rvitem__who">
              <img v-if="r.reviewerAvatar" :src="r.reviewerAvatar" alt="" class="rvitem__ava" />
              <span v-else class="rvitem__ava rvitem__ava--txt">{{ (r.reviewerNick || '?').slice(0, 1) }}</span>
              {{ r.reviewerNick || '校园用户' }}
            </span>
            <el-rate :model-value="r.rating" disabled size="small" />
          </div>
          <p v-if="r.tags?.length" class="rvitem__tags">
            <span v-for="t in r.tags" :key="t" class="rvitem__tag">{{ t }}</span>
          </p>
          <p v-if="r.comment" class="rvitem__text">{{ r.comment }}</p>
          <span class="rvitem__time">{{ formatRelativeTime(r.createTime) }}</span>
        </li>
      </ul>

      <div v-if="reviews.length < reviewTotal" class="more">
        <el-button
          :loading="reviewLoading"
          @click="
            () => {
              reviewPage += 1
              loadReviews()
            }
          "
        >
          加载更多
        </el-button>
      </div>
    </template>

    <!-- 收藏：只在自己的主页出现，内容区自带分页与取消收藏 -->
    <FavoritesPanel v-else-if="tab === 'favorites'" />

    <ProfileEditDialog v-model="editSelfOpen" />
    <ReportDialog v-model="reportOpen" :target-type="REPORT_TARGET.MOMENT" :target-id="reportId" />
  </div>

  <div v-else-if="!loading" class="cm-shell">
    <el-empty description="用户不存在">
      <el-button type="primary" @click="router.push({ name: 'home' })">回首页逛逛</el-button>
    </el-empty>
  </div>
</template>

<style scoped lang="scss">
.profile {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
  padding-bottom: var(--cm-space-8);
}

/* ── 资料卡 ─────────────────────────────────────────────── */

.who {
  display: flex;
  align-items: flex-start;
  gap: var(--cm-space-5);
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
}

.who__avatar {
  width: 72px;
  height: 72px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  background: var(--cm-color-brand-bg);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.who__t {
  font-size: 30px;
  font-weight: 700;
  color: var(--cm-color-text-1);
}

.who__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.who__top {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.who__nick {
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
  color: var(--cm-color-text-1);
}

.who__badge {
  padding: 2px 8px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-ok);
  background: #f0faf4;
  border-radius: var(--cm-radius-sm);

  &--me {
    color: var(--cm-color-text-2);
    background: var(--cm-color-bg);
  }
}

.who__sub {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 计数三连：关注 / 粉丝可点跳列表 ───────────────────── */

.counts {
  display: flex;
  gap: var(--cm-space-6);
  margin-top: var(--cm-space-1);
}

.counts__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0;
  background: none;
  border: none;
  cursor: pointer;
}

.counts__item--flat {
  cursor: default;
}

.counts__num {
  font-size: 18px;
  font-weight: 800;
  color: var(--cm-color-text-1);
}

.counts__label {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 主色只落在关注按钮这一处 */
.who__btn {
  flex: none;
  align-self: flex-start;
  height: 34px;
  min-width: 92px;
  padding: 0 var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border: 1px solid var(--cm-color-brand);
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover {
    background: var(--cm-color-brand-dark);
  }

  &--on {
    color: var(--cm-color-text-2);
    background: var(--cm-color-surface);
    border-color: var(--cm-color-border);

    &:hover {
      color: #cf1322;
      border-color: #cf1322;
    }
  }

  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
}

/* 自己的主页：次级描边按钮，与关注按钮同位 */
.who__edit {
  flex: none;
  align-self: flex-start;
  height: 34px;
  min-width: 92px;
  padding: 0 var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover {
    border-color: var(--cm-color-text-2);
  }
}

/* ── Tab ───────────────────────────────────────────────── */

.tabs {
  display: flex;
  gap: var(--cm-space-6);
  border-bottom: 1px solid var(--cm-color-border);
}

.tabs__item {
  height: 38px;
  padding: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;

  &.on {
    color: var(--cm-color-text-1);
    border-bottom-color: var(--cm-color-brand);
  }
}

/* ── 商品网格：与首页同款栅格 ──────────────────────────── */

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--cm-space-4);
  min-height: 120px;
}

.empty {
  margin: var(--cm-space-8) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* 动态流：单列，与广场同一宽度节奏 */
.feed {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  width: 100%;
  max-width: 720px;
}

.more {
  display: flex;
  justify-content: center;
  padding: var(--cm-space-2) 0;
}

.pager {
  justify-content: center;
}

/* ── 评价 Tab ─────────────────────────────────────────── */

/* 汇总卡：左边大平均分，右边五条分布条——一屏看清「几星居多」 */
.rvsum {
  display: flex;
  gap: var(--cm-space-6);
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

.rvsum__avg {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 108px;

  b {
    font-size: 32px;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }

  span {
    font-size: var(--cm-font-size-xs);
    color: var(--cm-color-text-3);
  }
}

.rvsum__bars {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rvbar {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.rvbar__star {
  width: 34px;
}

.rvbar__track {
  flex: 1;
  height: 6px;
  background: var(--cm-color-bg);
  border-radius: 999px;
  overflow: hidden;
}

.rvbar__fill {
  display: block;
  height: 100%;
  background: var(--cm-color-brand);
  border-radius: 999px;
}

.rvbar__n {
  width: 28px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.rvlist {
  width: 100%;
  max-width: 720px;
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.rvitem {
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

.rvitem__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-2);
}

.rvitem__who {
  display: inline-flex;
  align-items: center;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
}

.rvitem__ava {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
}

.rvitem__ava--txt {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
}

.rvitem__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-1);
  margin: var(--cm-space-2) 0 0;
}

.rvitem__tag {
  padding: 0 6px;
  font-size: var(--cm-font-size-xs);
  line-height: 20px;
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-sm);
}

.rvitem__text {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.7;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

.rvitem__time {
  display: block;
  margin-top: var(--cm-space-1);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
