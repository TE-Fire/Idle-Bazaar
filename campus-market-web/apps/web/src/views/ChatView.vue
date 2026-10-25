<script setup lang="ts">
/**
 * P6 消息 · 询单会话（business/01 W-B5，2026-10-22 同步留言口径）
 *
 * <p>同步模式：无 WebSocket 推送，进会话后 5 秒轮询拉新、拉取即已读
 * （服务端 read 端点）。会话按「商品 + 对方」定位，买家与卖家共用本页。
 * 图片消息与面交卡片不做——文本询价已覆盖「聊聊」的核心场景。
 */

import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { chatApi, type ChatConversationVO, type ChatMessageVO } from '@campus/api'
import { formatPrice, formatRelativeTime, ITEM_STATUS_LABELS } from '@campus/shared'

const route = useRoute()
const router = useRouter()

const conversations = ref<ChatConversationVO[]>([])
const loadingList = ref(true)

const selected = ref<ChatConversationVO | null>(null)
const messages = ref<ChatMessageVO[]>([])
const draft = ref('')
const sending = ref(false)
const loadingMsg = ref(false)
/** 最早一条 id：翻历史游标；null = 没有更多 */
const oldestId = ref<string | null>(null)

/** 顶部商品卡片显隐：用户级偏好存本地，默认展示（与小程序同 key 同口径） */
const CARD_HIDDEN_KEY = 'chat:item-card:hidden'
const cardHidden = ref(localStorage.getItem(CARD_HIDDEN_KEY) === '1')

let timer: number | undefined

