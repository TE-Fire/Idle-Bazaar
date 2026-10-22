<script setup lang="ts">
/**
 * M5 消息 · 询单对话（business/02 M-B4，2026-10-22 同步留言口径）
 *
 * <p>同步模式：进页面标已读 + 定时轮询拉新（无 WebSocket 推送）；离开即停。
 * 消息倒序拉取后反转成正序显示；「更早的消息」按钮用 beforeId 游标翻历史。
 * 图片消息与面交卡片不做——文本询价已覆盖核心场景。
 */

import { onHide, onLoad, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { userChatApi, type ChatMessageVO } from '@/api/user'

const itemId = ref('')
const peerId = ref('')
const peerNick = ref('')

const messages = ref<ChatMessageVO[]>([])
const draft = ref('')
const sending = ref(false)
/** 最早一条 id：翻历史游标；null = 没有更多 */
const oldestId = ref<string | null>(null)
const loadingMore = ref(false)

let timer: number | undefined

onLoad((query) => {
  const q = query as { itemId?: string; peerId?: string; nick?: string }
  itemId.value = q.itemId ?? ''
  peerId.value = q.peerId ?? ''
  peerNick.value = q.nick ? decodeURIComponent(q.nick) : ''
  uni.setNavigationBarTitle({ title: peerNick.value ? `与${peerNick.value}的询单` : '询单' })

  void pullMessages()
  // 拉完即已读
  void userChatApi.read(itemId.value, peerId.value).catch(() => undefined)
  timer = setInterval(pollNew, 5000)
})

onHide(() => {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
})

onUnload(() => {
  if (timer) clearInterval(timer)
})

async function pullMessages(): Promise<void> {
  loadingMore.value = true
  try {
    const list = await userChatApi.messages(
      itemId.value,
      peerId.value,
      oldestId.value ?? undefined
    )
    if (list.length) {
      const asc = [...list].reverse()
      messages.value = oldestId.value ? [...asc, ...messages.value] : asc
      oldestId.value = list[list.length - 1].id
      if (list.length < 30) oldestId.value = null
    } else {
      oldestId.value = null
    }
  } catch {
    // request 层已提示
  } finally {
    loadingMore.value = false
  }
}

/** 轮询：只拉最新一页，新消息追加到尾部；正在看会话，顺手标已读 */
async function pollNew(): Promise<void> {
  try {
    const list = await userChatApi.messages(itemId.value, peerId.value)
    const asc = [...list].reverse()
    const known = new Set(messages.value.map((m) => m.id))
    const fresh = asc.filter((m) => !known.has(m.id))
    if (fresh.length) {
      messages.value = [...messages.value, ...fresh]
      void userChatApi.read(itemId.value, peerId.value).catch(() => undefined)
    }
  } catch {
    // 轮询失败静默：下一轮再来
  }
}

async function send(): Promise<void> {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  try {
    await userChatApi.send(itemId.value, peerId.value, text)
    draft.value = ''
    await pollNew()
  } catch {
    // request 层已提示
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <view class="page">
    <scroll-view scroll-y class="msgs">
      <view v-if="!messages.length && !loadingMore" class="tip">
        还没有消息。议价请在这里口头沟通，订单一律按标价成交。
      </view>
      <view
        v-for="m in messages"
        :key="m.id"
        class="bubble"
        :class="{ 'bubble--mine': m.mine }"
      >
        <text class="bubble__text">{{ m.content }}</text>
      </view>
      <view v-if="oldestId" class="more">
        <text class="more__btn" @click="pullMessages">
          {{ loadingMore ? '加载中…' : '更早的消息' }}
        </text>
      </view>
    </scroll-view>

    <view class="bar">
      <input
        v-model="draft"
        class="bar__input"
        type="text"
        maxlength="500"
        placeholder="说点什么…（议价口头沟通，订单按标价成交）"
        confirm-type="send"
        @confirm="send"
      />
      <button class="bar__send" :disabled="sending || !draft.trim()" @click="send">发送</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: $cm-color-bg;
}

.msgs {
  flex: 1;
  padding: $cm-space-4;
}

.tip {
  padding: $cm-space-8 0;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.bubble {
  max-width: 70%;
  margin-bottom: $cm-space-3;
  padding: $cm-space-2 $cm-space-3;
  border-radius: $cm-radius-lg;
  background: $cm-color-surface;

  &--mine {
    margin-left: auto;
    background: $cm-color-brand;

    .bubble__text {
      color: #fff;
    }
  }
}

.bubble__text {
  font-size: $cm-font-size-base;
  line-height: 1.6;
  color: $cm-color-text-1;
  word-break: break-all;
}

.more {
  text-align: center;
  padding: $cm-space-2 0;
}

.more__btn {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand;
}

.bar {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  padding: $cm-space-2 $cm-space-3;
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
  padding-bottom: calc($cm-space-2 + env(safe-area-inset-bottom));
}

.bar__input {
  flex: 1;
  height: 72rpx;
  padding: 0 $cm-space-3;
  border-radius: $cm-radius-lg;
  background: $cm-color-bg;
  font-size: $cm-font-size-base;
}

.bar__send {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $cm-space-5;
  font-size: $cm-font-size-base;
  color: #fff;
  background: $cm-color-brand;
  border-radius: $cm-radius-lg;

  &[disabled] {
    opacity: 0.5;
  }
}
</style>
