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
import { computed, ref } from 'vue'
import { ITEM_STATUS_LABELS, formatPrice } from '@campus/shared'
import {
  OFFER_STATUS_LABELS,
  userChatApi,
  userItemApi,
  userOfferApi,
  userOrderApi,
  type ApiItemVO,
  type ChatMessageVO,
  type OfferCardPayload
} from '@/api/user'
import { getSession } from '@/utils/session'
import { userSocialApi } from '@/api/social'
import { confirmPay, clip } from '@/utils/confirm-pay'
import OfferSheet from '@/components/OfferSheet.vue'
import OfferRejectSheet from '@/components/OfferRejectSheet.vue'
import CmNav from '@/components/CmNav.vue'
import CmDialogHost from '@/components/CmDialogHost.vue'

const myId = getSession().userId
/** 自己的头像与昵称：会话里的右侧头像用它，没有头像图就退回昵称首字 */
const myAvatar = getSession().avatarUrl
const myNick = getSession().nickname

const itemId = ref('')
const peerId = ref('')
const peerNick = ref('')
/** 对方头像：会话列表跳转时带上；没有就退回昵称首字 */
const peerAvatar = ref('')

/** 商品详情：只为拿 sellerId 与 negotiable（判断「我能不能议价」），列表页带过来的卡片信息不完整 */
const item = ref<ApiItemVO | null>(null)
/** 议价操作进行中：防连点，同意与去付款都不能重复提交 */
const offerBusy = ref(false)
/** 出价面板 */
const offerOpen = ref(false)
/** 拒绝目标：非空即弹理由面板 */
const rejectTarget = ref<OfferCardPayload | null>(null)

/** 买家视角：卖家自己看自己的商品时没有议价入口 */
const isBuyer = computed(() => !!item.value && item.value.sellerId !== myId)

/** 议价入口条件：别人的、在售、卖家勾了可议价 */
const canOffer = computed(() => {
  const it = item.value
  return !!it && it.sellerId !== myId && it.status === 'ON_SALE' && it.negotiable === 1
})

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
    avatar?: string
    itemTitle?: string
    itemImage?: string
    itemPrice?: string
    itemStatus?: string
  }
  itemId.value = q.itemId ?? ''
  peerId.value = q.peerId ?? ''
  peerNick.value = q.nick ? decodeURIComponent(q.nick) : ''
  peerAvatar.value = q.avatar ? decodeURIComponent(q.avatar) : ''
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
  void loadItem()
  void loadPeer()
  // 拉完即已读
  void userChatApi.read(itemId.value, peerId.value).catch(() => undefined)
  timer = setInterval(pollNew, 5000)
})

/** 对方头像与昵称：从商品详情进来时不带头像，补一次公开主页，气泡左头像才不会全是首字 */
async function loadPeer(): Promise<void> {
  if (!peerId.value) return
  try {
    const p = await userSocialApi.profile(peerId.value)
    if (p?.avatarUrl) peerAvatar.value = p.avatarUrl
    if (!peerNick.value && p?.nickname) peerNick.value = p.nickname
  } catch {
    // 拿不到就用首字头像，不影响聊天
  }
}

/** 商品现状：议价入口与「是否已售」提示都靠它，失败不阻断聊天 */
async function loadItem(): Promise<void> {
  if (!itemId.value) return
  try {
    item.value = await userItemApi.detail(itemId.value)
  } catch {
    // 商品可能已删除：聊天继续，只是不显示议价入口
  }
}

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

/* ── 议价：卡片格式坏掉就当普通气泡，不让一条脏数据卡住整页 ── */

function payloadOf(m: ChatMessageVO): OfferCardPayload | null {
  if (m.type !== 7 || !m.extra) return null
  try {
    const p = JSON.parse(m.extra) as OfferCardPayload
    return p.offerId ? p : null
  } catch {
    return null
  }
}