onMounted(async () => {
  await loadConversations()
  // 从详情页「聊一聊」进来：query 带商品摘要，选中对应会话（可能还没有）
  const itemId = route.query.itemId as string | undefined
  const peerId = route.query.peerId as string | undefined
  if (itemId && peerId) {
    const hit = conversations.value.find((c) => c.itemId === itemId && c.peerUserId === peerId)
    if (hit) {
      await openConversation(hit)
    } else {
      // 没有会话记录也进得去：首条消息发出时才建会话，这里给个空对话壳；
      // 商品摘要取自 query（详情页带上来的），顶部卡片才能显示
      selected.value = {
        itemId,
        peerUserId: peerId,
        itemTitle: (route.query.itemTitle as string | undefined) || '新的询单',
        itemMainImage: (route.query.itemImage as string | undefined) || undefined,
        price: (route.query.itemPrice as string | undefined) || undefined,
        status: (route.query.itemStatus as ChatConversationVO['status']) || undefined,
        peerNick: '',
        lastContent: '',
        unread: 0
      }
      messages.value = []
      oldestId.value = null
    }
  } else if (conversations.value.length) {
    await openConversation(conversations.value[0])
  }
  timer = window.setInterval(pollNew, 5000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})

/** 关闭卡片：偏好落本地，之后本机所有会话都不再显示 */
function closeCard(): void {
  cardHidden.value = true
  localStorage.setItem(CARD_HIDDEN_KEY, '1')
}

/** 点卡片回商品详情：面交前核对商品现状（价格 / 是否已售） */
function goItem(): void {
  if (selected.value) {
    void router.push({ name: 'item', params: { id: selected.value.itemId } })
  }
}

async function loadConversations(): Promise<void> {
  try {
    conversations.value = await chatApi.conversations()
  } catch {
    // http 层已提示
  } finally {
    loadingList.value = false
  }
}

async function openConversation(c: ChatConversationVO): Promise<void> {
  selected.value = c
  messages.value = []
  oldestId.value = null
  await pullMessages()
  // 拉完即已读；未读数本地先清零，避免轮询间隙还挂着红点
  c.unread = 0
  void chatApi.read(c.itemId, c.peerUserId).catch(() => undefined)
}

async function pullMessages(): Promise<void> {
  const c = selected.value
  if (!c) return
  loadingMsg.value = true
  try {
    const list = await chatApi.messages(c.itemId, c.peerUserId, oldestId.value ?? undefined)
    if (list.length) {
      // 服务端倒序给，这里拼成时间正序；翻历史时把更早的插到前面
      const asc = [...list].reverse()
      messages.value = oldestId.value ? [...asc, ...messages.value] : asc
      oldestId.value = list[list.length - 1].id
      if (list.length < 30) oldestId.value = null
    } else {
      oldestId.value = null
    }
  } catch {
    // http 层已提示
  } finally {
    loadingMsg.value = false
  }
}

/** 轮询：只拉最新一页，把新消息追加到尾部 */
async function pollNew(): Promise<void> {
  const c = selected.value
  if (!c) return
  try {
    const list = await chatApi.messages(c.itemId, c.peerUserId)
    const asc = [...list].reverse()
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = asc.filter((m) => !known.has(m.id))
    if (fresh.length) {
      messages.value = [...messages.value, ...fresh]
      c.lastContent = fresh[fresh.length - 1].content
      // 正在看这个会话：拉取即已读
      void chatApi.read(c.itemId, c.peerUserId).catch(() => undefined)
    }
  } catch {
    // 轮询失败静默：下一轮再来，不弹窗打断输入
  }
}

async function send(): Promise<void> {
  const c = selected.value
  const text = draft.value.trim()
  if (!c || !text || sending.value) return
  sending.value = true
  try {
    await chatApi.send({ itemId: c.itemId, toUserId: c.peerUserId, content: text })
    draft.value = ''
    await pollNew()
    // 会话列表里这一行的最近消息随之更新
    c.lastContent = text
  } catch {
    // http 层已提示
  } finally {
    sending.value = false
  }
}

/** 自己商品的会话排在前面：卖家视角要最快看到新询单 */
const sortedConversations = computed(() => conversations.value)
</script>

<template>
  <div class="cm-shell chat">
    <!-- 左：会话列表 -->
    <aside class="chat__list">
      <h2 class="chat__title">消息</h2>
      <p v-if="loadingList" class="chat__empty">加载中…</p>
      <p v-else-if="!conversations.length" class="chat__empty">
        还没有会话。去商品详情页点「聊一聊」开始询价。
      </p>
      <button
        v-for="c in sortedConversations"
        :key="`${c.itemId}-${c.peerUserId}`"
        type="button"
        class="conv"
        :class="{ on: selected?.itemId === c.itemId && selected?.peerUserId === c.peerUserId }"
        @click="openConversation(c)"
      >
        <span class="conv__peer">{{ c.peerNick || '对方' }}</span>
        <span v-if="c.unread" class="conv__dot">{{ c.unread }}</span>
        <span class="conv__last">{{ c.lastContent || '（暂无消息）' }}</span>
        <span class="conv__item">问：{{ c.itemTitle || '商品' }}</span>
        <span class="conv__time">{{ c.lastAt ? formatRelativeTime(c.lastAt) : '' }}</span>
      </button>
    </aside>

    <!-- 右：对话 -->
    <section v-if="selected" class="chat__main">
      <header class="chat__head">
        <strong>{{ selected.peerNick || '对方' }}</strong>
        <span class="chat__head-sub">询单：{{ selected.itemTitle || '商品' }}</span>
      </header>

      <!-- 顶部商品卡片：买家从哪件商品发起询单；非在售态置灰，可点回详情核对 -->
      <div v-if="!cardHidden && selected.itemTitle" class="itemcard">
        <img v-if="selected.itemMainImage" class="itemcard__img" :src="selected.itemMainImage" alt="" />
        <div v-else class="itemcard__img itemcard__img--empty" />
        <button type="button" class="itemcard__body" @click="goItem">
          <span class="itemcard__title">{{ selected.itemTitle }}</span>
          <span class="itemcard__meta">
            <span
              v-if="selected.price"
              class="itemcard__price"
              :class="{ 'itemcard__price--off': selected.status && selected.status !== 'ON_SALE' }"
            >{{ formatPrice(Number(selected.price)) }}</span>
            <span v-if="selected.status && selected.status !== 'ON_SALE'" class="itemcard__status">
              {{ ITEM_STATUS_LABELS[selected.status] }}
            </span>
          </span>
        </button>
        <button type="button" class="itemcard__close" title="关闭商品卡片" @click="closeCard">×</button>
      </div>

      <div class="chat__msgs">
        <p v-if="!messages.length && !loadingMsg" class="chat__empty">
          还没有消息。议价请在这里口头沟通，订单一律按标价成交。
        </p>
        <div
          v-for="m in messages"
          :key="m.id"
          class="bubble"
          :class="{ 'bubble--mine': m.mine }"
        >
          <span class="bubble__text">{{ m.content }}</span>
          <span class="bubble__time">{{ formatRelativeTime(m.createdAt) }}</span>
        </div>
      </div>

      <footer class="chat__input">
        <button v-if="oldestId" type="button" class="chat__more" :disabled="loadingMsg" @click="pullMessages">
          {{ loadingMsg ? '加载中…' : '加载更早的消息' }}
        </button>
        <div class="chat__row">
          <input
            v-model="draft"
            type="text"
            maxlength="500"
            placeholder="说点什么…（议价口头沟通，订单按标价成交）"
            @keyup.enter="send"
          />
          <button type="button" class="chat__send" :disabled="sending || !draft.trim()" @click="send">
            发送
          </button>
        </div>
      </footer>
    </section>

    <section v-else class="chat__main chat__main--empty">
      <p class="chat__empty">选择左侧会话开始沟通</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.chat {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--cm-space-4);
  align-items: start;
  padding-top: var(--cm-space-4);
}

