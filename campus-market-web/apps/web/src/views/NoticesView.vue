<script setup lang="ts">
/**
 * 系统通知（notes/22 批次 3、design/01 §4.8）：平台写给个人的信。
 *
 * <p>两层结构——列表是信封、点开是信笺。列表只给两行摘要：通知正文上限 2000 字，
 * 全摊在列表里会把十条通知变成两千行，扫描成本远高于它带来的便利。
 *
 * <p>已读不自动发生。原「进页面即全部已读」的口径作废：用户还没看，红点就没了。
 * 现在两条路——点开某封标该封，右上「全部已读」标全部；红点只认未读数，归零即消失。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { formatRelativeTime } from '@campus/shared'
import { userNoticeApi, type NoticeVO } from '@campus/api'
import { useNoticeStore } from '@/stores/notice'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()
const notice = useNoticeStore()

const list = ref<NoticeVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20
const loading = ref(false)

/** 当前展开的那封；null = 关着 */
const current = ref<NoticeVO | null>(null)
const open = ref(false)

const finished = computed(() => list.value.length >= total.value)

/** 称谓：昵称太长会把「亲爱的 ……：」挤出两行，截断即可，信里不需要全名 */
const salute = computed(() => {
  const nick = (session.nickname || '').trim()
  return nick ? nick.slice(0, 10) : '同学'
})

async function load(reset = false): Promise<void> {
  if (loading.value) return
  if (!session.isLogin) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  loading.value = true
  try {
    if (reset) pageNum.value = 1
    const p = await userNoticeApi.list(pageNum.value, pageSize)
    list.value = reset ? p.list : [...list.value, ...p.list]
    total.value = Number(p.total)
  } catch {
    // http 层已提示
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load(true)
  // 直接进本页时 store 还是空的（布局不一定先挂过），这里补一次
  void notice.refresh()
})

function more(): void {
  pageNum.value += 1
  void load()
}

/** 点开一封：先本地置已读让红点当场落下，再报服务端 */
function openLetter(n: NoticeVO): void {
  current.value = n
  open.value = true
  if (n.readFlag !== 0) return
  n.readFlag = 1
  notice.consumeOne()
  void userNoticeApi.readOne(n.id).catch(() => {
    // 失败不回滚本地态：下次 refresh 会按服务端真相拉回来，弹回去反而吓人
  })
}

async function readAll(): Promise<void> {
  try {
    await userNoticeApi.readAll()
    list.value = list.value.map((n) => ({ ...n, readFlag: 1 }))
    notice.consumeAll()
    ElMessage.success('已全部标为已读')
  } catch {
    // http 层已提示
  }
}

const TYPE_TEXT: Record<number, string> = { 4: '系统', 5: '互动', 6: '公告' }

function typeText(type: number): string {
  return TYPE_TEXT[type] ?? '系统'
}

const pad = (n: number): string => String(n).padStart(2, '0')

/** 信笺落款日期用中文，列表里的时间仍走相对时间（design/01 §七） */
function letterDate(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
}

