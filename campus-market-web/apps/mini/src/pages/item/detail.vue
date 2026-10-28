<script setup lang="ts">
/**
 * M2 商品详情（design/02 §六）
 *
 * <p>「半熟人信任可见」（原则 4）：学号认证、同校、面交点三项必须一眼看到——
 * 所以这三样放在参数区的最前面，不折叠。
 *
 * <p>数据接 `/api/item/{id}`（免登录）。底部「聊一聊」进询单会话（同步留言）；
 * 「我想要」下单（未登录先跳登录）：只插待付款订单，商品不锁定，金额按标价。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { CONDITION_LABELS, formatPrice, formatRelativeTime } from '@campus/shared'
import { userItemApi, userOrderApi, type ApiItemVO } from '@/api/user'
import { itemCommentApi, momentApi, type ItemCommentVO } from '@/api/moment'
import { REPORT_TARGET, reportTarget } from '@/utils/report'
import { getSession } from '@/utils/session'
import ConditionTag from '@/components/ConditionTag.vue'

const myId = getSession().userId

const id = ref('')
const item = ref<ApiItemVO | null>(null)
/** 分享到广场在途：防止连点重复发动态 */
const sharing = ref(false)

/** 商品评论（notes/22 S7）：仅 ON_SALE 可发，下架后仍可读历史 */
const comments = ref<ItemCommentVO[]>([])
const commentTotal = ref('0')
const draft = ref('')
const sending = ref(false)
const replyTarget = ref<{ userId: string; nick: string } | null>(null)
const canComment = computed(() => item.value?.status === 'ON_SALE')
const replyHint = computed(() =>
  canComment.value
    ? replyTarget.value
      ? `回复 @${replyTarget.value.nick}`
      : '问问成色、配件或交易方式'
    : '商品已下架，暂不可评论'
)

onLoad((query) => {
  id.value = (query as { id?: string })?.id ?? ''
  void userItemApi
    .detail(id.value)
    .then((vo) => {
      item.value = vo
      loadComments()
    })
    .catch(() => {
      // 不存在 / 已下架：request 层已提示，落空态
    })
})

/** 图集：有真实图片用图片，否则一张渐变占位 */
const shots = computed(() => {
  const imgs = item.value?.images ?? []
  return imgs.length ? imgs.map((i) => i.url) : [null]
})

function shotGradient(): string {
  return 'linear-gradient(135deg, #ECEFF3, #D3D9E0)'
}

/** 进询单会话：把手里的商品摘要带上，会话窗顶部卡片直接用（见 chat/detail onLoad） */
function onChat(): void {
  if (!item.value) return
  const it = item.value
  const q = [
    `itemId=${it.id}`,
    `peerId=${it.sellerId}`,
    `nick=${encodeURIComponent(it.sellerNick ?? '')}`,
    `itemTitle=${encodeURIComponent(it.title)}`,
    `itemImage=${it.mainImage ? encodeURIComponent(it.mainImage) : ''}`,
    `itemPrice=${it.price ?? ''}`,
    `itemStatus=${it.status ?? ''}`
  ].join('&')
  void uni.navigateTo({ url: `/pages/chat/detail?${q}` })
}

/** 我想要：未登录先跳登录（下单要令牌），已登录直接创建待付款订单进详情 */
function onBuy(): void {
  if (!item.value) return
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (item.value.sellerId === getSession().userId) {
    uni.showToast({ title: '不能购买自己发布的商品', icon: 'none' })
    return
  }
  uni.showLoading({ title: '下单中…', mask: true })
  userOrderApi
    .create({ itemId: item.value.id, meetPointId: item.value.meetPointId ?? undefined })
    .then((orderId) => {
      uni.hideLoading()
      uni.showToast({ title: '已创建订单', icon: 'success' })
      void uni.redirectTo({ url: `/pages/order/detail?id=${orderId}&role=buyer` })
    })
    .catch(() => uni.hideLoading())
}

/** 卖家卡 → TA 的个人主页 */
function goSeller(): void {
  if (!item.value) return
  void uni.navigateTo({ url: `/pages/user/index?userId=${item.value.sellerId}` })
}

/**
 * 分享到广场（动态 type=2）。
 *
 * <p>正文给一句默认推荐语，不让用户先写再发——分享动作要一步完成才有转化。
 * 商品必须仍在售，非在售服务端返回 4023，这里由请求层提示。
 */
async function onShare(): Promise<void> {
  if (!item.value || sharing.value) return
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  sharing.value = true
  uni.showLoading({ title: '分享中…', mask: true })
  try {
    await momentApi.publish({
      type: 2,
      content: `推荐一件好物：${item.value.title}`,
      itemId: item.value.id
    })
    uni.hideLoading()
    uni.showToast({ title: '已分享到广场', icon: 'success' })
  } catch {
    uni.hideLoading()
  } finally {
    sharing.value = false
  }
}

