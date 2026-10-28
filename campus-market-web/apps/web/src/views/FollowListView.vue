<script setup lang="ts">
/**
 * 关注 / 粉丝列表（notes/22 S2）
 *
 * <p>一页两用：`/follow` 看自己，`/user/:id/follow` 看他人。tab 写进 query（`?tab=fans`），
 * 刷新与后退都能回到同一个 tab；两个计数做成头部的大数字，点击即切 tab——用户关心的是
 * 「多少人关注我」而不是翻页，计数必须比列表先看到（notes/23 第二节）。
 *
 * <p>列表行点击进 TA 的个人主页（资料 + 在售闲置 + 关注入口）；主页落地前曾临时
 * 跳「TA 的关注列表」，现在统一走主页——主页才是关注关系的自然落点。
 *
 * <p>关注 / 取关是幂等的，本地只做乐观更新：成功后改该行 `following` 与计数，失败不折腾界面
 * （http 层已提示），下次进页自然回到服务端真值。
 */

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userSocialApi, type FollowListType, type FollowUserVO } from '@campus/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

const PAGE_SIZE = 10

/** 目标用户：路由带 id 就是看他人，否则是自己 */
const targetId = computed(() => (route.params.id as string | undefined) || undefined)
const isSelf = computed(() => !targetId.value || targetId.value === session.userId)

/** tab 来源是 query，非法值一律回落 following（分享链接里手改过也不会白屏） */
const tab = computed<FollowListType>(() => (route.query.tab === 'fans' ? 'fans' : 'following'))

