<script setup lang="ts">
/**
 * 动态详情（notes/22 S6）
 *
 * <p>上半动态本体（MomentCard 传 inDetail，点内容不再跳自己），下半评论列表 + 底部输入条。
 * 评论不分页：后端契约就是一次性返回全部正序，单条动态几十条评论在小程序里够用。
 *
 * <p>回复：点某条评论的「回复」记下目标用户，提交时传其 userId（后端存的是用户 id，
 * 不是评论 id）。提交后整页重拉——正文经服务端敏感词替换，本地拼的那条会显示原文。
 *
 * <p>输入条固定在底部并用 `bottom: env(safe-area-inset-bottom)` 兜住刘海屏，
 * 列表底部留同高 padding，否则最后一条评论会被输入条压住。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { formatRelativeTime } from '@campus/shared'
import MomentCard from '@/components/MomentCard.vue'
import { momentApi, type MomentCommentVO, type MomentVO } from '@/api/moment'
import { REPORT_TARGET, reportTarget } from '@/utils/report'
import { getSession } from '@/utils/session'

const myId = getSession().userId

const momentId = ref('')
const moment = ref<MomentVO | null>(null)
const comments = ref<MomentCommentVO[]>([])
const loading = ref(false)
const missing = ref(false)

const draft = ref('')
const sending = ref(false)
const replyTarget = ref<{ userId: string; nick: string } | null>(null)
const likePend = ref('')

const replyHint = computed(() => (replyTarget.value ? `回复 @${replyTarget.value.nick}：` : '说点什么…'))

async function load(): Promise<void> {
  loading.value = true
  try {
    const d = await momentApi.detail(momentId.value)
    moment.value = d.moment
    comments.value = d.comments ?? []
    missing.value = false
  } catch {
    moment.value = null
    missing.value = true
  } finally {
    loading.value = false
  }
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

function setReply(c: MomentCommentVO): void {
  if (c.userId === getSession().userId) return
  replyTarget.value = { userId: c.userId, nick: c.author?.nickname || 'TA' }
}

function clearReply(): void {
  replyTarget.value = null
}

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || !moment.value || sending.value) return
  sending.value = true
  try {
    await momentApi.comment(moment.value.id, text, replyTarget.value?.userId)
    draft.value = ''
    replyTarget.value = null
    await load()
  } catch {
    // 请求层已提示（含敏感词拒绝）
  } finally {
    sending.value = false
  }
}

function goProfile(userId: string): void {
  void uni.navigateTo({ url: `/pages/user/index?userId=${userId}` })
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

onLoad((query) => {
  const q = query as { id?: string }
  momentId.value = q.id ?? ''
  if (!momentId.value) {
    missing.value = true
    return
  }
  void load()
})
</script>

<template>
  <view class="page">
    <template v-if="moment">
      <MomentCard :moment="moment" in-detail @toggle-like="toggleLike" />

      <view class="cmt">
        <view class="cmt__hd">
          <text class="cmt__title">评论{{ comments.length ? ' ' + comments.length : '' }}</text>
        </view>

        <view v-if="!comments.length" class="cmt__empty"><text class="cmt__empty-t">还没有评论，说第一句吧</text></view>

        <view v-for="c in comments" :key="c.id" class="row">
          <view class="row__ava" hover-class="row__ava--active" @click="goProfile(c.userId)">
            <image v-if="c.author?.avatarUrl" class="row__ava-img" :src="c.author.avatarUrl" mode="aspectFill" />
            <text v-else class="row__ava-t">{{ initial(c.author?.nickname) }}</text>
          </view>
          <view class="row__body">
            <view class="row__top">
              <text class="row__nick">{{ c.author?.nickname || '校园用户' }}</text>
              <text class="row__time">{{ formatRelativeTime(c.createTime) }}</text>
            </view>
            <text class="row__text">
              <text v-if="c.replyTo" class="row__reply">回复 @{{ c.replyToNick || 'TA' }}：</text>{{ c.content }}
            </text>
            <view class="row__acts">
              <text class="row__act" @click="setReply(c)">回复</text>
              <text
                v-if="c.userId !== myId"
                class="row__act"
                @click="reportTarget(REPORT_TARGET.MOMENT_COMMENT, c.id)"
              >
                举报
              </text>
            </view>
          </view>
        </view>
      </view>

      <!-- 底部输入条：固定，避开 home 指示条 -->
      <view class="bar">
        <view v-if="replyTarget" class="bar__to">
          <text class="bar__to-t">回复 @{{ replyTarget.nick }}</text>
          <text class="bar__cancel" @click="clearReply">取消</text>
        </view>
        <view class="bar__row">
          <input
            v-model="draft"
            class="bar__input"
            :maxlength="500"
            :placeholder="replyHint"
            placeholder-class="bar__ph"
            confirm-type="send"
            @confirm="send"
          />
          <view
            class="bar__send"
            :class="{ 'bar__send--off': !draft.trim() }"
            hover-class="bar__send--active"
            @click="send"
          >
            <text>{{ sending ? '发送中' : '发送' }}</text>
          </view>
        </view>
      </view>
    </template>

    <view v-else-if="loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="missing" class="state"><text class="state__t">这条动态不存在或已被删除</text></view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-3 $cm-space-3 220rpx;
  background: $cm-color-bg;
  box-sizing: border-box;
}

.cmt {
  margin-top: $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.cmt__hd {
  margin-bottom: $cm-space-3;
}

.cmt__title {
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
}

.cmt__empty {
  padding: $cm-space-4 0;
}

.cmt__empty-t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.row {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3 0;
  border-top: 1rpx solid $cm-color-border;
}

.row__ava {
  width: 60rpx;
  height: 60rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: $cm-color-brand-bg;
  border-radius: 50%;
}

.row__ava--active {
  opacity: 0.8;
}

.row__ava-img {
  width: 100%;
  height: 100%;
}

.row__ava-t {
  font-size: 24rpx;
  font-weight: 700;
  color: $cm-color-brand-text;
}

.row__body {
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
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-2;
}

.row__time {
  font-size: 20rpx;
  color: $cm-color-text-3;
}

.row__text {
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-1;
  word-break: break-all;
}

.row__reply {
  color: $cm-color-brand-text;
}

.row__acts {
  align-self: flex-start;
  display: flex;
  gap: $cm-space-3;
}

.row__act {
  font-size: 20rpx;
  color: $cm-color-text-3;
}

/* ── 底部输入条 ─────────────────────────────────────────── */

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: $cm-space-3 $cm-space-4;
  padding-bottom: calc(#{$cm-space-3} + env(safe-area-inset-bottom));
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
}

.bar__to {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  margin-bottom: 8rpx;
}

.bar__to-t {
  font-size: 20rpx;
  color: $cm-color-text-2;
}

.bar__cancel {
  font-size: 20rpx;
  color: $cm-color-brand-text;
}

.bar__row {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
}

.bar__input {
  flex: 1;
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: 36rpx;
}

.bar__ph {
  color: $cm-color-text-3;
}

.bar__send {
  flex: none;
  min-width: 120rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: 32rpx;
}

.bar__send--off {
  opacity: 0.5;
}

.bar__send--active {
  opacity: 0.85;
}

.state {
  padding: 200rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>
