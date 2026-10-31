<script setup lang="ts">
/**
 * 广场（notes/22 S3–S5）
 *
 * <p>两条流：广场（全站时间流）与关注（只看已关注作者）。默认广场——新用户没有关注关系，
 * 先落在关注流只会看到空白。切换时用请求序号丢弃过期响应，避免旧流的数据续到新流尾部。
 *
 * <p>发布走底部上滑面板（含文字 + 最多 9 图）：小程序没有对话框组件，
 * 且发布是本页唯一的重动作，独立页面反而多一次跳转与回填。
 *
 * <p>点赞只翻转本地状态、失败回滚；状态在这层统一改，卡片只发事件。
 */

import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import MomentCard from '@/components/MomentCard.vue'
import CmIcon from '@/components/CmIcon.vue'
import { momentApi, type MomentVO } from '@/api/moment'
import { uploadImage } from '@/utils/upload'
import { getSession } from '@/utils/session'

const PAGE = 10
const MAX_IMAGES = 9
const MAX_LEN = 500

type MomentScope = 'square' | 'following'

const scope = ref<MomentScope>('square')
const list = ref<MomentVO[]>([])
const pageNum = ref(1)
const total = ref(0)
const loading = ref(false)
/** 请求序号：切流后旧请求的返回直接丢弃 */
let reqSeq = 0
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
  if (loading.value && !reset) return
  if (!reset && finished.value) return
  const my = ++reqSeq
  loading.value = true
  const p = reset ? 1 : pageNum.value + 1
  const useScope = scope.value
  try {
    const res = useScope === 'following' ? await momentApi.following(p, PAGE) : await momentApi.square(p, PAGE)
    if (my !== reqSeq) return
    const rows = res.list ?? []
    list.value = reset ? rows : [...list.value, ...rows]
    pageNum.value = p
    total.value = rows.length < PAGE ? list.value.length : Number(res.total)
  } catch {
    if (my === reqSeq && reset) {
      list.value = []
      total.value = 0
    }
  } finally {
    if (my === reqSeq) loading.value = false
  }
}

function reload(): void {
  pageNum.value = 1
  total.value = 0
  void fetchPage(true)
}

/** 未登录提示并去登录页，登录后回落到广场；返回 true 表示已拦下 */
function needLogin(): boolean {
  if (getSession().tokenValue) return false
  // 不要自己 encodeURIComponent：navigateTo 会再编一次，redirect 变成 %252F 解码不回来
  void uni.navigateTo({ url: '/pages/login/index?redirect=/pages/moments/index' })
  return true
}

function switchScope(next: MomentScope): void {
  if (scope.value === next) return
  // 关注流是「我关注的人」，没登录无从谈起，先拦住再切
  if (next === 'following' && needLogin()) return
  scope.value = next
  list.value = []
  pageNum.value = 1
  total.value = 0
  loading.value = false
  void fetchPage(true)
}

/** 点赞 / 取消：乐观翻转，失败按原值回滚 */
async function toggleLike(m: MomentVO): Promise<void> {
  if (needLogin()) return
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
  if (needLogin()) return
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
  // 广场不拦登录：未登录照样能翻，点赞/发布/关注流再引导登录
  if (!getSession().tokenValue) scope.value = 'square'
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
  <view class="cm-page">
    <view class="inner">
      <!-- 头部：标题 + 发布入口。发布是这一页唯一的重动作，放在首屏可见处 -->
      <view class="hero">
        <view class="hero__glow" />
        <view class="hero__row">
          <view class="hero__main">
            <text class="hero__t">广场</text>
            <text class="hero__s">看看同学们在聊什么</text>
          </view>
          <view class="hero__pub" hover-class="hero__pub--on" @click="openComposer">
            <CmIcon name="plus" tone="white" size="sm" />
            <text>发动态</text>
          </view>
        </view>
      </view>

      <!-- 双流切换：选中块随切换平移，比换字色更能读出当前在哪条流 -->
      <view class="seg">
        <view class="seg__cur" :class="{ 'seg__cur--right': scope === 'following' }" />
        <view class="seg__i" :class="{ 'seg__i--on': scope === 'square' }" @click="switchScope('square')">
          <text>广场</text>
        </view>
        <view class="seg__i" :class="{ 'seg__i--on': scope === 'following' }" @click="switchScope('following')">
          <text>关注</text>
        </view>
      </view>

      <view v-if="!list.length && loading" class="feed">
        <view v-for="n in 3" :key="n" class="cm-skel feed-skel" />
      </view>
      <view v-else-if="!list.length" class="cm-state">
        <CmIcon name="heart" size="lg" tone="muted" />
        <text class="cm-state__t">{{ scope === 'following' ? '关注的人还没发动态' : '还没有人发动态' }}</text>
        <text class="cm-state__s">{{ scope === 'following' ? '去广场逛逛，关注几个同学' : '做第一个分享校园生活的人' }}</text>
      </view>

      <view v-else class="feed">
        <view
          v-for="(m, i) in list"
          :key="m.id"
          class="cm-in"
          :style="{ animationDelay: Math.min(i, 8) * 40 + 'ms' }"
        >
          <MomentCard :moment="m" @toggle-like="toggleLike" />
        </view>
      </view>

      <view v-if="list.length" class="loadmore">
        <view v-if="loading" class="loadmore__dots">
          <view class="loadmore__dot" />
          <view class="loadmore__dot" />
          <view class="loadmore__dot" />
        </view>
        <text v-else class="loadmore__t">{{ finished ? '没有更多了' : '上拉加载更多' }}</text>
      </view>
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
.inner {
  padding: 0 $cm-space-3 $cm-space-6;
}

/* ── 头部 ───────────────────────────────────────────────── */
.hero {
  position: relative;
  margin: 0 (-$cm-space-3);
  padding: $cm-space-4 $cm-space-4 $cm-space-5;
  background-image: linear-gradient(180deg, #FFEDDD 0%, $cm-color-bg 100%);
  overflow: hidden;
}

.hero__glow {
  position: absolute;
  right: -80rpx;
  top: -120rpx;
  width: 300rpx;
  height: 300rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 138, 61, 0.3), rgba(255, 138, 61, 0));
}

