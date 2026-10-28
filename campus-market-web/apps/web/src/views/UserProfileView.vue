<script setup lang="ts">
/**
 * 个人主页（notes/22 S2 的宿主页面）
 *
 * <p>资料卡 + 关注按钮 + TA 的在售闲置。动态 Tab（S4）还没实现，先给占位空态——
 * 占位写在同一个 tab 结构里，S4 落地时只换内容不换骨架。
 *
 * <p>商品网格复用 ProductCard（经 toGoodsItem 映射），与首页同一张卡：
 * 主页商品不允许长出第二套视觉，否则改卡片要改两处。
 *
 * <p>关注按钮乐观更新：成功即翻转并同步粉丝数，失败不动界面（http 层已提示），
 * 下次进页回到服务端真值。自己的主页不显示关注按钮，粉丝数照常展示。
 */

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userItemApi, userSocialApi, type UserProfileVO } from '@campus/api'
import type { GoodsItem } from '@campus/shared'
import { toGoodsItem } from '@/utils/item-map'
import ProductCard from '@/components/ProductCard.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

const PAGE_SIZE = 12

const userId = computed(() => route.params.id as string)

const profile = ref<UserProfileVO | null>(null)
const loading = ref(false)

const tab = ref<'items' | 'moments'>('items')

const items = ref<GoodsItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const itemsLoading = ref(false)
/** 逐行 loading：关注请求在途时按钮不重复点 */
const acting = ref(false)

const isSelf = computed(() => !!profile.value?.self)
const nickname = computed(() => profile.value?.nickname || '校园用户')

/** 自己的主页：直接在这里编辑资料（与顶栏下拉共用弹层） */
const editSelfOpen = ref(false)

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

function switchTab(next: 'items' | 'moments'): void {
  if (tab.value === next) return
  tab.value = next
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
    if (userId.value) {
      pageNum.value = 1
      void loadProfile()
      void loadItems()
    }
  }
)

onMounted(() => {
  if (!session.isLogin) {
    gotoLogin()
    return
  }
  void loadProfile()
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

    <!-- Tab：商品 / 动态占位 -->
    <nav class="tabs">
      <button type="button" class="tabs__item" :class="{ on: tab === 'items' }" @click="switchTab('items')">
        TA 的闲置（{{ profile.onSaleCount }}）
      </button>
      <button type="button" class="tabs__item" :class="{ on: tab === 'moments' }" @click="switchTab('moments')">
        动态
      </button>
    </nav>

    <template v-if="tab === 'items'">
      <p v-if="!itemsLoading && !items.length" class="empty">TA 还没有在售的闲置</p>
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

    <p v-else class="empty">动态功能即将上线，敬请期待</p>

    <ProfileEditDialog v-model="editSelfOpen" />
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

.pager {
  justify-content: center;
}
</style>
