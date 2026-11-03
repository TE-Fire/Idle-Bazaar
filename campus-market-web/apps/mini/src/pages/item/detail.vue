<script setup lang="ts">
/**
 * M2 商品详情（design/02 §六）
 *
 * <p>「半熟人信任可见」（原则 4）：学号认证、同校、面交点三项必须一眼看到——
 * 所以这三样放在参数区的最前面，不折叠。
 *
 * <p>页面用自定义导航：顶部操作区浮在图集上（左返回、右更多），把分享 / 海报 / 举报
 * 收进「更多」，正文只留商品信息。
 *
 * <p>数据接 `/api/item/{id}`（免登录）。底部「聊一聊」进询单会话（同步留言）；
 * 「我想要」下单（未登录先跳登录）：只插待付款订单，商品不锁定，金额按标价。
 */

import { computed, ref } from 'vue'
import { onLoad, onPageScroll, onUnload } from '@dcloudio/uni-app'
import { formatPrice, formatRelativeTime } from '@campus/shared'
import { ADDRESS_KIND, addressApi, addressText, type AddressVO } from '@/api/address'
import { userItemApi, userOrderApi, type ApiItemVO } from '@/api/user'
import { itemCommentApi, momentApi, type ItemCommentVO } from '@/api/moment'
import { REPORT_REASONS } from '@/api/social'
import { REPORT_TARGET, reportTarget, submitReport } from '@/utils/report'
import { confirmPay, clip } from '@/utils/confirm-pay'
import { getSession } from '@/utils/session'
import CmIcon from '@/components/CmIcon.vue'
import CmDialogHost from '@/components/CmDialogHost.vue'
import ConditionTag from '@/components/ConditionTag.vue'

const myId = getSession().userId

/** 收货方式，与后端 DeliveryTypeEnum 对齐 */
const DELIVERY = { MEET: 1, PICKUP: 2, HOME: 3 } as const

const id = ref('')
const item = ref<ApiItemVO | null>(null)
/** 送货上门订单的收货地址：进页面先取默认地址，用户不必每次手动选 */
const shipAddress = ref<AddressVO | null>(null)
/** 分享到广场在途：防止连点重复发动态 */
const sharing = ref(false)

/** 状态栏高度：H5 为 0，小程序端要留出系统栏，否则按钮压在时间下面 */
const statusBarHeight = uni.getSystemInfoSync().statusBarHeight ?? 0
const sysInfo = uni.getSystemInfoSync()
/** 系统栏之下再让 10px：按钮离顶太近时单手够不到，也让图集顶部那层压暗有落点 */
const TOP_GAP = 10

/** 顶部操作区：滚过图集后转白底并淡出标题，图集上保持挖空圆钮 */
const scrollTop = ref(0)
onPageScroll((e) => {
  scrollTop.value = e.scrollTop
  // 浮层是钉在点击位置上的绝对层，页面一滚它就挂在错误的内容上。
  // 遮罩挡住了页面主体的拖动，但顶栏那一条在遮罩之上，仍能带着浮层一起走。
  if (moreOpen.value) {
    closeMore()
  }
})
/** 图集 640rpx≈333px、顶栏 = TOP_GAP + 96rpx ≈ 60px：滑到图完全躲进栏后（333-60）再转实底 */
const BAR_SOLID_AT = 273
const barSolid = computed(() => !item.value || scrollTop.value > BAR_SOLID_AT)

/** 「更多」：menu 是三个动作，report 是第二步选原因——同一个小浮层内切换，不叠系统弹层 */
const moreOpen = ref(false)
const moreStep = ref<'menu' | 'report'>('menu')
const reportReason = ref('')
/** 浮层锚点：贴着「更多」按钮右下角弹出，若取不到按钮位置就退到右侧安全位 */
const moreAnchor = ref({ top: statusBarHeight + TOP_GAP + 58, right: 16, cx: sysInfo.windowWidth - 40 })
/** 自己的商品没有举报对象，只给分享与海报 */
const canReport = computed(() => !!item.value && item.value.sellerId !== myId)

/**
 * 描述拆句：首句当引子（大一号、深色），其余按句分段。
 * 长段落靠段间距给呼吸，不靠首行缩进——移动端缩进会切碎阅读节奏。
 */