.chat__title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.chat__list {
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
  padding: var(--cm-space-4);
  min-height: 480px;
}

.chat__empty {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  padding: var(--cm-space-4) 0;
}

.conv {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px var(--cm-space-2);
  width: 100%;
  text-align: left;
  padding: var(--cm-space-3);
  border: none;
  border-radius: var(--cm-radius-lg);
  background: transparent;
  cursor: pointer;

  &:hover {
    background: var(--cm-color-bg);
  }

  &.on {
    background: var(--cm-color-bg);
  }
}

.conv__peer {
  font-weight: 600;
  font-size: var(--cm-font-size-sm);
}

.conv__dot {
  min-width: 18px;
  height: 18px;
  line-height: 18px;
  text-align: center;
  font-size: 11px;
  color: #fff;
  background: var(--cm-color-err);
  border-radius: 9px;
  padding: 0 5px;
}

.conv__last {
  grid-column: 1 / -1;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv__item {
  grid-column: 1;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv__time {
  grid-column: 2;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 对话区 ─────────────────────────────────────────────── */

.chat__main {
  display: flex;
  flex-direction: column;
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
  min-height: 480px;

  &--empty {
    align-items: center;
    justify-content: center;
  }
}

.chat__head {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-3);
  padding: var(--cm-space-4) var(--cm-space-5);
  border-bottom: 1px solid var(--cm-color-border);
}

.chat__head-sub {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 顶部商品卡片 ─────────────────────────────────────── */

.itemcard {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  margin: var(--cm-space-3) var(--cm-space-5) 0;
  padding: var(--cm-space-3);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-lg);
}

.itemcard__img {
  width: 56px;
  height: 56px;
  border-radius: var(--cm-radius-md);
  object-fit: cover;
  flex-shrink: 0;

  &--empty {
    background: var(--cm-color-border);
  }
}

.itemcard__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
  padding: 0;
}

.itemcard__title {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.itemcard__meta {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.itemcard__price {
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  color: var(--cm-color-price);

  /* 非在售态置灰：双方对一件已售 / 已下架商品继续聊没有意义 */
  &--off {
    color: var(--cm-color-text-3);
    text-decoration: line-through;
  }
}

.itemcard__status {
  font-size: 11px;
  color: var(--cm-color-text-3);
  padding: 1px 8px;
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-sm);
}

.itemcard__close {
  align-self: flex-start;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: var(--cm-color-text-3);
  padding: var(--cm-space-1);

  &:hover {
    color: var(--cm-color-text-1);
  }
}

.chat__msgs {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  padding: var(--cm-space-5);
  overflow-y: auto;
  max-height: 420px;
}

.bubble {
  max-width: 70%;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--cm-space-2) var(--cm-space-3);
  border-radius: var(--cm-radius-lg);
  background: var(--cm-color-bg);
  align-self: flex-start;

  &--mine {
    align-self: flex-end;
    background: var(--cm-color-brand);
    color: #fff;
  }
}

.bubble__text {
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  white-space: pre-line;
}

.bubble__time {
  font-size: 11px;
  opacity: 0.6;
}

.chat__input {
  border-top: 1px solid var(--cm-color-border);
  padding: var(--cm-space-3) var(--cm-space-5) var(--cm-space-4);
}

.chat__more {
  border: none;
  background: none;
  color: var(--cm-color-brand);
  font-size: var(--cm-font-size-xs);
  cursor: pointer;
  padding: 0 0 var(--cm-space-2);
}

.chat__row {
  display: flex;
  gap: var(--cm-space-3);

  input {
    flex: 1;
    height: 38px;
    padding: 0 var(--cm-space-3);
    border: 1px solid var(--cm-color-border);
    border-radius: var(--cm-radius-lg);
    font-size: var(--cm-font-size-sm);
    background: var(--cm-color-bg);
    color: var(--cm-color-text-1);
  }
}

.chat__send {
  height: 38px;
  padding: 0 var(--cm-space-6);
  border: none;
  border-radius: var(--cm-radius-lg);
  background: var(--cm-color-brand);
  color: #fff;
  font-size: var(--cm-font-size-sm);
  cursor: pointer;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

@media (max-width: 1023px) {
  .chat {
    grid-template-columns: 1fr;
  }
}
</style>