/** 生成分享海报：关键字段随 query 带到海报页，省一次详情请求 */
function onPoster(): void {
  if (!item.value) return
  const it = item.value
  const meta = [
    it.grade ? `${it.grade} 级` : '',
    it.campusName ?? '',
    it.meetPointName ?? ''
  ]
    .filter(Boolean)
    .join(' · ')
  const q = [
    `title=${encodeURIComponent(it.title)}`,
    `price=${encodeURIComponent(formatPrice(Number(it.price)))}`,
    `meta=${encodeURIComponent(meta)}`,
    `image=${it.mainImage ? encodeURIComponent(it.mainImage) : ''}`
  ].join('&')
  void uni.navigateTo({ url: `/pages/poster/index?${q}` })
}

/** 评论头像 → TA 的主页 */function goProfile(userId: string): void {
  void uni.navigateTo({ url: `/pages/user/index?userId=${userId}` })
}

/** 拉评论：一次 50 条，商品评论量小不分页 */
function loadComments(): void {
  if (!id.value) return
  itemCommentApi
    .list(id.value)
    .then((p) => {
      comments.value = p.list
      commentTotal.value = p.total
    })
    .catch(() => undefined)
}

/** 头像占位：取昵称首字 */
function initial(nick?: string): string {
  return (nick ?? '同').slice(0, 1)
}

function needLogin(): boolean {
  if (getSession().tokenValue) return false
  uni.showToast({ title: '请先登录', icon: 'none' })
  void uni.navigateTo({ url: '/pages/login/index' })
  return true
}

function setReply(c: ItemCommentVO): void {
  if (needLogin()) return
  replyTarget.value = { userId: c.userId, nick: c.author?.nickname || 'TA' }
}

function clearReply(): void {
  replyTarget.value = null
}