function letterClock(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return `${letterDate(iso)} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div class="cm-shell notice">
    <header class="notice__hd">
      <h1>系统通知</h1>
      <span v-if="notice.unread > 0" class="notice__n">{{ notice.unread }} 条未读</span>
      <span v-else class="notice__n">全部已读</span>
      <button v-if="notice.unread > 0" class="notice__read" type="button" @click="readAll">
        全部已读
      </button>
    </header>

    <p v-if="loading && !list.length" class="notice__state">加载中…</p>

    <div v-else-if="!list.length" class="notice__empty">
      <span class="notice__empty-icon" aria-hidden="true" />
      <p class="notice__empty-t">信箱是空的</p>
      <p class="notice__empty-s">平台有新的处置结果或公告时，会送到这里</p>
    </div>

    <ul v-else class="notice__list">
      <li
        v-for="n in list"
        :key="n.id"
        class="env"
        :class="[`env--t${n.type}`, { 'env--new': n.readFlag === 0 }]"
        role="button"
        tabindex="0"
        @click="openLetter(n)"
        @keydown.enter="openLetter(n)"
        @keydown.space.prevent="openLetter(n)"
      >
        <div class="env__body">
          <div class="env__top">
            <span class="env__tag">{{ typeText(n.type) }}</span>
            <span class="env__time">{{ formatRelativeTime(n.createTime) }}</span>
            <span v-if="n.readFlag === 0" class="env__dot" aria-label="未读" />
          </div>
          <p v-if="n.title" class="env__title">{{ n.title }}</p>
          <p class="env__text">{{ n.content }}</p>
        </div>
      </li>
    </ul>

    <div v-if="list.length && !finished" class="notice__more">
      <el-button :loading="loading" @click="more">加载更多</el-button>
    </div>

    <el-dialog
      v-model="open"
      width="560px"
      :show-close="false"
      append-to-body
      class="letter-dialog"
      @closed="current = null"
    >
      <div v-if="current" class="letter">
        <button class="letter__close" type="button" aria-label="关闭" @click="open = false">×</button>

        <p class="letter__head">校 园 二 手 · 系 统 通 知</p>

        <div class="letter__meta">
          <span>{{ typeText(current.type) }}</span>
          <span>{{ letterClock(current.createTime) }}</span>
        </div>

        <h2 v-if="current.title" class="letter__title">{{ current.title }}</h2>

        <p class="letter__salut">亲爱的 {{ salute }}：</p>

        <div class="letter__body">{{ current.content }}</div>

        <div class="letter__foot">
          <span class="letter__seal" aria-hidden="true">校园<br />二手</span>
          <div class="letter__sign">
            <p class="letter__sign-line">校园二手 · 平台运营</p>
            <p class="letter__sign-line">{{ letterDate(current.createTime) }}</p>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.notice {
  max-width: 760px;
  padding-top: var(--cm-space-5);
  padding-bottom: var(--cm-space-8);
}

.notice__hd {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-4);
}

.notice__hd h1 {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.notice__n {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 「全部已读」是次要动作：文字按钮，不进按钮组抢视线（design/01 原则 1） */
.notice__read {
  margin-left: auto;
  padding: 2px 10px;
  border: 1px solid var(--cm-color-border-strong);
  border-radius: var(--cm-radius-pill);
  background: transparent;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.notice__read:hover {
  color: var(--cm-color-text-1);
  background: var(--cm-color-surface);
}

.notice__state,
.notice__empty {
  margin: var(--cm-space-8) 0;
  text-align: center;
}

.notice__state {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* 空状态：页面留白本来就大，往上再垫一档，重心才不至于贴着头 */
.notice__empty {
  padding-top: var(--cm-space-10);
}

.notice__empty-icon {
  display: inline-block;
  width: 48px;
  height: 34px;
  border: 1.5px solid var(--cm-color-border-strong);
  border-radius: 4px;
  position: relative;
}

/* 信封的三角封舌：只用两条边线，不填充，保持线描的统一 */
.notice__empty-icon::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 26px;
  height: 26px;
  border-right: 1.5px solid var(--cm-color-border-strong);
  border-bottom: 1.5px solid var(--cm-color-border-strong);
  transform: translate(-50%, -6px) rotate(45deg);
  background: transparent;
}

.notice__empty-t {
  margin: var(--cm-space-3) 0 4px;
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-2);
}

.notice__empty-s {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.notice__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

/* ── 信封卡片 ───────────────────────────────────────────── */

.env {
  position: relative;
  padding: var(--cm-space-4) var(--cm-space-5) var(--cm-space-4) 56px;
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-xl);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.env:hover {
  background: var(--cm-color-gray-100);
}

/* 左侧色块＝信封的封舌侧边；靠类名分色，图标不再单独上色 */
.env::before {
  content: '';
  position: absolute;
  left: 20px;
  top: var(--cm-space-4);
  width: 20px;
  height: 20px;
  border-radius: 4px;
  background: var(--cm-color-border-strong);
  box-shadow: none;
}

.env--t6::before {
  background: var(--cm-color-brand);
  box-shadow: none;
}

.env--t5::before {
  background: var(--cm-color-brand-bg);
  box-shadow: inset 0 0 0 1px var(--cm-color-brand-border);
}

.env__body {
  min-width: 0;
}

.env__top {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin-bottom: 6px;
}

.env__tag {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.env__time {
  margin-left: auto;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* 未读点：只有未读才有，已读不留空心占位 */
.env__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cm-color-err);
}

.env__title {
  margin: 0 0 2px;
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  line-height: 1.45;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

/* 摘要两行：列表只负责"这是哪封信"，读全文交给信笺 */
.env__text {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  color: var(--cm-color-text-2);
  word-break: break-word;
}

.env--new .env__title,
.env--new .env__text {
  color: var(--cm-color-text-1);
}

.env--new .env__tag {
  color: var(--cm-color-text-2);
}

.notice__more {
  margin-top: var(--cm-space-4);
  text-align: center;
}

/* ── 信笺 ──────────────────────────────────────────────── */

.letter {
  position: relative;
  padding: 40px 40px 32px;
  background: var(--cm-color-brand-bg);
  border-radius: var(--cm-radius-lg);
}

.letter__close {
  position: absolute;
  top: 8px;
  right: 10px;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  font-size: 18px;
  line-height: 1;
  color: var(--cm-color-text-2);
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.letter__close:hover {
  background: var(--cm-color-brand-border);
  color: var(--cm-color-text-1);
}

/* 抬头：字距拉开才有"公文/信纸"的仪式感，居中压一条通栏细线 */
.letter__head {
  margin: 0 0 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cm-color-brand-border);
  text-align: center;
  font-size: var(--cm-font-size-xs);
  letter-spacing: 0.3em;
  color: var(--cm-color-text-2);
}

.letter__meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 18px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.letter__title {
  margin: 0 0 10px;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  line-height: 1.45;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

.letter__salut {
  margin: 0 0 10px;
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

/* 纸面阅读比正文松：行高 1.9，首行缩进两字，保留原有换行 */
.letter__body {
  font-size: var(--cm-font-size-base);
  line-height: 1.9;
  color: var(--cm-color-text-1);
  white-space: pre-wrap;
  text-indent: 2em;
  word-break: break-word;
}

/* 落款与印章同处右下：印章在名字左侧，像真实信件的签章关系，
   别把印章甩到左下角——那样它就和落款脱了钩，只剩一个装饰。 */
.letter__foot {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  gap: var(--cm-space-4);
  margin-top: 28px;
}

.letter__sign {
  text-align: right;
}

.letter__sign-line {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.8;
  color: var(--cm-color-text-2);
}

/* 盖章：品牌黄描边双圈；全站唯一的一处"印章" */
.letter__seal {
  flex: none;
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cm-color-brand-dark);
  border-radius: 50%;
  box-shadow: inset 0 0 0 2px var(--cm-color-brand-bg),
    inset 0 0 0 3px var(--cm-color-brand-dark);
  font-size: 12px;
  line-height: 1.15;
  letter-spacing: 0.06em;
  text-align: center;
  color: var(--cm-color-brand-dark);
  opacity: 0.85;
  transform: rotate(-8deg);
  user-select: none;
}

/* EP 弹层自带 header 与 body padding，信纸要贴边；弹层是先 teleport 再挂 class，
   scoped 的 data-v 到不了，所以这几条放在非 scoped 块里（见文件末尾） */
</style>

<style lang="scss">
.letter-dialog .el-dialog__header {
  display: none;
}

.letter-dialog .el-dialog__body {
  padding: 0;
}

.letter-dialog.el-dialog {
  max-width: calc(100vw - 32px);
  background: transparent;
  box-shadow: none;
}
</style>
