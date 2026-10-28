<script setup lang="ts">
/**
 * 广场（notes/22 S3–S5 的宿主页）
 *
 * <p>一条流、不分 tab：动态与「分享好物」混排，靠卡片右上的类型标区分。
 * 校园场景里动态量不大，分频道反而每条都稀。
 *
 * <p>加载用「哨兵 + IntersectionObserver」而不是滚动监听：滚动监听每帧算一次
 * getBoundingClientRect，长列表里是白白的开销；哨兵只在真正进出视口时触发一次。
 *
 * <p>点赞统一在这里改状态：卡片拿到的是同一批对象的引用，改一处列表即同步。
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userMomentApi, type MomentVO } from '@campus/api'
import MomentCard from '@/components/MomentCard.vue'
import MomentComposer from '@/components/MomentComposer.vue'
import { useUserStore } from '@/stores/user'

const PAGE_SIZE = 10

const router = useRouter()
const session = useUserStore()

const moments = ref<MomentVO[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
/** 正在请求的那条动态 id：防止连点重复请求 */
const likePending = ref<string | null>(null)

const composerOpen = ref(false)
const sentinel = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

const finished = computed(() => moments.value.length >= total.value)

async function load(reset = false): Promise<void> {
  if (loading.value) return
  if (!reset && finished.value) return
  loading.value = true
  const next = reset ? 1 : pageNum.value + 1
  try {
    const page = await userMomentApi.square(next, PAGE_SIZE)
    const rows = page.list ?? []
    moments.value = reset ? rows : [...moments.value, ...rows]
    pageNum.value = next
    total.value = Number(page.total)
    if (rows.length < PAGE_SIZE) total.value = moments.value.length
  } catch {
    if (reset) {
      moments.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function requireLogin(): boolean {
  if (session.isLogin) return true
  void router.push({ name: 'login', query: { redirect: '/moments' } })
  return false
}

function openComposer(): void {
  if (!requireLogin()) return
  composerOpen.value = true
}

/** 点赞 / 取消：乐观翻转，失败按服务端原值回滚 */
async function toggleLike(m: MomentVO): Promise<void> {
  if (!requireLogin() || likePending.value === m.id) return
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

function goDetail(m: MomentVO): void {
  void router.push({ name: 'moment-detail', params: { id: m.id } })
}

function onPublished(): void {
  void load(true)
}

onMounted(() => {
  void load(true)
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting) void load(false)
    },
    { rootMargin: '240px' }
  )
  if (sentinel.value) observer.observe(sentinel.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div class="cm-shell plaza">
    <header class="hd">
      <div class="hd__txt">
        <h1 class="hd__title">广场</h1>
        <p class="hd__sub">看看同校同学在聊什么、晒什么</p>
      </div>
      <el-button type="primary" round @click="openComposer">＋ 发条动态</el-button>
    </header>

    <p v-if="!loading && !moments.length" class="empty">
      还没有人发动态，你可以做第一个
    </p>

    <div class="feed">
      <MomentCard
        v-for="m in moments"
        :key="m.id"
        :moment="m"
        @toggle-like="toggleLike"
        @comment="goDetail"
      />
    </div>

    <!-- 哨兵：进入视口即拉下一页 -->
    <div ref="sentinel" class="sentinel">
      <span v-if="loading">加载中…</span>
      <span v-else-if="moments.length && finished">没有更多了</span>
    </div>

    <MomentComposer v-model="composerOpen" @published="onPublished" />
  </div>
</template>

<style scoped lang="scss">
.plaza {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  /* 单列窄栏：社交流读的是文字，铺满 1200px 会一行扫不完 */
  max-width: 760px;
  margin: 0 auto;
  padding-top: var(--cm-space-5);
  padding-bottom: var(--cm-space-8);
}

.hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

.hd__title {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--cm-color-text-1);
}

.hd__sub {
  margin: 4px 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 与页头同宽，不再单独限宽 */
.feed {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  width: 100%;
}

.empty {
  margin: var(--cm-space-8) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.sentinel {
  padding: var(--cm-space-4) 0;
  text-align: center;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
