<script setup lang="ts">
/**
 * 广场（notes/22 S3–S5）
 *
 * <p>一条流不分频道：校园场景动态量不大，再分 tab 只会每条都稀。
 * 「分享好物」靠卡片右上类型标区分，混排即可。
 *
 * <p>发布走底部上滑面板（含文字 + 最多 9 图）：小程序没有对话框组件，
 * 且发布是本页唯一的重动作，独立页面反而多一次跳转与回填。
 *
 * <p>点赞只翻转本地状态、失败回滚；状态在这层统一改，卡片只发事件。
 */

import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import MomentCard from '@/components/MomentCard.vue'
import { momentApi, type MomentVO } from '@/api/moment'
import { uploadImage } from '@/utils/upload'
import { getSession } from '@/utils/session'

const PAGE = 10
const MAX_IMAGES = 9
const MAX_LEN = 500

const list = ref<MomentVO[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
const likePend = ref('')

const finished = computed(() => list.value.length >= total.value)

/** 发布面板 */
const composerOpen = ref(false)
const draft = ref('')
const shots = ref<{ id: number; url: string; uploading: boolean }[]>([])
const submitting = ref(false)
let seq = 0

const canSubmit = computed(
  () => !submitting.value && !shots.value.some((s) => s.uploading) && (!!draft.value.trim() || shots.value.some((s) => s.url))
)

async function fetchPage(reset: boolean): Promise<void> {
  if (loading.value) return
  if (!reset && finished.value) return
  loading.value = true
  const p = reset ? 1 : pageNum.value + 1
  try {
    const res = await momentApi.square(p, PAGE)
    const rows = res.list ?? []
    list.value = reset ? rows : [...list.value, ...rows]
    pageNum.value = p
    total.value = rows.length < PAGE ? list.value.length : Number(res.total)
  } catch {
    if (reset) {
      list.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function reload(): void {
  pageNum.value = 1
  total.value = 0
  void fetchPage(true)
}

/** 点赞 / 取消：乐观翻转，失败按原值回滚 */
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

/* ── 发布面板 ─────────────────────────────────────────── */

function openComposer(): void {
  composerOpen.value = true
}

function closeComposer(): void {
  composerOpen.value = false
  draft.value = ''
  shots.value = []
  submitting.value = false
}

function pickImages(): void {
  if (shots.value.length >= MAX_IMAGES) {
    uni.showToast({ title: `最多 ${MAX_IMAGES} 张图`, icon: 'none' })
    return
  }
  uni.chooseImage({
    count: MAX_IMAGES - shots.value.length,
    sizeType: ['compressed'],
    success: (res) => {
      const paths = (res.tempFilePaths ?? []) as string[]
      const files = (res.tempFiles ?? []) as { path: string; size: number }[]
      paths.forEach((path, i) => {
        const shot = { id: ++seq, url: '', uploading: true }
        shots.value = [...shots.value, shot]
        const size = files[i]?.size ?? 0
        void uploadImage(path, size)
          .then((url) => {
            const hit = shots.value.find((s) => s.id === shot.id)
            if (hit) {
              hit.url = url
              hit.uploading = false
            }
          })
          .catch((err: unknown) => {
            shots.value = shots.value.filter((s) => s.id !== shot.id)
            uni.showToast({ title: err instanceof Error ? err.message : '上传失败', icon: 'none' })
          })
      })
    }
  })
}

function removeShot(id: number): void {
  shots.value = shots.value.filter((s) => s.id !== id)
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    await momentApi.publish({
      type: 1,
      content: draft.value.trim() || undefined,
      images: shots.value.map((s) => s.url).filter(Boolean)
    })
    uni.showToast({ title: '已发布', icon: 'success' })
    closeComposer()
    reload()
  } catch {
    // 请求层已提示（含敏感词 4024），保留草稿让用户改
  } finally {
    submitting.value = false
  }
}

onShow(() => {
  if (!getSession().tokenValue) {
    void uni.navigateTo({ url: '/pages/login/index?redirect=moments' })
    return
  }
  reload()
})

onPullDownRefresh(() => {
  reload()
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  void fetchPage(false)
})
</script>

<template>
  <view class="page">
    <view v-if="!list.length && loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="!list.length" class="state"><text class="state__t">还没有人发动态，做第一个吧</text></view>

    <view class="feed">
      <MomentCard v-for="m in list" :key="m.id" :moment="m" @toggle-like="toggleLike" />
    </view>

    <view v-if="list.length" class="loadmore">
      <text>{{ loading ? '加载中…' : finished ? '没有更多了' : '上拉加载更多' }}</text>
    </view>

    <!-- 发布浮动按钮：抬到 tabBar 之上 -->
    <view class="fab" hover-class="fab--active" @click="openComposer">
      <text class="fab__plus">＋</text>
    </view>

    <!-- 发布面板：常驻 DOM 只切 class，否则首次渲染就是终态、过渡放不出来 -->
    <view class="mask" :class="{ 'mask--on': composerOpen }" @click="closeComposer" />
    <view class="sheet" :class="{ 'sheet--on': composerOpen }">
      <view class="sheet__hd">
        <text class="sheet__title">发条动态</text>
        <text class="sheet__close" @click="closeComposer">✕</text>
      </view>

      <textarea
        v-model="draft"
        class="sheet__input"
        :maxlength="MAX_LEN"
        placeholder="分享你的校园生活、闲置心得…"
        placeholder-class="sheet__ph"
      />

      <view class="shots">
        <view v-for="s in shots" :key="s.id" class="shot">
          <image v-if="s.url" class="shot__img" :src="s.url" mode="aspectFill" />
          <view v-else class="shot__waiting"><text class="shot__waiting-t">上传中…</text></view>
          <text class="shot__del" @click="removeShot(s.id)">×</text>
        </view>
        <view v-if="shots.length < MAX_IMAGES" class="shot shot--add" hover-class="shot--active" @click="pickImages">
          <text class="shot__plus">＋</text>
          <text class="shot__tip">{{ shots.length }}/{{ MAX_IMAGES }}</text>
        </view>
      </view>

      <view class="sheet__ft">
        <text class="sheet__tips">违规内容会被过滤或拒收</text>
        <view class="sheet__btn" :class="{ 'sheet__btn--off': !canSubmit }" hover-class="sheet__btn--active" @click="submit">
          <text>{{ submitting ? '发布中…' : '发布' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-3 $cm-space-3 160rpx;
  background: $cm-color-bg;
  box-sizing: border-box;
}

.feed {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.state {
  padding: 200rpx $cm-space-6;
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

/* ── 发布 FAB ───────────────────────────────────────────── */

.fab {
  position: fixed;
  right: $cm-space-4;
  bottom: 40rpx;
  width: 104rpx;
  height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $cm-color-brand;
  border-radius: 50%;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.16);
  z-index: 20;
}

.fab--active {
  transform: scale(0.94);
}

.fab__plus {
  font-size: 48rpx;
  color: $cm-color-text-1;
  line-height: 1;
}

/* ── 发布面板 ───────────────────────────────────────────── */

.mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.24s ease;
  z-index: 30;
}

.mask--on {
  opacity: 1;
  pointer-events: auto;
}

.sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 31;
  padding: $cm-space-4 $cm-space-4 $cm-space-6;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg $cm-radius-lg 0 0;
  transform: translateY(100%);
  pointer-events: none;
  transition: transform 0.24s ease;
}

.sheet--on {
  transform: translateY(0);
  pointer-events: auto;
}

.sheet__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $cm-space-3;
}

.sheet__title {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-text-1;
}

.sheet__close {
  padding: 0 $cm-space-2;
  font-size: $cm-font-size-md;
  color: $cm-color-text-3;
}

.sheet__input {
  width: 100%;
  height: 200rpx;
  padding: $cm-space-3;
  font-size: $cm-font-size-base;
  line-height: 1.6;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
  box-sizing: border-box;
}

.sheet__ph {
  color: $cm-color-text-3;
}

.shots {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
  margin-top: $cm-space-3;
}

.shot {
  position: relative;
  width: 136rpx;
  height: 136rpx;
  overflow: hidden;
  background: $cm-color-bg;
  border-radius: $cm-radius-sm;
}

.shot__img {
  width: 100%;
  height: 100%;
}

.shot__waiting {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.shot__waiting-t {
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.shot__del {
  position: absolute;
  top: 0;
  right: 0;
  width: 32rpx;
  height: 32rpx;
  line-height: 30rpx;
  text-align: center;
  font-size: 22rpx;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 0 0 0 8rpx;
}

.shot--add {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  border: 1rpx dashed $cm-color-border;
  box-sizing: border-box;
}

.shot--active {
  opacity: 0.7;
}

.shot__plus {
  font-size: 34rpx;
  color: $cm-color-text-3;
  line-height: 1;
}

.shot__tip {
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.sheet__ft {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $cm-space-4;
}

.sheet__tips {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.sheet__btn {
  min-width: 160rpx;
  height: 68rpx;
  line-height: 68rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: 34rpx;
}

.sheet__btn--off {
  opacity: 0.5;
}

.sheet__btn--active {
  opacity: 0.85;
}
</style>
