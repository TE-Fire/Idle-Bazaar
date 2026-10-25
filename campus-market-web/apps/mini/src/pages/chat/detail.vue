<script setup lang="ts">
/**
 * M5 消息 · 询单对话（business/02 M-B4，2026-10-22 同步留言口径）
 *
 * <p>同步模式：进页面标已读 + 定时轮询拉新（无 WebSocket 推送）；离开即停。
 * 消息倒序拉取后反转成正序显示；「更早的消息」按钮用 beforeId 游标翻历史。
 * 图片消息与面交卡片不做——文本询价已覆盖核心场景。
 *
 * <p>顶部商品卡片：数据由两个入口跳转时带上（会话列表项 / 商品详情「聊一聊」），
 * 本页不拉商品接口；显隐是用户级偏好（本地存储，默认展示），非在售态置灰提示。
 */

import { onHide, onLoad, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ITEM_STATUS_LABELS, formatPrice } from '@campus/shared'
import { userChatApi, type ChatMessageVO } from '@/api/user'

const itemId = ref('')
const peerId = ref('')
const peerNick = ref('')

/** 会话窗顶部商品卡片：两个入口（会话列表 / 商品详情「聊一聊」）跳转时带上，不额外拉接口 */
const card = ref<{ title: string; image: string; price: string; status: string } | null>(null)
/** 卡片显隐是用户级偏好：本地存储，默认展示；关掉后本机所有会话都不再显示 */
const cardVisible = ref(true)
const CARD_HIDDEN_KEY = 'chat:item-card:hidden'

const messages = ref<ChatMessageVO[]>([])
const draft = ref('')
const sending = ref(false)
/** 最早一条 id：翻历史游标；null = 没有更多 */
const oldestId = ref<string | null>(null)
const loadingMore = ref(false)

let timer: number | undefined

onLoad((query) => {
  const q = query as {
    itemId?: string
    peerId?: string
    nick?: string
    itemTitle?: string
    itemImage?: string
    itemPrice?: string
    itemStatus?: string
  }
  itemId.value = q.itemId ?? ''
  peerId.value = q.peerId ?? ''
  peerNick.value = q.nick ? decodeURIComponent(q.nick) : ''
  uni.setNavigationBarTitle({ title: peerNick.value ? `与${peerNick.value}的询单` : '询单' })

  const title = q.itemTitle ? decodeURIComponent(q.itemTitle) : ''
  card.value = title
    ? {
        title,
        image: q.itemImage ? decodeURIComponent(q.itemImage) : '',
        price: q.itemPrice ?? '',
        status: q.itemStatus ?? ''
      }
    : null
  cardVisible.value = !uni.getStorageSync(CARD_HIDDEN_KEY)

  void pullMessages()
  // 拉完即已读
  void userChatApi.read(itemId.value, peerId.value).catch(() => undefined)
  timer = setInterval(pollNew, 5000)
})

/** 关闭卡片：偏好落本地，下次进任意会话都不再显示 */
function closeCard(): void {
  cardVisible.value = false
  uni.setStorageSync(CARD_HIDDEN_KEY, '1')
}

/** 点卡片回商品详情：面交前核对商品现状（价格 / 是否已售） */
function goItem(): void {
  if (itemId.value) {
    void uni.navigateTo({ url: `/pages/item/detail?id=${itemId.value}` })
  }
}

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
    <!-- 顶部商品卡片：买家从哪件商品发起询单，面交前可点回详情核对 -->
    <view v-if="card && cardVisible" class="card">
      <image v-if="card.image" class="card__img" :src="card.image" mode="aspectFill" />
      <view v-else class="card__img card__img--empty" />
      <view class="card__body" @click="goItem">
        <text class="card__title">{{ card.title }}</text>
        <view class="card__meta">
          <text
            v-if="card.price"
            class="card__price"
            :class="{ 'card__price--off': card.status && card.status !== 'ON_SALE' }"
          >{{ formatPrice(Number(card.price)) }}</text>
          <text v-if="card.status && card.status !== 'ON_SALE'" class="card__status">
            {{ ITEM_STATUS_LABELS[card.status as keyof typeof ITEM_STATUS_LABELS] }}
          </text>
        </view>
      </view>
      <text class="card__close" @click="closeCard">×</text>
    </view>

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

/* 顶部商品卡片：不随消息滚动，关闭后由用户级偏好控制不再出现 */
.card {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  margin: $cm-space-3 $cm-space-4 0;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.card__img {
  width: 96rpx;
  height: 96rpx;
  border-radius: $cm-radius-md;
  background: $cm-color-bg;
  flex-shrink: 0;

  &--empty {
    background: $cm-color-bg;
  }
}

.card__body {
  flex: 1;
  min-width: 0;
}

.card__title {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__meta {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  margin-top: $cm-space-1;
}

.card__price {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-price;

  /* 非在售态置灰：双方对一件已售 / 已下架商品继续聊没有意义 */
  &--off {
    color: $cm-color-text-3;
    text-decoration: line-through;
  }
}

.card__status {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  padding: 2rpx 10rpx;
  background: $cm-color-bg;
  border-radius: $cm-radius-sm;
}

.card__close {
  align-self: flex-start;
  padding: $cm-space-1 $cm-space-2;
  font-size: 36rpx;
  line-height: 1;
  color: $cm-color-text-3;
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