/** 发评论：正文由服务端过敏感词（低危词变 *），发完重拉而不是本地插 */
async function send(): Promise<void> {
  if (!item.value || sending.value) return
  const text = draft.value.trim()
  if (!text) return
  if (needLogin()) return
  sending.value = true
  try {
    await itemCommentApi.publish(item.value.id, text, replyTarget.value?.userId)
    draft.value = ''
    replyTarget.value = null
    uni.showToast({ title: '评论已发布', icon: 'success' })
    loadComments()
  } catch {
    // 请求层已提示（4024 敏感词 / 4025 商品不在售）
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <view v-if="item" class="page">
    <swiper class="swiper" indicator-dots circular>
      <swiper-item v-for="(s, i) in shots" :key="i">
        <image v-if="s" :src="s" mode="aspectFill" class="shot__img" />
        <view v-else class="shot" :style="{ backgroundImage: shotGradient() }" />
      </swiper-item>
    </swiper>

    <view class="box">
      <text class="price">{{ formatPrice(Number(item.price)) }}</text>
      <text v-if="item.originPrice" class="origin">原价 {{ formatPrice(Number(item.originPrice)) }}</text>
      <text class="title">{{ item.title }}</text>
      <text class="want">浏览 {{ item.viewCount }} 次 · {{ formatRelativeTime(item.publishAt) }}</text>
    </view>

    <!-- 校园四项：成色 / 校区 / 面交点 / 认证，前三项必须一眼可见 -->
    <view class="box">
      <view class="row">
        <text class="row__k">成色</text>
        <ConditionTag :grade="item.grade as 'A'" />
        <text class="row__v">{{ CONDITION_LABELS[item.grade as 'A'] }}</text>
      </view>
      <view class="row"><text class="row__k">校区</text><text class="row__v">{{ item.campusName }}</text></view>
      <view class="row"><text class="row__k">面交点</text><text class="row__v">{{ item.meetPointName ?? '待定' }}</text></view>
      <view class="row">
        <text class="row__k">交易</text>
        <text class="row__v">{{ item.negotiable ? '可议价' : '一口价' }} · {{ item.deliveryIncluded ? '包配送' : '自提' }}</text>
      </view>
    </view>

    <view class="box seller" hover-class="seller--active" @click="goSeller">
      <view class="avatar" :style="{ backgroundImage: shotGradient() }" />
      <view class="seller__meta">
        <text class="seller__nick">{{ item.sellerNick ?? '卖家' }}</text>
        <text class="seller__sub">信用 {{ item.sellerCredit ?? 100 }} 分 · 进主页</text>
      </view>
      <text class="seller__arrow">›</text>
    </view>

    <view class="box">
      <text class="desc__t">商品描述</text>
      <text class="desc">{{ item.description || '卖家没有填写描述。' }}</text>
      <view class="share-row" hover-class="share-row--active" @click="onShare">
        <text class="share-row__t">{{ sharing ? '分享中…' : '分享到广场，让更多同学看到' }}</text>
        <text class="share-row__arrow">›</text>
      </view>
      <view class="share-row" hover-class="share-row--active" @click="onPoster">
        <text class="share-row__t">生成分享海报</text>
        <text class="share-row__arrow">›</text>
      </view>
    </view>

    <!-- 商品评论（notes/22 S7）：售前问答，扁平一层 -->
    <view class="box">
      <view class="cmt__hd">
        <text class="desc__t">商品评论</text>
        <text class="cmt__n">{{ commentTotal }} 条</text>
      </view>

      <text v-if="!comments.length" class="cmt__empty">还没有人评论，来问两句</text>

      <view v-for="c in comments" :key="c.id" class="row">
        <view class="row__ava" @click="goProfile(c.userId)">
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
              @click="reportTarget(REPORT_TARGET.ITEM_COMMENT, c.id)"
            >
              举报
            </text>
          </view>
        </view>
      </view>

      <view class="cmt__editor">
        <view v-if="replyTarget" class="cmt__to">
          <text class="cmt__to-t">回复 @{{ replyTarget.nick }}</text>
          <text class="cmt__cancel" @click="clearReply">取消</text>
        </view>
        <view class="cmt__row">
          <input
            v-model="draft"
            class="cmt__input"
            :maxlength="500"
            :disabled="!canComment"
            :placeholder="replyHint"
            placeholder-class="cmt__ph"
            confirm-type="send"
            @confirm="send"
          />
          <view
            class="cmt__send"
            :class="{ 'cmt__send--off': !canComment || !draft.trim() || sending }"
            @click="send"
          >
            <text>{{ sending ? '发送中' : '发送' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部固定操作条：50pt + 安全区，主行动占 60% -->
    <view class="actionbar">
      <view class="actionbar__minor" @click="onChat">聊一聊</view>
      <view class="actionbar__main" hover-class="actionbar__main--active" @click="onBuy">我想要</view>
    </view>
  </view>

  <view v-else class="empty">
    <text>商品不存在或已下架</text>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
}

/* 图集通铺到屏幕边（闲鱼口径）：卡片从图下面才开始 */
.swiper {
  height: 640rpx;
}

.shot {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}

.shot__img {
  width: 100%;
  height: 100%;
}

.box {
  margin: $cm-space-3 $cm-space-4;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.price {
  font-size: 56rpx;
  font-weight: 700;
  line-height: 1;
  color: $cm-color-price;
}

.origin {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-decoration: line-through;
}

.title {
  font-size: $cm-font-size-lg;
  font-weight: 700;
  line-height: 1.4;
  color: $cm-color-text-1;
}

.want {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.row {
  display: flex;
  align-items: center;
  gap: $cm-space-4;
  font-size: $cm-font-size-sm;
  min-height: 56rpx;
}

.row__k {
  width: 112rpx;
  color: $cm-color-text-3;
  flex: none;
}

.row__v {
  color: $cm-color-text-1;
}

.seller {
  flex-direction: row;
  align-items: center;
}

.seller--active {
  transform: scale(0.99);
}

.seller__arrow {
  margin-left: auto;
  font-size: $cm-font-size-lg;
  color: $cm-color-text-3;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background-size: cover;
}

.seller__meta {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  margin-left: $cm-space-4;
}

.seller__nick {
  font-size: $cm-font-size-base;
  font-weight: 600;
}

.seller__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.desc__t {
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
}

.desc {
  font-size: $cm-font-size-sm;
  line-height: 1.7;
  color: $cm-color-text-2;
}

/* 分享到广场：描述卡内的次级动作行，不抢底部主行动 */
.share-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $cm-space-2;
  padding: $cm-space-3;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.share-row--active {
  opacity: 0.8;
}

.share-row__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-brand-text;
}

.share-row__arrow {
  font-size: $cm-font-size-md;
  color: $cm-color-text-3;
}

/* ── 商品评论 ── */
.cmt__hd {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
}

.cmt__n {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.cmt__empty {
  padding: $cm-space-3 0;
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
  font-weight: 700;
  color: $cm-color-text-1;
}

.row__time {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.row__text {
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-2;
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
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.cmt__editor {
  padding-top: $cm-space-3;
  border-top: 1rpx solid $cm-color-border;
}

.cmt__to {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $cm-space-2;
}

.cmt__to-t {
  font-size: $cm-font-size-xs;
  color: $cm-color-brand-text;
}

.cmt__cancel {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.cmt__row {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.cmt__input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.cmt__ph {
  color: $cm-color-text-3;
}

.cmt__send {
  flex: none;
  height: 72rpx;
  padding: 0 $cm-space-4;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-sm;
  font-weight: 700;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
}

.cmt__send--off {
  opacity: 0.45;
}

.actionbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: calc(100rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding-left: $cm-space-3;
  padding-right: $cm-space-3;
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
}

.actionbar__minor {
  width: 40%;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
  border: 1rpx solid $cm-color-border-strong;
  border-radius: $cm-radius-md;
}

.actionbar__main {
  flex: 1;
  height: 76rpx;
  line-height: 76rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: #fff;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
  transition: transform 120ms ease-out;
}

.actionbar__main--active {
  transform: scale(0.97);
}

.empty {
  padding: 200rpx 0;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>