const descParts = computed(() => {
  const raw = (item.value?.description ?? '').trim()
  // 没写描述时不摆引子架子，占位句按正文排
  if (!raw) return { lead: '', body: ['卖家没有填写描述。'] }
  const sents = raw
    .replace(/\r/g, '')
    .replace(/\n+/g, '。')
    .split('。')
    .map((s) => s.trim())
    .filter(Boolean)
  // 只有一句话时不拆：整段塞进引子字号反而更难读
  if (sents.length < 2) return { lead: '', body: [raw] }
  return { lead: `${sents[0]}。`, body: sents.slice(1).map((s) => `${s}。`) }
})

/** 浮层宽度上限 176px（三行中文菜单的舒适宽度）；窄屏留 12px 边距，箭头夹在左右各 18px 内 */
const POP_W = 176
const popGeo = computed(() => {
  const vw = sysInfo.windowWidth
  const w = Math.min(POP_W, vw - 24)
  const right = Math.max(12, Math.min(moreAnchor.value.right, vw - 12 - w))
  const arrowLeft = Math.max(18, Math.min(w - 18, moreAnchor.value.cx - (vw - right - w)))
  return { w, right, arrowLeft }
})
const popStyle = computed(() => ({
  top: `${moreAnchor.value.top}px`,
  right: `${popGeo.value.right}px`,
  width: `${popGeo.value.w}px`
}))

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
  // 地址簿选完地址回来靠全局事件回传：地址 id 拼在 URL 里会被页面栈缓存，
  // 返回时 query 不变，拿不到新选的那条
  uni.$on('cm:address-picked', onAddressPicked)
  void userItemApi
    .detail(id.value)
    .then((vo) => {
      item.value = vo
      loadComments()
      if ((vo.deliveryType ?? DELIVERY.MEET) === DELIVERY.HOME) void loadShipAddress()
    })
    .catch(() => {
      // 不存在 / 已下架：request 层已提示，落空态
    })
})

onUnload(() => {
  uni.$off('cm:address-picked', onAddressPicked)
})

function onAddressPicked(payload: unknown): void {
  shipAddress.value = payload as AddressVO
}

async function loadShipAddress(): Promise<void> {
  try {
    const list = await addressApi.list(ADDRESS_KIND.ship)
    shipAddress.value = list.find((a) => a.isDefault === 1) ?? list[0] ?? null
  } catch {
    // 未登录或网络异常：下单前还会再拦一次，这里不打扰
  }
}

function pickShipAddress(): void {
  void uni.navigateTo({ url: `/pages/address/list?kind=${ADDRESS_KIND.ship}&pick=1` })
}

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

/** 当前商品的收货方式与展示文案（面交看面交点、自提看提货点、送货看收货地址） */
const deliveryType = computed(() => item.value?.deliveryType ?? DELIVERY.MEET)
const deliveryLabel = computed(() =>
  deliveryType.value === DELIVERY.PICKUP ? '自提' : deliveryType.value === DELIVERY.HOME ? '送货上门' : '面交'
)
const specKey = computed(() =>
  deliveryType.value === DELIVERY.PICKUP ? '自提点' : deliveryType.value === DELIVERY.HOME ? '收货地址' : '面交点'
)
const specValue = computed(() => {
  if (deliveryType.value === DELIVERY.PICKUP) return item.value?.pickupAddress || '卖家未填提货点'
  if (deliveryType.value === DELIVERY.HOME) {
    return shipAddress.value ? addressText(shipAddress.value) : '点击选择收货地址'
  }
  return item.value?.meetPointName ?? '待定'
})