/** 截止时刻取 HH:mm：10 分钟的窗口用倒计时反而更催人，这里只给一个可核对的时点 */
function deadlineText(p: OfferCardPayload): string {
  return p.expireTime ? p.expireTime.slice(11, 16) : ''
}

/* ── 气泡头像：微信式「左对方 / 右自己」，头像圆形固定 68rpx ── */

function avatarImg(m: ChatMessageVO): string {
  return m.mine ? myAvatar : peerAvatar.value
}

function avatarChar(m: ChatMessageVO): string {
  const n = m.mine ? myNick || '我' : peerNick.value || '对方'
  return n.slice(0, 1)
}

/** 底色按发送方固定：同一个人不要在不同消息里换颜色 */
function avatarClass(m: ChatMessageVO): string {
  const seed = m.mine ? 'me' : peerId.value || 'peer'
  let h = 0
  for (let i = 0; i < seed.length; i += 1) h = (h * 31 + seed.charCodeAt(i)) % 997
  return `row__av--${h % 6}`
}

/** 出价：区间校验在面板内，这里只挡「便宜到没法议价」的极端商品 */
function startOffer(): void {
  const it = item.value
  if (!it || offerBusy.value) return
  const price = Number(it.price)
  const floor = Math.max(0.01, Math.round(price * 0.1 * 100) / 100)
  if (price <= floor) {
    uni.showToast({ title: '价格过低，暂不支持议价', icon: 'none' })
    return
  }
  offerOpen.value = true
}

async function agree(p: OfferCardPayload): Promise<void> {
  if (offerBusy.value) return
  offerBusy.value = true
  try {
    await userOfferApi.agree(p.offerId)
    uni.showToast({ title: `已同意，等买家 10 分钟内付款`, icon: 'none' })
    await pollNew()
  } catch {
    // request 层已提示
  } finally {
    offerBusy.value = false
  }
}

/** 拒绝：弹理由面板，提交在面板内完成，回调里只刷新消息 */
function reject(p: OfferCardPayload): void {
  if (offerBusy.value) return
  rejectTarget.value = p
}

async function cancelOffer(p: OfferCardPayload): Promise<void> {
  if (offerBusy.value) return
  offerBusy.value = true
  try {
    await userOfferApi.cancel(p.offerId)
    uni.showToast({ title: '已撤回', icon: 'none' })
    await pollNew()
  } catch {
    // request 层已提示
  } finally {
    offerBusy.value = false
  }
}

/**
 * 去付款：下单（带 offerId）后立刻模拟支付。
 *
 * <p>两步连做而不是先下单再跳订单页付款：议价只有 10 分钟，让用户多跳一页只会增加超时概率。
 * 支付失败时订单已建（待付款），订单页仍可继续付，所以这里不额外回滚。
 */
async function goPay(p: OfferCardPayload): Promise<void> {
  if (offerBusy.value) return
  const ok = await confirmPay({
    title: '确认付款',
    lines: [
      { label: '商品', value: clip(item.value?.title ?? '本商品') },
      { label: '成交价', value: `¥${p.offerPrice}`, strong: true },
      { label: '原标价', value: `¥${p.originPrice}` }
    ],
    note: '付款后进入待面交；议价价只对这笔订单生效，不影响商品挂牌价。',
    confirmText: '确认支付'
  })
  if (!ok) return
  offerBusy.value = true
  try {
    const orderId = await userOrderApi.create({ itemId: p.itemId, offerId: p.offerId })
    await userOrderApi.pay(orderId)
    await pollNew()
    void uni.navigateTo({ url: `/pages/order/detail?id=${orderId}` })
  } catch {
    // request 层已提示（如「议价已过付款时限」）
  } finally {
    offerBusy.value = false
  }
}
</script>