const list = ref<FollowUserVO[]>([])
const total = ref(0)
const followingCount = ref(0)
const fansCount = ref(0)
const pageNum = ref(1)
const loading = ref(false)
/** 逐行 loading：只禁用被点的那一个，避免整列表灰掉 */
const pending = ref<Record<string, boolean>>({})

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await userSocialApi.list({
      type: tab.value,
      userId: targetId.value,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE
    })
    list.value = res.list
    // 后端 Long 统一转字符串，参与分页与比较前必须转数字
    total.value = Number(res.total)
    followingCount.value = Number(res.followingCount)
    fansCount.value = Number(res.fansCount)
  } catch {
    // http 层已提示；未登录（1001）会被统一带去登录页
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(next: FollowListType): void {
  if (tab.value === next) return
  pageNum.value = 1
  void router.replace({ query: { ...route.query, tab: next === 'fans' ? 'fans' : undefined } })
}

/** tab 变了才重查：route.query 变化也会触发 watch，切页时不动 */
watch(
  () => tab.value,
  () => void load()
)

/** 关注 / 取关：成功后本地翻转该行，并同步计数 */
async function toggle(u: FollowUserVO): Promise<void> {
  if (pending.value[u.userId]) return
  pending.value = { ...pending.value, [u.userId]: true }
  const wasFollowing = !!u.following
  try {
    if (wasFollowing) {
      await userSocialApi.unfollow(u.userId)
    } else {
      await userSocialApi.follow(u.userId)
    }
    u.following = !wasFollowing
    // 关注列表里取关 → 这行已不属于当前列表，移除比留在原地更符合预期
    if (tab.value === 'following' && wasFollowing) {
      list.value = list.value.filter((x) => x.userId !== u.userId)
      total.value = Math.max(total.value - 1, 0)
    }
    // 只有看自己的页才动「关注」计数（看他人时列表是人家的，我的操作不改他的数）
    if (isSelf.value) {
      followingCount.value = Math.max(followingCount.value + (wasFollowing ? -1 : 1), 0)
    }
    ElMessage.success({ message: wasFollowing ? '已取关' : '已关注', duration: 1500 })
  } finally {
    const next = { ...pending.value }
    delete next[u.userId]
    pending.value = next
  }
}

function formatTime(iso?: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

/** 行点击进 TA 的主页；自己的主页也可以进（显示「这是你的主页」） */
function goProfile(u: FollowUserVO): void {
  void router.push({ name: 'user-profile', params: { id: u.userId } })
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

/** 行内按钮只对「不是我自己」的人显示 */
function showAction(u: FollowUserVO): boolean {
  return u.userId !== session.userId
}

onMounted(() => {
  if (!session.isLogin) {
    ElMessage.info({ message: '请先登录', duration: 2000 })
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  void load()
})
</script>

<template>
  <div class="cm-shell follows">
    <header class="head">
      <h1 class="head__title">{{ isSelf ? '我的关注' : 'TA 的关注与粉丝' }}</h1>
      <RouterLink :to="{ name: 'home' }" class="head__link">去逛逛 →</RouterLink>
    </header>

    <!-- 两个大数字：用户先看「有多少」，再决定翻不翻列表 -->
    <section class="counts">
      <button type="button" class="counts__item" :class="{ on: tab === 'following' }" @click="switchTab('following')">
        <b class="counts__num">{{ followingCount }}</b>
        <span class="counts__label">关注</span>
      </button>
      <button type="button" class="counts__item" :class="{ on: tab === 'fans' }" @click="switchTab('fans')">
        <b class="counts__num">{{ fansCount }}</b>
        <span class="counts__label">粉丝</span>
      </button>
    </section>

    <p v-if="!loading && !list.length" class="empty">
      {{ tab === 'following' ? '还没有关注任何人' : '还没有粉丝，多在广场发点东西吧' }}
    </p>

    <ul v-loading="loading" class="list">
      <li v-for="u in list" :key="u.userId" class="row" @click="goProfile(u)">
        <div class="row__avatar">
          <img v-if="u.avatarUrl" :src="u.avatarUrl" alt="" />
          <span v-else class="row__t">{{ initial(u.nickname) }}</span>
        </div>

        <div class="row__bd">
          <p class="row__top">
            <span class="row__nick">{{ u.nickname || '校园用户' }}</span>
            <span v-if="u.authStatus === 2" class="row__badge">已认证</span>
          </p>
          <p class="row__meta">
            <template v-if="u.creditScore != null">信用 {{ u.creditScore }}</template>
            <template v-if="u.followTime"> · {{ formatTime(u.followTime) }} {{ tab === 'fans' ? '关注了你' : '关注' }}</template>
          </p>
        </div>

        <button
          v-if="showAction(u)"
          type="button"
          class="row__btn"
          :class="{ 'row__btn--on': u.following }"
          :disabled="pending[u.userId]"
          @click.stop="toggle(u)"
        >
          {{ pending[u.userId] ? '处理中' : u.following ? '已关注' : tab === 'fans' ? '回关' : '关注' }}
        </button>
      </li>
    </ul>

    <el-pagination
      v-if="total > PAGE_SIZE"
      class="pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="PAGE_SIZE"
      :current-page="pageNum"
      @current-change="(p: number) => { pageNum = p; load() }"
    />
  </div>
</template>

<style scoped lang="scss">
.follows {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
  padding-bottom: var(--cm-space-8);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.head__title {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
}

.head__link {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  text-decoration: none;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 计数：两个并排的大数字，选中态用黄色下划线 ────────── */

.counts {
  display: flex;
  gap: var(--cm-space-8);
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
}

.counts__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 0 0 var(--cm-space-1);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;

  &.on {
    border-bottom-color: var(--cm-color-brand);
  }
}

.counts__num {
  font-size: 22px;
  font-weight: 800;
  color: var(--cm-color-text-1);
}

.counts__label {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 列表 ─────────────────────────────────────────────── */

.list {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
  min-height: 40px;
}

.row {
  display: flex;
  align-items: center;
  gap: var(--cm-space-4);
  padding: var(--cm-space-3) var(--cm-space-4);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
  cursor: pointer;
  transition: border-color 160ms ease-out, box-shadow 160ms ease-out;

  &:hover {
    border-color: var(--cm-color-brand-border);
    box-shadow: var(--cm-shadow-1);
  }
}

.row__avatar {
  width: 44px;
  height: 44px;
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

.row__t {
  font-size: 18px;
  font-weight: 700;
  color: var(--cm-color-text-1);
}

.row__bd {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.row__top {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  min-width: 0;
}

.row__nick {
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  color: var(--cm-color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row__badge {
  flex: none;
  padding: 1px 6px;
  font-size: 11px;
  color: var(--cm-color-ok);
  background: #f0faf4;
  border-radius: var(--cm-radius-sm);
}

.row__meta {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 未关注 = 黄底黑字（主色只落在这一处）；已关注降级成白底描边 */
.row__btn {
  flex: none;
  height: 30px;
  min-width: 72px;
  padding: 0 var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
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

    /* 悬停提示「这一点会取关」，用错误色而非价格色 */
    &:hover {
      color: #cf1322;
      border-color: #cf1322;
      background: var(--cm-color-surface);
    }
  }

  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
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
