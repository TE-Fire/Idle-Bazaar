<script setup lang="ts">
/**
 * 动态详情（notes/22 S6）
 *
 * <p>上半是动态本体（复用 MomentCard，`inDetail` 让卡片自身不再跳转），
 * 下半是评论列表 + 输入框。评论暂不分页——后端契约就是一次性返回全部正序，
 * 校园场景单条动态几十条评论已足够。
 *
 * <p>回复：点某条评论的「回复」把目标用户记在 `replyTarget`，提交时传其 userId。
 * 后端 `replyTo` 存的是**用户 id** 而不是评论 id，所以同一人被回复多次会聚成一条线，
 * 这是刻意的——比按评论树渲染简单，也符合「评论只有一层」的产品口径。
 *
 * <p>提交后整页重拉而不是本地 push：正文经过服务端敏感词替换，本地拼出来的
 * 那条会显示原文（含未替换的脏词），与服务端真实存档不一致。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatRelativeTime } from '@campus/shared'
import { userMomentApi, REPORT_TARGET, type MomentCommentVO, type MomentVO } from '@campus/api'
import MomentCard from '@/components/MomentCard.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

const momentId = computed(() => route.params.id as string)

const moment = ref<MomentVO | null>(null)
const comments = ref<MomentCommentVO[]>([])
const loading = ref(false)
const missing = ref(false)

const draft = ref('')
const sending = ref(false)
/** 回复目标：为空即直接评论动态 */
const replyTarget = ref<{ userId: string; nick: string } | null>(null)
/** 正在点赞的动态 id（详情只有一条，仍用 id 比较以复用同样逻辑） */
const likePending = ref<string | null>(null)

/** 举报弹窗：动态与动态评论共用一个弹层，类型随入口切换 */
const reportOpen = ref(false)
const reportType = ref<number>(REPORT_TARGET.MOMENT)
const reportId = ref('')

function openReport(type: number, id: string): void {
  reportType.value = type
  reportId.value = id
  reportOpen.value = true
}

function reportMoment(m: MomentVO): void {
  openReport(REPORT_TARGET.MOMENT, m.id)
}

const replyHint = computed(() => (replyTarget.value ? `回复 @${replyTarget.value.nick}：` : '说点什么…'))

async function load(): Promise<void> {
  loading.value = true
  try {
    const d = await userMomentApi.detail(momentId.value)
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
  if (!session.isLogin) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
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

function setReply(c: MomentCommentVO): void {
  if (c.userId === session.userId) return
  replyTarget.value = { userId: c.userId, nick: c.author?.nickname || 'TA' }
}

function clearReply(): void {
  replyTarget.value = null
}

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || !moment.value) return
  if (!session.isLogin) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  sending.value = true
  try {
    await userMomentApi.comment(moment.value.id, text, replyTarget.value?.userId)
    draft.value = ''
    replyTarget.value = null
    await load()
  } catch {
    // http 层已提示（含敏感词拒绝）
  } finally {
    sending.value = false
  }
}

function goProfile(userId: string): void {
  void router.push({ name: 'user-profile', params: { id: userId } })
}

function initial(nick?: string): string {
  return (nick || '?').slice(0, 1)
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="cm-shell detail">
    <button type="button" class="back" @click="router.back()">← 返回</button>

    <template v-if="moment">
      <MomentCard :moment="moment" in-detail @toggle-like="toggleLike" @comment="() => {}" @report="reportMoment" />

      <section class="cmt">
        <h2 class="cmt__title">评论 {{ comments.length ? `(${comments.length})` : '' }}</h2>

        <p v-if="!comments.length" class="cmt__empty">还没有评论，说第一句吧</p>

        <ul class="cmt__list">
          <li v-for="c in comments" :key="c.id" class="row">
            <button type="button" class="row__ava" @click="goProfile(c.userId)">
              <img v-if="c.author?.avatarUrl" :src="c.author.avatarUrl" alt="" />
              <span v-else>{{ initial(c.author?.nickname) }}</span>
            </button>
            <div class="row__body">
              <p class="row__top">
                <span class="row__nick">{{ c.author?.nickname || '校园用户' }}</span>
                <span class="row__time">{{ formatRelativeTime(c.createTime) }}</span>
              </p>
              <p class="row__text">
                <span v-if="c.replyTo" class="row__reply">回复 @{{ c.replyToNick || 'TA' }}：</span>{{ c.content }}
              </p>
              <div class="row__acts">
                <button type="button" class="row__act" @click="setReply(c)">回复</button>
                <button
                  v-if="c.userId !== session.userId"
                  type="button"
                  class="row__act"
                  @click="openReport(REPORT_TARGET.MOMENT_COMMENT, c.id)"
                >
                  举报
                </button>
              </div>
            </div>
          </li>
        </ul>
      </section>

      <!-- 输入区：回复态显示目标并把焦点留在输入框 -->
      <footer class="editor">
        <div v-if="replyTarget" class="editor__to">
          正在回复 @{{ replyTarget.nick }}
          <button type="button" class="editor__cancel" @click="clearReply">取消</button>
        </div>
        <div class="editor__row">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            :maxlength="500"
            resize="none"
            :placeholder="replyHint"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="send">发送</el-button>
        </div>
        <p class="editor__tip">Enter 发送，Shift + Enter 换行</p>
      </footer>
    </template>

    <div v-else-if="loading" class="state">加载中…</div>
    <div v-else-if="missing" class="state">这条动态不存在或已被删除</div>

    <ReportDialog v-model="reportOpen" :target-type="reportType" :target-id="reportId" />
  </div>
</template>

<style scoped lang="scss">
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  max-width: 760px;
  margin: 0 auto;
  padding-top: var(--cm-space-4);
  padding-bottom: var(--cm-space-8);
}

.back {
  align-self: flex-start;
  padding: 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 评论列表 ───────────────────────────────────────────── */

.cmt {
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

.cmt__title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-md);
  font-weight: 700;
  color: var(--cm-color-text-1);
}

.cmt__empty {
  margin: var(--cm-space-3) 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.cmt__list {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.row {
  display: flex;
  gap: var(--cm-space-3);
}

.row__ava {
  width: 32px;
  height: 32px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  font-size: 13px;
  font-weight: 700;
  color: var(--cm-color-brand-text);
  background: var(--cm-color-brand-bg);
  border: none;
  border-radius: 50%;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.row__body {
  flex: 1;
  min-width: 0;
}

.row__top {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
  margin: 0;
}

.row__nick {
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  color: var(--cm-color-text-2);
}

.row__time {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.row__text {
  margin: 4px 0 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

.row__reply {
  color: var(--cm-color-brand-text);
}

.row__acts {
  display: flex;
  gap: var(--cm-space-3);
  margin-top: 4px;
}

.row__act {
  padding: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-brand-dark);
  }
}

/* ── 输入区 ─────────────────────────────────────────────── */

.editor {
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

.editor__to {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
}

.editor__cancel {
  padding: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-brand-text);
  background: none;
  border: none;
  cursor: pointer;
}

.editor__row {
  display: flex;
  align-items: flex-end;
  gap: var(--cm-space-3);
}

.editor__tip {
  margin: var(--cm-space-2) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.state {
  padding: 80px 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}
</style>