<template>
  <view class="page">
    <!-- 标题就是聊天对象，省掉「询单」这种看不出跟谁说的文案 -->
    <CmNav :title="peerNick || '询单'" />

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

    <!-- 议价入口：只在别人的、在售的、卖家勾了「可议价」的商品上出现 -->
    <view v-if="canOffer" class="offer-entry">
      <text class="offer-entry__text">卖家支持议价，可以出个价试试</text>
      <button class="offer-entry__btn" :disabled="offerBusy" @click="startOffer">我要议价</button>
    </view>

    <scroll-view scroll-y class="msgs">
      <view v-if="!messages.length && !loadingMore" class="tip">
        还没有消息。可以聊商品情况，也可以直接出价议价。
      </view>
      <template v-for="m in messages" :key="m.id">
        <!-- 一条消息一行：头像在外侧，内容是气泡或议价卡；自己的靠右 -->
        <view
          class="row"
          :class="{ 'row--mine': m.mine, 'row--card': !!payloadOf(m) }"
        >
          <view class="row__av" :class="avatarClass(m)">
            <image v-if="avatarImg(m)" class="row__av-img" :src="avatarImg(m)" mode="aspectFill" />
            <text v-else class="row__av-t">{{ avatarChar(m) }}</text>
          </view>

          <view class="row__body">
            <!-- 议价卡片：状态由服务端按最新单据回填，历史消息也能反映当前结论 -->
            <view v-if="payloadOf(m)" class="offer-card">
              <view class="offer-card__head">
                <text class="offer-card__label">议价</text>
                <text class="offer-card__status">{{ OFFER_STATUS_LABELS[payloadOf(m)!.status] }}</text>
              </view>
              <view class="offer-card__body">
                <text class="offer-card__price">{{ formatPrice(Number(payloadOf(m)!.offerPrice)) }}</text>
                <text class="offer-card__origin">标价 {{ formatPrice(Number(payloadOf(m)!.originPrice)) }}</text>
              </view>
              <text
                v-if="payloadOf(m)!.status === 'AGREED' && payloadOf(m)!.expireTime"
                class="offer-card__hint"
              >请在 {{ deadlineText(payloadOf(m)!) }} 前付款，过时失效</text>
              <view class="offer-card__ops">
                <template v-if="!isBuyer && payloadOf(m)!.status === 'PENDING'">
                  <button class="offer-card__btn offer-card__btn--primary" :disabled="offerBusy" @click="agree(payloadOf(m)!)">同意</button>
                  <button class="offer-card__btn" :disabled="offerBusy" @click="reject(payloadOf(m)!)">拒绝</button>
                </template>
                <button
                  v-else-if="isBuyer && payloadOf(m)!.status === 'AGREED'"
                  class="offer-card__btn offer-card__btn--primary"
                  :disabled="offerBusy"
                  @click="goPay(payloadOf(m)!)"
                >去付款</button>
                <button
                  v-else-if="isBuyer && m.mine && payloadOf(m)!.status === 'PENDING'"
                  class="offer-card__btn"
                  :disabled="offerBusy"
                  @click="cancelOffer(payloadOf(m)!)"
                >撤回</button>
              </view>
            </view>
            <view v-else class="bubble">
              <text class="bubble__text">{{ m.content }}</text>
            </view>
          </view>
        </view>
      </template>
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
        placeholder="说点什么…"
        confirm-type="send"
        @confirm="send"
      />
      <button class="bar__send" :disabled="sending || !draft.trim()" @click="send">发送</button>
    </view>

    <OfferSheet
      v-model="offerOpen"
      :item-id="item?.id ?? itemId"
      :price="item?.price ?? '0'"
      @done="pollNew"
    />
    <OfferRejectSheet
      :model-value="!!rejectTarget"
      :offer-id="rejectTarget?.offerId ?? ''"
      :offer-price="rejectTarget?.offerPrice ?? '0'"
      @update:model-value="(v: boolean) => { if (!v) rejectTarget = null }"
      @done="pollNew"
    />

    <CmDialogHost />
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
  box-sizing: border-box;
}