/** 我想要：未登录先跳登录（下单要令牌），已登录确认后创建待付款订单进详情 */
async function onBuy(): Promise<void> {
  const it = item.value
  if (!it) return
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (it.sellerId === getSession().userId) {
    uni.showToast({ title: '不能购买自己发布的商品', icon: 'none' })
    return
  }
  // 送货上门必须落到一个地址：没有就先去选，别把订单创建出来却没有收货地
  if (deliveryType.value === DELIVERY.HOME && !shipAddress.value) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' })
    pickShipAddress()
    return
  }

  const lines: Array<{ label: string; value: string; strong?: boolean }> = [
    { label: '商品', value: clip(it.title) },
    { label: '应付', value: formatPrice(Number(it.price)), strong: true }
  ]
  if (deliveryType.value === DELIVERY.HOME && shipAddress.value) {
    lines.push({
      label: '收货',
      // 地址比商品名更长，放宽到 24 字才能留下门牌号
      value: clip(`${shipAddress.value.contactName} ${addressText(shipAddress.value)}`, 24)
    })
  } else if (deliveryType.value === DELIVERY.PICKUP) {
    lines.push({ label: '自提点', value: clip(it.pickupAddress || '下单后与卖家确认', 24) })
  } else {
    lines.push({ label: '面交点', value: clip(it.meetPointName || '下单后与卖家约定') })
  }

  // 「我想要」是拇指最常落的位置，误触一次就多一张待付款单，所以先过一道确认
  const ok = await confirmPay({
    title: '确认购买',
    lines,
    note: '仅创建待付款订单，暂不扣款；确认收货后才结算给卖家。',
    confirmText: '确认下单'
  })
  if (!ok) return

  const req =
    deliveryType.value === DELIVERY.HOME
      ? { itemId: it.id, addressId: shipAddress.value?.id }
      : {
          itemId: it.id,
          meetPointId: deliveryType.value === DELIVERY.MEET ? it.meetPointId ?? undefined : undefined
        }

  uni.showLoading({ title: '下单中…', mask: true })
  userOrderApi
    .create(req)
    .then((orderId) => {
      uni.hideLoading()
      uni.showToast({ title: '已创建订单', icon: 'success' })
      void uni.redirectTo({ url: `/pages/order/detail?id=${orderId}&role=buyer` })
    })
    .catch(() => uni.hideLoading())
}

/** 收藏 / 取消收藏：状态就地改 item.favorited，不重新拉详情 */
function onFavorite(): void {
  if (!item.value) return
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  const id = item.value.id
  const wasFav = !!item.value.favorited
  const call = wasFav ? userItemApi.unfavorite(id) : userItemApi.favorite(id)
  call
    .then(() => {
      const it = item.value
      if (it) it.favorited = !wasFav
      uni.showToast({ title: wasFav ? '已取消收藏' : '已收藏', icon: 'none' })
    })
    .catch(() => undefined)
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
    `id=${encodeURIComponent(it.id)}`,
    `title=${encodeURIComponent(it.title)}`,
    `price=${encodeURIComponent(formatPrice(Number(it.price)))}`,
    `meta=${encodeURIComponent(meta)}`,
    `image=${it.mainImage ? encodeURIComponent(it.mainImage) : ''}`
  ].join('&')
  void uni.navigateTo({ url: `/pages/poster/index?${q}` })
}

/** 返回：直接进入（无栈）时回首页，否则退一层——自定义导航没有系统返回键兜底 */
function goBack(): void {
  if (getCurrentPages().length > 1) {
    void uni.navigateBack()
    return
  }
  void uni.reLaunch({ url: '/pages/index/index' })
}

/** 打开前先量按钮位置，浮层落在手指当前位置的下方，不必再把手指移到底部 */
function openMore(): void {
  if (!item.value) return
  if (moreOpen.value) {
    closeMore()
    return
  }
  moreStep.value = 'menu'
  reportReason.value = ''
  uni
    .createSelectorQuery()
    .select('.tbtn--more')
    .boundingClientRect((res) => {
      const r = res as UniApp.NodeInfo | null
      const { bottom, right, width } = r ?? {}
      if (bottom != null && right != null && width != null && width > 0) {
        moreAnchor.value = { top: bottom + 10, right: sysInfo.windowWidth - right, cx: right - width / 2 }
      }
      moreOpen.value = true
    })
    .exec()
}

function closeMore(): void {
  moreOpen.value = false
}

/** 更多里的三个动作：分享与海报直接走，举报先切到选原因这一步 */
function onMore(key: 'share' | 'poster' | 'report'): void {
  if (key === 'report') {
    moreStep.value = 'report'
    return
  }
  closeMore()
  if (key === 'share') {
    void onShare()
  } else {
    onPoster()
  }
}