.hero__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
}

.hero__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.hero__t {
  font-size: $cm-font-size-2xl;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
  letter-spacing: 0.8rpx;
}

.hero__s {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

.hero__pub {
  flex: none;
  height: 68rpx;
  padding: 0 $cm-space-4 0 $cm-space-3;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out;
}

.hero__pub--on {
  transform: scale(0.95);
}

/* ── 双流切换 ───────────────────────────────────────────── */

.seg {
  position: relative;
  display: flex;
  margin: 0 $cm-space-2 $cm-space-3;
  padding: 6rpx;
  background: rgba(255, 255, 255, 0.72);
  border-radius: $cm-radius-pill;
  box-shadow: $cm-shadow-xs;
}

/* 选中块：宽 = 一半减去内边距，靠平移切换，不改宽高所以不触发布局 */
.seg__cur {
  position: absolute;
  top: 6rpx;
  bottom: 6rpx;
  left: 6rpx;
  width: calc(50% - 6rpx);
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  border-radius: $cm-radius-pill;
  box-shadow: 0 8rpx 20rpx rgba(255, 106, 0, 0.28);
  transition: transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.seg__cur--right {
  transform: translateX(100%);
}

.seg__i {
  position: relative;
  z-index: 1;
  flex: 1;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-sm;
  font-weight: 500;
  color: $cm-color-text-3;
  transition: color 200ms ease-out, font-weight 200ms ease-out;
}

.seg__i--on {
  color: #fff;
  font-weight: 700;
}

.feed {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.feed-skel {
  height: 460rpx;
}

.loadmore {
  padding: $cm-space-4 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loadmore__t {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.loadmore__dots {
  display: flex;
  gap: 8rpx;
}

.loadmore__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $cm-color-gray-400;
  animation: dotPulse 1s ease-in-out infinite;
}

.loadmore__dot:nth-child(2) {
  animation-delay: 0.15s;
}

.loadmore__dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes dotPulse {
  0%,
  100% {
    opacity: 0.3;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1.1);
  }
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
  padding: $cm-space-4 $cm-space-4 calc(#{$cm-space-6} + env(safe-area-inset-bottom));
  background: $cm-color-surface;
  border-radius: $cm-radius-3xl $cm-radius-3xl 0 0;
  box-shadow: 0 -8rpx 32rpx rgba(27, 27, 33, 0.12);
  transform: translateY(100%);
  pointer-events: none;
  transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
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
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

.sheet__close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  background: $cm-color-gray-100;
}

.sheet__input {
  width: 100%;
  height: 220rpx;
  padding: $cm-space-3;
  font-size: $cm-font-size-base;
  line-height: 1.6;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-2xl;
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
  min-width: 180rpx;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: $cm-font-weight-bold;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  border-radius: $cm-radius-pill;
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out, opacity 160ms ease-out;
}

.sheet__btn--off {
  opacity: 0.45;
  box-shadow: none;
}

.sheet__btn--active {
  transform: scale(0.96);
}
</style>