/* ── 消息行：头像在外、内容在内，自己的整行反向 ─────────── */
.row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  margin-bottom: $cm-space-3;
}

.row--mine {
  flex-direction: row-reverse;
}

.row__av {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 4rpx;
}

.row__av-img {
  width: 68rpx;
  height: 68rpx;
  border-radius: 50%;
}

.row__av-t {
  font-size: 26rpx;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
}

.row__av--0 {
  background-image: linear-gradient(135deg, #FFB673, $cm-color-brand);
}

.row__av--1 {
  background-image: linear-gradient(135deg, #A98BFF, $cm-color-accent-violet);
}

.row__av--2 {
  background-image: linear-gradient(135deg, #4FD6A3, $cm-color-accent-teal);
}

.row__av--3 {
  background-image: linear-gradient(135deg, #6FB2FF, $cm-color-blue-500);
}

.row__av--4 {
  background-image: linear-gradient(135deg, #FFA6BE, $cm-color-brand-2);
}

.row__av--5 {
  background-image: linear-gradient(135deg, #FFB067, $cm-color-orange-500);
}

/* 内容宽度按内容走：短句两三个字就是一个小气泡，长句到 72% 折行 */
.row__body {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.row--mine .row__body {
  align-items: flex-end;
}

/* 议价卡片是固定版式，宽度不随文字变：给足 76% 才放得下价格与两个按钮 */
.row--card .row__body {
  width: 76%;
  max-width: 76%;
}

/* 议价入口：横向一条，不占太多高度，避免挤压消息区 */
.offer-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $cm-space-3;
  margin: $cm-space-2 $cm-space-4 0;
  padding: $cm-space-2 $cm-space-3;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
  border-radius: $cm-radius-lg;
}

.offer-entry__text {
  flex: 1;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

.offer-entry__btn {
  margin: 0;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 $cm-space-4;
  font-size: $cm-font-size-xs;
  color: #fff;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;

  &[disabled] {
    opacity: 0.5;
  }
}

/* 议价卡片：占满内容列宽度，靠头像所在侧表明是谁发的 */
.offer-card {
  width: 100%;
  box-sizing: border-box;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
  border-radius: $cm-radius-lg;
}

.offer-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.offer-card__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  letter-spacing: 1rpx;
}

.offer-card__status {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand;
}

.offer-card__body {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
  margin-top: $cm-space-1;
}

.offer-card__price {
  font-size: 40rpx;
  font-weight: 600;
  color: $cm-color-price;
}

.offer-card__origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
}

.offer-card__hint {
  display: block;
  margin-top: $cm-space-1;
  font-size: $cm-font-size-xs;
  color: $cm-color-warn;
}

.offer-card__ops {
  display: flex;
  gap: $cm-space-2;
  margin-top: $cm-space-3;
}

.offer-card__btn {
  flex: 1;
  margin: 0;
  height: 64rpx;
  line-height: 64rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;

  &--primary {
    color: #fff;
    background: $cm-color-brand;
  }

  &[disabled] {
    opacity: 0.5;
  }
}

.tip {
  padding: $cm-space-8 0;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

/* 气泡：宽度贴合文字，短句不占一整行；靠头像那一角收窄成小尖角 */
.bubble {
  max-width: 100%;
  box-sizing: border-box;
  padding: 18rpx 24rpx;
  border-radius: 24rpx 24rpx 24rpx 8rpx;
  background: $cm-color-surface;
  box-shadow: $cm-shadow-xs;
}

.row--mine .bubble {
  border-radius: 24rpx 24rpx 8rpx 24rpx;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: 0 8rpx 18rpx -12rpx rgba(255, 106, 0, 0.9);

  .bubble__text {
    color: #fff;
  }
}

.bubble__text {
  font-size: $cm-font-size-base;
  line-height: 1.55;
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