function onReportSubmit(): void {
  if (!item.value || !reportReason.value) return
  if (!getSession().tokenValue) {
    closeMore()
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  const reason = reportReason.value
  closeMore()
  submitReport(REPORT_TARGET.ITEM, item.value.id, reason)
}

/** 评论头像 → TA 的主页 */
function goProfile(userId: string): void {
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
  <view class="page cm-page">
    <!-- 顶部操作区：浮在图集上，滚动后转白底并淡出页面标题 -->
    <view
      class="topbar"
      :class="{ 'topbar--solid': barSolid, 'topbar--pop': moreOpen }"
      :style="{ paddingTop: statusBarHeight + TOP_GAP + 'px' }"
    >
      <view class="topbar__scrim" :class="{ 'topbar__scrim--off': barSolid }" />
      <view class="topbar__row">
        <view class="tbtn" hover-class="tbtn--on" @click="goBack">
          <CmIcon name="back" tone="ink" size="sm" />
        </view>
        <text class="topbar__title">商品详情</text>
        <view
          v-if="item"
          class="tbtn tbtn--more"
          :class="{ 'tbtn--open': moreOpen }"
          hover-class="tbtn--on"
          @click="openMore"
        >
          <CmIcon name="more" :tone="moreOpen ? 'white' : 'ink'" size="sm" />
        </view>
      </view>
    </view>

    <template v-if="item">
      <swiper class="swiper" indicator-dots circular>
        <swiper-item v-for="(s, i) in shots" :key="i">
          <image v-if="s" :src="s" mode="aspectFill" class="shot__img" />
          <view v-else class="shot" :style="{ backgroundImage: shotGradient() }" />
        </swiper-item>
      </swiper>

      <view class="box box--hero">
        <view class="hero__price">
          <text class="price">{{ formatPrice(Number(item.price)) }}</text>
          <text v-if="item.originPrice" class="origin">原价 {{ formatPrice(Number(item.originPrice)) }}</text>
        </view>
        <text class="title">{{ item.title }}</text>
        <text class="want">浏览 {{ item.viewCount }} 次 · {{ formatRelativeTime(item.publishAt) }}</text>
      </view>

      <!-- 交易信息：成色与交易条件并成一行标签作焦点，校区/面交点退为两列底注 -->
      <view class="box box--spec">
        <view class="spec__tags">
          <ConditionTag :grade="item.grade as 'A'" />
          <text class="spec__tag">{{ item.negotiable ? '可议价' : '一口价' }}</text>
          <text class="spec__tag">{{ deliveryLabel }}</text>
        </view>
        <view class="spec__grid">
          <view class="spec__cell">
            <text class="spec__k">校区</text>
            <text class="spec__v">{{ item.campusName }}</text>
          </view>
          <view
            class="spec__cell"
            :class="{ 'spec__cell--tap': deliveryType === DELIVERY.HOME }"
            @click="deliveryType === DELIVERY.HOME && pickShipAddress()"
          >
            <text class="spec__k">{{ specKey }}</text>
            <text class="spec__v spec__v--wrap">{{ specValue }}</text>
          </view>
        </view>
      </view>

      <view class="box seller" hover-class="seller--active" @click="goSeller">
        <view class="avatar" :style="{ backgroundImage: shotGradient() }" />
        <view class="seller__meta">
          <text class="seller__nick">{{ item.sellerNick ?? '卖家' }}</text>
          <view class="seller__row">
            <text class="seller__chip">信用 {{ item.sellerCredit ?? 100 }}</text>
            <text class="seller__hint">进主页看在售</text>
          </view>
        </view>
        <text class="seller__arrow">›</text>
      </view>

      <!-- 描述独占一张卡：分享类动作已收进「更多」，这里不再分心 -->
      <view class="box box--desc">
        <text class="desc__t">商品描述</text>
        <text v-if="descParts.lead" class="desc__lead">{{ descParts.lead }}</text>
        <text v-for="(p, i) in descParts.body" :key="i" class="desc__p">{{ p }}</text>
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

      <!-- 底部固定操作条：收藏 + 聊一聊 + 我想要，主行动占 60% -->
      <view class="actionbar">
        <view class="actionbar__fav" :class="{ 'actionbar__fav--on': item.favorited }" @click="onFavorite">
          <CmIcon name="heart" size="md" />
        </view>
        <view class="actionbar__minor" @click="onChat">聊一聊</view>
        <view class="actionbar__main" hover-class="actionbar__main--active" @click="onBuy">我想要</view>
      </view>
    </template>

    <view v-else class="empty">
      <text>商品不存在或已下架</text>
    </view>

    <!-- 更多：贴「更多」按钮下方弹出，遮罩透明只负责收起，不改变页面布局 -->
    <view v-if="moreOpen" class="pop-mask" @click="closeMore" @touchmove.stop.prevent />
    <view v-if="moreOpen" class="pop" :style="popStyle">
      <view class="pop__arrow" :style="{ left: popGeo.arrowLeft + 'px' }" />

      <template v-if="moreStep === 'menu'">
        <view class="pop__row" hover-class="pop__row--on" @click="onMore('share')">
          <text class="pop__l">{{ sharing ? '分享中…' : '分享到广场' }}</text>
        </view>
        <view class="pop__row" hover-class="pop__row--on" @click="onMore('poster')">
          <text class="pop__l">生成海报</text>
        </view>
        <view v-if="canReport" class="pop__row" hover-class="pop__row--on" @click="onMore('report')">
          <text class="pop__l">举报商品</text>
          <text class="pop__arrow-r">›</text>
        </view>
      </template>

      <template v-else>
        <view class="pop__hd" @click="moreStep = 'menu'">
          <text class="pop__back">‹</text>
          <text class="pop__t">举报原因</text>
        </view>
        <view
          v-for="r in REPORT_REASONS"
          :key="r"
          class="pop__opt"
          :class="{ 'pop__opt--on': reportReason === r }"
          @click="reportReason = r"
        >
          <text class="pop__opt-t">{{ r }}</text>
          <view class="pop__dot" />
        </view>
        <view class="pop__submit" :class="{ 'pop__submit--off': !reportReason }" @click="onReportSubmit">
          <text>提交举报</text>
        </view>
      </template>
    </view>

    <CmDialogHost />
  </view>
</template>

<style scoped lang="scss">
.page {
  padding-bottom: calc(160rpx + env(safe-area-inset-bottom));
}

/* ── 顶部操作区：挖空圆钮浮在图集上，滚动后转白底 ── */
.topbar {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  /* 高于遮罩：浮层展开时两颗按钮仍可直接点，再按一次即收起 */
  z-index: 38;
  background: transparent;
  transition: background-color 220ms ease-out, box-shadow 220ms ease-out;
}

.topbar--solid {
  background: rgba(255, 255, 255, 0.9);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  box-shadow: 0 1rpx 0 $cm-color-border;
}

/* 图集上的压暗层：白底图里白盘会与背景糊在一起，靠这层给按钮一条可读的底色 */
.topbar__scrim {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: -64rpx;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(17, 17, 22, 0.28) 0%,
    rgba(17, 17, 22, 0.12) 58%,
    rgba(17, 17, 22, 0) 100%
  );
  transition: opacity 220ms ease-out;
}

.topbar__scrim--off {
  opacity: 0;
}

.topbar__row {
  position: relative;
  height: 96rpx;
  padding: 0 $cm-space-4;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 微信右上角胶囊固定在系统栏下方，这里让出一格再放「更多」，否则两个按钮叠在一起 */
/* #ifdef MP-WEIXIN */
.topbar__row {
  padding-right: 200rpx;
}
/* #endif */

/* 挖空圆钮：实白盘 + 白环 + 投影，压在浅色图上边界仍清楚；图标用墨色线框 */
.tbtn {
  /* relative 只为给浮层展开态让出层级（见 .topbar--pop），不影响排版 */
  position: relative;
  width: 68rpx;
  height: 68rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1rpx solid rgba(255, 255, 255, 0.95);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.94);
  /* 内侧一道极淡的墨线：纯白图里白盘与背景同色时，靠它守住圆形边界 */
  box-shadow: inset 0 0 0 1rpx rgba(17, 17, 22, 0.08), 0 2rpx 10rpx rgba(17, 17, 22, 0.22);
  transition: background-color 220ms ease-out, border-color 220ms ease-out, box-shadow 220ms ease-out,
    transform 140ms ease-out;
}

.topbar--solid .tbtn {
  border-color: $cm-color-gray-100;
  background: $cm-color-gray-100;
  box-shadow: none;
}

/* 展开态：盘转深墨、图标反白，指明浮层是从哪个按钮出来的 */
.tbtn.tbtn--open,
.topbar--solid .tbtn.tbtn--open {
  border-color: rgba(17, 17, 22, 0.88);
  background: rgba(17, 17, 22, 0.88);
  box-shadow: 0 2rpx 10rpx rgba(17, 17, 22, 0.28);
}

.tbtn--on {
  transform: scale(0.92);
}

/* 浮层展开时遮罩升到 39（盖住顶栏），两个圆钮抬到 40 才仍可点：
   返回照常退一层，更多照常收起——不必先点一下空白把菜单关掉。 */
.topbar--pop .tbtn {
  z-index: 40;
}

/* 绝对居中：空态只有返回键时也不会被 space-between 挤到右边 */
.topbar__title {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
  opacity: 0;
  pointer-events: none;
  transition: opacity 220ms ease-out;
}

.topbar--solid .topbar__title {
  opacity: 1;
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
  border-radius: $cm-radius-2xl;
  box-shadow: $cm-shadow-xs;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

/* 价格卡压在图上：给「图 → 信息」一个层次交叠，避免两张白块并排的死板 */
.box--hero {
  position: relative;
  margin-top: -$cm-space-6;
  border-radius: $cm-radius-3xl $cm-radius-3xl $cm-radius-2xl $cm-radius-2xl;
  box-shadow: $cm-shadow-md;
}

.hero__price {
  display: flex;
  align-items: baseline;
  gap: $cm-space-3;
}

.price {
  font-size: 60rpx;
  font-weight: $cm-font-weight-heavy;
  line-height: 1;
  color: $cm-color-price;
  letter-spacing: -1rpx;
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

/* 交易信息：标签行作焦点，两列底注承接次级信息；四行平铺的分隔线全去掉，省出纵向空间 */
.box--spec {
  gap: $cm-space-4;
}

.spec__tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $cm-space-2;
}

.spec__tag {
  font-size: $cm-font-size-xs;
  line-height: 1.6;
  padding: 0 8rpx;
  color: $cm-color-text-2;
  background: $cm-color-gray-100;
  border-radius: $cm-radius-sm;
}

.spec__grid {
  display: flex;
  gap: $cm-space-3;
  padding-top: $cm-space-3;
  border-top: 1rpx solid $cm-color-border;
}

.spec__cell {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.spec__k {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.spec__v {
  font-size: $cm-font-size-base;
  font-weight: 500;
  color: $cm-color-text-1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 收货地址可能长过一格，允许折两行；面交点 / 自提点仍走单行省略。
   字号降一档，否则两行只放得下十几个字，门牌号必被截掉。 */
.spec__v--wrap {
  font-size: $cm-font-size-sm;
  white-space: normal;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.4;
}

/* 可点的格子给一点按压反馈：送货上门的「收货地址」要能换 */
.spec__cell--tap {
  transition: opacity 140ms ease-out;
}

.spec__cell--tap:active {
  opacity: 0.6;
}

.spec__cell--tap .spec__v {
  color: $cm-color-brand-text;
}

.seller {
  flex-direction: row;
  align-items: center;
  gap: $cm-space-4;
}

.seller--active {
  transform: scale(0.99);
}

.seller__arrow {
  font-size: $cm-font-size-lg;
  color: $cm-color-gray-400;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background-size: cover;
}

.seller__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.seller__nick {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-text-1;
}

.seller__row {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

/* 信用做浅底标签：比一行灰字更像「资质」，又不至于抢昵称的主位 */
.seller__chip {
  font-size: $cm-font-size-xs;
  line-height: 1.6;
  padding: 0 10rpx;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: $cm-radius-sm;
}

.seller__hint {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.desc__t {
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
}

/* 描述独占一卡：首句当引子，正文分段；靠字号与行距拉出阅读节奏 */
.box--desc {
  padding: $cm-space-5 $cm-space-5 $cm-space-6;
  gap: $cm-space-3;
}

.desc__lead {
  font-size: $cm-font-size-md;
  font-weight: 600;
  line-height: 1.65;
  color: $cm-color-text-1;
  word-break: break-all;
}

.desc__p {
  font-size: $cm-font-size-base;
  line-height: 1.85;
  color: $cm-color-text-2;
  word-break: break-all;
}

/* ── 「更多」浮层：贴着按钮下方弹出，尺寸固定，不影响正文与点击区 ── */
.pop-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  /* 盖过顶栏(38)：顶栏空白与标题也算「浮层外的区域」，点它们应当收起，
     而不是戳在遮罩上面没反应。两个圆钮各自抬到 40，仍可点。 */
  z-index: 39;
}

.pop {
  position: fixed;
  z-index: 41;
  padding: 10rpx 8rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.98);
  border-radius: $cm-radius-xl;
  box-shadow: 0 16rpx 48rpx rgba(17, 17, 22, 0.14), 0 4rpx 12rpx rgba(17, 17, 22, 0.06);
  transform-origin: top right;
  animation: popIn 160ms cubic-bezier(0.22, 1, 0.36, 1);
}

/* 箭头顶在按钮中心正下方，指明清单一源 */
.pop__arrow {
  position: absolute;
  top: -7rpx;
  width: 16rpx;
  height: 16rpx;
  margin-left: -8rpx;
  border-radius: 4rpx;
  background: rgba(255, 255, 255, 0.98);
  transform: rotate(45deg);
}

.pop__row {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  /* 左右各留 4px：分隔线内缩，不顶到圆角边缘，观感更软 */
  margin: 0 8rpx;
  padding: 24rpx 20rpx;
  transition: background-color 120ms ease-out;
}

.pop__row + .pop__row {
  border-top: 1rpx solid $cm-color-border;
}

.pop__row--on {
  background: $cm-color-gray-50;
  border-radius: $cm-radius-lg;
}

.pop__l {
  flex: 1;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.pop__arrow-r {
  font-size: $cm-font-size-md;
  line-height: 1;
  color: $cm-color-gray-400;
}

/* 举报第二步：头部回退一行，原因行比菜单行更矮，五项加按钮仍在 280px 内 */
.pop__hd {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin: 0 8rpx;
  padding: 12rpx 20rpx 8rpx;
}

.pop__back {
  font-size: $cm-font-size-md;
  line-height: 1;
  color: $cm-color-text-3;
}

.pop__t {
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-2;
}

.pop__opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 8rpx;
  padding: 18rpx 20rpx;
}

.pop__opt-t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.pop__dot {
  width: 30rpx;
  height: 30rpx;
  flex: none;
  border: 1rpx solid $cm-color-gray-300;
  border-radius: 50%;
}

.pop__opt--on .pop__opt-t {
  font-weight: 600;
  color: $cm-color-text-1;
}

.pop__opt--on .pop__dot {
  border-color: $cm-color-brand;
  background: $cm-color-brand;
  /* 内圈留白：实心点外面套一圈品牌色，比加粗描边更轻盈 */
  box-shadow: inset 0 0 0 6rpx #fff;
}

.pop__submit {
  margin: 8rpx 20rpx 10rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: $cm-font-size-sm;
  font-weight: 700;
  color: #fff;
  background: $cm-color-text-1;
  border-radius: 12rpx;
}

.pop__submit--off {
  background: $cm-color-gray-200;
  color: $cm-color-gray-500;
}

@keyframes popIn {
  from { opacity: 0; transform: translateY(-10rpx) scale(0.96); }
  to { opacity: 1; transform: none; }
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
  height: calc(112rpx + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding-left: $cm-space-4;
  padding-right: $cm-space-4;
  background: $cm-color-surface;
  border-top: 1rpx solid $cm-color-border;
  box-shadow: 0 -4rpx 20rpx rgba(27, 27, 33, 0.05);
}

/* 收藏：未收藏是灰心 + 浅灰底，收藏后转正红 + 浅红底并弹一下——
   只改图标颜色在小屏上几乎看不出来，底色一起变才有「点中了」的反馈 */
.actionbar__fav {
  flex: 0 0 auto;
  width: 76rpx;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $cm-color-text-3;
  background: $cm-color-bg;
  border-radius: $cm-radius-xl;
  transition: color 0.16s ease, background 0.16s ease;
}

/* 图标默认自带 text-2 颜色，不跟着父级变；收藏态要它继承按钮色，否则只有底色变、心里还是灰的 */
.actionbar__fav :deep(.i) {
  color: inherit;
  transition: color 0.16s ease;
}

.actionbar__fav--on {
  color: #e8543f;
  background: #ffe7e1;
  animation: favPop 320ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes favPop {
  0% {
    transform: scale(1);
  }

  40% {
    transform: scale(1.16);
  }

  70% {
    transform: scale(0.94);
  }

  100% {
    transform: scale(1);
  }
}

/* 次要动作：描边胶囊，白底不抢主按钮 */
.actionbar__minor {
  width: 38%;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-medium;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: $cm-radius-pill;
}

.actionbar__main {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  border-radius: $cm-radius-pill;
  transition: transform 140ms ease-out;
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
