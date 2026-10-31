<script setup lang="ts">
/**
 * P2 商品详情（business/01 §二 W-B2）
 *
 * <p>校园字段是一等公民（design/01 原则 5）：成色 / 校区 / 面交点 / 学号认证
 * 必须在**卡片、筛选、详情**三处同时可见，所以这里把四项放进参数表而不是折叠起来。
 *
 * <p>数据接 `/api/item/{id}`（免登录）：详情同时是交易入口——「聊一聊」进询单会话，
 * 自己的商品显示编辑入口。无主图时保留渐变占位（骨架期视觉方案）。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CONDITION_LABELS, formatPrice, formatRelativeTime, type GoodsItem } from '@campus/shared'
import {
  ADDRESS_KIND,
  REPORT_TARGET,
  fullAddress,
  userAddressApi,
  userDictApi,
  userItemApi,
  userItemCommentApi,
  userMomentApi,
  userOrderApi,
  type ApiItemVO,
  type CategoryVO,
  type ItemCommentVO,
  type UserAddressVO
} from '@campus/api'
import { useCompareStore } from '@/stores/compare'
import { useUserStore } from '@/stores/user'
import SharePoster from '@/components/SharePoster.vue'
import ReportDialog from '@/components/ReportDialog.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const userStore = useUserStore()

const item = ref<ApiItemVO | null>(null)
const loading = ref(true)
/** 分享到广场在途：防连点重复发动态 */
const sharing = ref(false)
/** 收藏在途：防连点 */
const favoriting = ref(false)
/** 海报弹层：纯前端 canvas，打开时才绘制 */
const posterOpen = ref(false)

/** 评论举报弹层 */
const reportOpen = ref(false)
const reportId = ref('')

function openCommentReport(id: string): void {
  reportId.value = id
  reportOpen.value = true
}

/** 商品评论（notes/22 S7）：仅登录可发、仅 ON_SALE 可发，下架后仍可读历史 */
const comments = ref<ItemCommentVO[]>([])
const commentTotal = ref('0')
const commentText = ref('')
const commentSending = ref(false)
/** 回复目标：存用户 id + 昵称，不定位父楼（与后端扁平列表一致） */
const replyTarget = ref<{ userId: string; nickname: string } | null>(null)
const canComment = computed(() => item.value?.status === 'ON_SALE')

/** 分类字典：把商品挂的二级分类 id 还原成「一级 / 二级」，商品表只存二级 id */
const dictCats = ref<CategoryVO[]>([])
const catPath = computed(() => {
  const id = item.value?.categoryId
  if (!id) return ''
  const self = dictCats.value.find((c) => c.id === id)
  if (!self) return item.value?.categoryName ?? ''
  const parent = self.parentId ? dictCats.value.find((c) => c.id === self.parentId) : undefined
  return parent ? `${parent.name} / ${self.name}` : self.name
})

onMounted(async () => {
  // 字典不阻塞详情：拉不到就退化成只显示二级分类名
  void userDictApi
    .all()
    .then((d) => (dictCats.value = d.categories))
    .catch(() => undefined)
  try {
    item.value = await userItemApi.detail(String(route.params.id ?? ''))
    void loadComments()
  } catch {
    // 不存在 / 已下架：http 层已提示，这里落空态
  } finally {
    loading.value = false
  }
})

/** 图集：有真实图片用图片，否则一张渐变占位（切换「图」就是换一组渐变） */
const shots = computed(() => {
  const imgs = item.value?.images ?? []
  return imgs.length ? imgs.map((i) => i.url) : [null]
})
const active = ref(0)
const bg = (flip: boolean): string => {
  const tints: [string, string] = ['#ECEFF3', '#D3D9E0']
  return `linear-gradient(135deg, ${flip ? tints[1] : tints[0]}, ${flip ? tints[0] : tints[1]})`
}

const selected = computed(() => (item.value ? compare.has(item.value.id) : false))
const isMine = computed(() => !!item.value && userStore.userId === item.value.sellerId)
/** 仅在售可下单：下架 / 已售不开放购买入口 */
const onSale = computed(() => item.value?.status === 'ON_SALE')

/** 收货方式（与后端 DeliveryTypeEnum 的 code 一致）：1 面交 2 自提 3 送货上门 */
const DELIVERY = { MEET: 1, PICKUP: 2, DELIVERY: 3 } as const
const deliveryType = computed(() => item.value?.deliveryType ?? DELIVERY.MEET)
const DELIVERY_LABELS: Record<number, string> = { 1: '面交', 2: '自提', 3: '送货上门' }
const deliveryLabel = computed(() => DELIVERY_LABELS[deliveryType.value] ?? '面交')
/** 卖家头像：按种子约定从 sellerId 派生 demo 头像（后端暂未下发 sellerAvatar） */
const sellerAvatarUrl = computed(() => `/api/file/demo/avatar/${item.value?.sellerId}.png`)

function onCompare(): void {
  if (!item.value) return
  // 对比 store 的字段来自 shared 契约，分类 / 校区均为字典字符串
  const goods: GoodsItem = {
    id: item.value.id,
    title: item.value.title,
    categoryId: item.value.categoryId,
    category: item.value.categoryName ?? '其他',
    price: Number(item.value.price),
    originalPrice: item.value.originPrice ? Number(item.value.originPrice) : undefined,
    condition: item.value.grade as GoodsItem['condition'],
    campus: item.value.campusName ?? '',
    meetPoint: item.value.meetPointName ?? '待定',
    negotiable: !!item.value.negotiable,
    delivery: item.value.deliveryType === 3,
    status: item.value.status,
    wantCount: 0,
    description: item.value.description ?? '',
    seller: {
      id: item.value.sellerId,
      nick: item.value.sellerNick ?? '卖家',
      verified: true,
      credit: '良好',
      tints: ['#BFD4EA', '#8FA9C4']
    },
    images: [],
    tints: ['#ECEFF3', '#D3D9E0'],
    publishedAt: item.value.publishAt
  }
  const r = compare.toggle(goods)
  if (r === 'full') ElMessage.warning({ message: '最多对比 4 件', duration: 2000 })
  else if (r === 'mismatch')
    ElMessage.warning({ message: `对比仅限同类商品（${compare.lockedCategoryName}）`, duration: 2200 })
}

/* ── 下单：送货上门需先选收货地址，面交 / 自提直接下单 ─── */

const addressDialogVisible = ref(false)
const addressLoading = ref(false)
const shipAddresses = ref<UserAddressVO[]>([])
const selectedAddressId = ref('')

/**
 * 立即购买：登录后下单（待付款），跳订单详情页完成付款 / 面交核销。
 *
 * <p>三条收货方式分叉：面交直接下单（面交点下单后约）、自提直接用卖家提货地址、
 * 送货上门必须先选收货地址——所以只有送货上门会先弹地址选择框。
 */
async function onBuy(): Promise<void> {
  if (!item.value) return
  if (!userStore.userId) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (deliveryType.value === DELIVERY.DELIVERY) {
    await openAddressPicker()
    return
  }
  await createOrder()
}

async function createOrder(addressId?: string): Promise<void> {
  if (!item.value) return
  try {
    const orderId = await userOrderApi.create({ itemId: item.value.id, addressId })
    ElMessage.success('订单已创建，去付款')
    void router.push({ name: 'order-detail', params: { id: orderId } })
  } catch {
    // http 层已提示（商品不在售 / 未选地址等）
  }
}

/** 打开收货地址选择框：默认选中默认地址，没有默认就选第一条 */
async function openAddressPicker(): Promise<void> {
  addressDialogVisible.value = true
  addressLoading.value = true
  try {
    shipAddresses.value = await userAddressApi.list(ADDRESS_KIND.SHIP)
    if (!shipAddresses.value.some((a) => a.id === selectedAddressId.value)) {
      const def = shipAddresses.value.find((a) => a.isDefault === 1) ?? shipAddresses.value[0]
      selectedAddressId.value = def?.id ?? ''
    }
  } catch {
    // http 层已提示
  } finally {
    addressLoading.value = false
  }
}

async function confirmAddressOrder(): Promise<void> {
  if (!selectedAddressId.value) {
    ElMessage.warning({ message: '请先选择收货地址', duration: 2000 })
    return
  }
  addressDialogVisible.value = false
  await createOrder(selectedAddressId.value)
}

function goAddressManage(): void {
  addressDialogVisible.value = false
  void router.push({ name: 'address', query: { kind: String(ADDRESS_KIND.SHIP) } })
}

/**
 * 分享到广场（动态 type=2）
 *
 * <p>正文给一句默认推荐语，不让用户先写再发——分享要一步完成才有转化。
 * 商品必须仍在售，非在售服务端返回 4023，由请求层提示。
 */
async function onShare(): Promise<void> {
  if (!item.value || sharing.value) return
  if (!userStore.userId) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  sharing.value = true
  try {
    await userMomentApi.publish({
      type: 2,
      content: `推荐一件好物：${item.value.title}`,
      itemId: item.value.id
    })
    ElMessage.success('已分享到广场')
  } catch {
    // http 层已提示（含 4023 商品不在售）
  } finally {
    sharing.value = false
  }
}

/** 评论头像加载失败（如 MinIO 离线）退回 demo 渐变占位，保证头像区始终有图 */
function avatarFallback(e: Event, userId: string): void {
  const img = e.target as HTMLImageElement
  img.onerror = null
  img.src = `/api/file/demo/avatar/${userId}.png`
}

/** 拉评论：一次取 50 条，商品评论量小不分页 */
async function loadComments(): Promise<void> {
  if (!item.value) return
  try {
    const p = await userItemCommentApi.list(item.value.id, 1, 50)
    comments.value = p.list
    commentTotal.value = p.total
  } catch {
    // http 层已提示；评论拉不到不影响商品主体信息
  }
}

/** 未登录一律先登录再回来，避免用户写完才发现发不出去 */
function requireLogin(): boolean {
  if (userStore.userId) return true
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

function startReply(c: ItemCommentVO): void {
  if (!requireLogin()) return
  replyTarget.value = { userId: c.userId, nickname: c.author?.nickname ?? '同学' }
}

/** 发评论：正文由服务端过敏感词（低危词会变 *），所以发完重拉而不是本地插 */
async function sendComment(): Promise<void> {
  if (!item.value || commentSending.value) return
  const text = commentText.value.trim()
  if (!text) return
  if (!requireLogin()) return
  commentSending.value = true
  try {
    await userItemCommentApi.publish(item.value.id, text, replyTarget.value?.userId)
    ElMessage.success('评论已发布')
    commentText.value = ''
    replyTarget.value = null
    await loadComments()
  } catch {
    // http 层已提示（4024 敏感词 / 4025 商品不在售）
  } finally {
    commentSending.value = false
  }
}

/** 聊一聊：未登录先跳登录（带回跳）；自己的商品不进会话。
 *  商品摘要随 query 带上——首聊时会话还没建，ChatView 的壳会话用它显示顶部卡片 */
function onChat(): void {
  if (!item.value) return
  if (!userStore.userId) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  void router.push({
    name: 'chat',
    query: {
      itemId: item.value.id,
      peerId: item.value.sellerId,
      itemTitle: item.value.title,
      itemImage: item.value.mainImage ?? '',
      itemPrice: item.value.price,
      itemStatus: item.value.status
    }
  })
}

/** 收藏 / 取消收藏：未登录先跳登录（带回跳）。状态就地改 item.favorited，不重新拉详情 */
async function onFavorite(): Promise<void> {
  if (!item.value) return
  if (!userStore.userId) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (favoriting.value) return
  favoriting.value = true
  const id = item.value.id
  try {
    if (item.value.favorited) {
      await userItemApi.unfavorite(id)
      item.value.favorited = false
      ElMessage.success('已取消收藏')
    } else {
      await userItemApi.favorite(id)
      item.value.favorited = true
      ElMessage.success('已收藏，可在「我的收藏」里找回')
    }
  } finally {
    favoriting.value = false
  }
}

function onEdit(): void {  if (!item.value) return
  void router.push({ path: '/publish', query: { edit: item.value.id } })
}

/** 卖家卡 → TA 的个人主页；自己的商品点进自己的主页也没问题 */
function goSeller(): void {
  if (!item.value) return
  void router.push({ name: 'user-profile', params: { id: item.value.sellerId } })
}
</script>

<template>
  <div v-if="item" class="cm-shell detail">
    <div class="top">
      <!-- 图集 -->
      <div class="gallery">
        <div class="stage" :style="{ background: shots[active] ? undefined : bg(active % 2 === 1) }">
          <img v-if="shots[active]" :src="shots[active]!" alt="" class="stage__img" />
        </div>
        <div class="thumbs">
          <button
            v-for="(s, i) in shots"
            :key="i"
            type="button"
            class="thumb"
            :class="{ on: active === i }"
            :style="{ background: s ? undefined : bg(i % 2 === 1) }"
            :aria-label="`第 ${i + 1} 张图`"
            @click="active = i"
          >
            <img v-if="s" :src="s" alt="" class="thumb__img" />
          </button>
        </div>
      </div>

      <!-- 价格盒 -->
      <div class="box">
        <h1 class="title">{{ item.title }}</h1>

        <div class="tags">
          <span
            class="cm-tag"
            :class="
              deliveryType === DELIVERY.DELIVERY
                ? 'cm-tag--red'
                : deliveryType === DELIVERY.PICKUP
                  ? 'cm-tag--yellow'
                  : 'cm-tag--blue'
            "
          >
            {{ deliveryLabel }}
          </span>
          <span v-if="item.negotiable" class="cm-tag cm-tag--yellow">可议价</span>
          <span class="cm-tag cm-tag--blue">信用 {{ item.sellerCredit ?? 100 }} 分</span>
        </div>

        <div class="price">
          <strong>{{ formatPrice(Number(item.price)) }}</strong>
          <s v-if="item.originPrice">{{ formatPrice(Number(item.originPrice)) }}</s>
          <span class="want">浏览 {{ item.viewCount }} 次</span>
        </div>

        <dl class="params">
          <dt>分类</dt>
          <dd class="params__wide">{{ catPath }}</dd>
          <dt>成色</dt>
          <dd>{{ item.grade }} 级 · {{ CONDITION_LABELS[item.grade as 'A'] }}</dd>
          <dt>校区</dt>
          <dd>{{ item.campusName }}</dd>
          <dt>收货方式</dt>
          <dd>{{ deliveryLabel }}</dd>
          <template v-if="deliveryType === DELIVERY.MEET">
            <dt>面交点</dt>
            <dd>{{ item.meetPointName ?? '待定' }}</dd>
          </template>
          <dt>发布</dt>
          <dd>{{ formatRelativeTime(item.publishAt) }}</dd>
        </dl>

        <p v-if="deliveryType === DELIVERY.DELIVERY" class="delivery-note">
          送货上门：下单时选择你的收货地址，卖家发货后你确认收货。
        </p>
        <p v-else-if="deliveryType === DELIVERY.PICKUP" class="delivery-note">
          自提：下单后到卖家设置的提货地址自取，付款后订单里可见具体地址。
        </p>

        <div class="acts">
          <template v-if="isMine">
            <el-button type="primary" size="large" @click="onEdit">编辑商品</el-button>
          </template>
          <template v-else>
            <el-button type="primary" size="large" @click="onBuy" :disabled="!onSale">
              {{ onSale ? '立即购买' : '已下架' }}
            </el-button>
            <el-button size="large" @click="onChat">聊一聊</el-button>
            <el-button size="large" :type="selected ? 'warning' : 'default'" @click="onCompare">
              {{ selected ? '已加入对比' : '加入对比' }}
            </el-button>
          </template>
          <el-button
            size="large"
            :type="item.favorited ? 'danger' : 'default'"
            :plain="!!item.favorited"
            :loading="favoriting"
            @click="onFavorite"
          >
            {{ item.favorited ? '已收藏' : '收藏' }}
          </el-button>
        </div>

        <!-- 卖家卡：点击进 TA 的主页（资料 + 在售闲置） -->
        <div class="seller seller--link" @click="goSeller">
          <img v-if="sellerAvatarUrl" class="avatar" :src="sellerAvatarUrl" :alt="item.sellerNick ?? '卖家'" />
          <span v-else class="avatar" :style="{ background: bg(false) }" />
          <div class="seller__meta">
            <span class="nick">{{ item.sellerNick ?? '卖家' }}</span>
            <span class="sub">信用 {{ item.sellerCredit ?? 100 }} 分 · 进主页 →</span>
          </div>
        </div>

        <p class="notice">
          面交须知：请在校园内公共场所交接，先验货后付款；平台不代收货款，
          任何要求站外转账的都请举报。
        </p>
      </div>
    </div>

    <section class="desc">
      <div class="desc__hd">
        <h2>商品描述</h2>
        <div class="desc__acts">
          <el-button link type="primary" @click="posterOpen = true">生成海报</el-button>
          <el-button link type="primary" :loading="sharing" @click="onShare">分享到广场</el-button>
        </div>
      </div>
      <p class="desc__text">{{ item.description || '卖家没有填写描述。' }}</p>
    </section>

    <!-- 商品评论（notes/22 S7）：与购后星级评价是两套，这里只做售前问答 -->
    <section class="cmt">
      <div class="cmt__hd">
        <h2>商品评论</h2>
        <span class="cmt__n">{{ commentTotal }} 条</span>
      </div>

      <p v-if="!comments.length" class="cmt__empty">还没有人评论，来问两句</p>

      <ul v-else class="cmt__list">
        <li v-for="c in comments" :key="c.id" class="cmt__row">
          <img
            v-if="c.author?.avatarUrl"
            class="cmt__ava cmt__ava--img"
            :src="c.author.avatarUrl"
            :alt="c.author?.nickname"
            @error="avatarFallback($event, c.userId)"
          />
          <span v-else class="cmt__ava" :style="{ background: bg(false) }" />
          <div class="cmt__body">
            <div class="cmt__top">
              <span class="cmt__nick">{{ c.author?.nickname ?? '同学' }}</span>
              <span class="cmt__time">{{ formatRelativeTime(c.createTime) }}</span>
            </div>
            <p class="cmt__text">
              <span v-if="c.replyToNick" class="cmt__at">回复 @{{ c.replyToNick }}：</span>{{ c.content }}
            </p>
            <div class="cmt__acts">
              <button type="button" class="cmt__reply" @click="startReply(c)">回复</button>
              <button
                v-if="c.userId !== userStore.userId"
                type="button"
                class="cmt__reply"
                @click="openCommentReport(c.id)"
              >
                举报
              </button>
            </div>
          </div>
        </li>
      </ul>

      <div class="cmt__editor">
        <div v-if="replyTarget" class="cmt__replyto">
          <span>回复 @{{ replyTarget.nickname }}</span>
          <button type="button" @click="replyTarget = null">取消</button>
        </div>
        <el-input
          v-model="commentText"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          :disabled="!canComment"
          :placeholder="canComment ? '问问成色、配件或交易方式' : '商品已下架，暂不可评论'"
        />
        <div class="cmt__foot">
          <span class="cmt__hint">评论对所有人可见，请文明交流</span>
          <el-button
            type="primary"
            :disabled="!canComment || !commentText.trim()"
            :loading="commentSending"
            @click="sendComment"
          >
            发表评论
          </el-button>
        </div>
      </div>
    </section>
  </div>

  <!-- 找不到（id 被改过 / 已下架）：给回列表的出口，不留白屏 -->
  <div v-else-if="!loading" class="cm-shell">
    <el-empty description="商品不存在或已下架">
      <el-button type="primary" @click="router.push({ name: 'home' })">回首页逛逛</el-button>
    </el-empty>
  </div>

  <!-- 送货上门：先选收货地址再下单 -->
  <el-dialog v-model="addressDialogVisible" title="选择收货地址" width="460px" append-to-body>
    <div v-loading="addressLoading" class="addr-pick">
      <p v-if="!addressLoading && !shipAddresses.length" class="addr-pick__empty">
        还没有收货地址，
        <el-button link type="primary" @click="goAddressManage">去添加 ›</el-button>
      </p>
      <el-radio-group v-else v-model="selectedAddressId" class="addr-pick__group">
        <el-radio v-for="a in shipAddresses" :key="a.id" :value="a.id" class="addr-pick__row">
          <span class="addr-pick__name">{{ a.contactName }}</span>
          <span class="addr-pick__phone">{{ a.contactPhone }}</span>
          <span v-if="a.isDefault === 1" class="addr-pick__badge">默认</span>
          <span class="addr-pick__detail">{{ fullAddress(a) }}</span>
        </el-radio>
      </el-radio-group>
    </div>
    <template #footer>
      <el-button @click="addressDialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedAddressId" @click="confirmAddressOrder">确认下单</el-button>
    </template>
  </el-dialog>

  <SharePoster v-if="posterOpen && item" :item="item" @close="posterOpen = false" />
  <ReportDialog v-model="reportOpen" :target-type="REPORT_TARGET.ITEM_COMMENT" :target-id="reportId" />
</template>

<style scoped lang="scss">
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
}

.top {
  display: flex;
  gap: var(--cm-space-6);
  align-items: flex-start;
}

/* ── 图集 ───────────────────────────────────────────────── */

.gallery {
  width: 460px;
  flex: none;
}

.stage {
  aspect-ratio: 4 / 3;
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
  overflow: hidden;
}

.stage__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumbs {
  display: flex;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.thumb {
  width: 84px;
  height: 63px;
  border: 2px solid transparent;
  border-radius: var(--cm-radius-lg);
  cursor: pointer;
  padding: 0;
  overflow: hidden;
}

.thumb.on {
  border-color: var(--cm-color-brand);
}

.thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ── 价格盒 ─────────────────────────────────────────────── */

.box {
  flex: 1;
  min-width: 0;
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xl);
  line-height: 1.4;
  font-weight: 600;
}

/* 标题下的卖点标签行：与卡片标题里的 chips 同一套（cm-tag 全局类） */
.tags {
  display: flex;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.price {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-3);
  padding-bottom: var(--cm-space-4);
  border-bottom: 1px solid var(--cm-color-border);
}

.price strong {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--cm-color-price);
}

.price s {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.want {
  margin-left: auto;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.params {
  display: grid;
  grid-template-columns: 72px 1fr 72px 1fr;
  gap: var(--cm-space-2) var(--cm-space-4);
  margin: var(--cm-space-4) 0;
  font-size: var(--cm-font-size-sm);
}

/* 分类名是「一级 / 二级」，比其余值长，独占一行（4 列网格跨 3 列） */
.params__wide {
  grid-column: span 3;
}

.params dt {
  color: var(--cm-color-text-3);
}

.params dd {
  margin: 0;
  color: var(--cm-color-text-1);
}

.delivery-note {
  margin: 0 0 var(--cm-space-4);
  padding: var(--cm-space-2) var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-md);
}

/* 收货地址选择：每行一条，选中态描边 */
.addr-pick {
  min-height: 60px;
}

.addr-pick__empty {
  margin: var(--cm-space-3) 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.addr-pick__group {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
  width: 100%;
}

.addr-pick__row {
  display: flex;
  align-items: center;
  height: auto;
  padding: var(--cm-space-3);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  margin-right: 0;
}

.addr-pick__row.is-checked {
  border-color: var(--cm-color-brand);
  background: var(--cm-color-brand-bg);
}

.addr-pick__name {
  font-weight: 600;
}

.addr-pick__phone {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-text-2);
}

.addr-pick__badge {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-sm);
  padding: 0 6px;
}

.addr-pick__detail {
  display: block;
  margin-top: 2px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.acts {
  display: flex;
  gap: var(--cm-space-3);
  margin-bottom: var(--cm-space-5);
}

/* 详情页主行动按钮高 46px（design/01 §4.2） */
.acts :deep(.el-button--large) {
  height: 46px;
  padding: 0 var(--cm-space-8);
}

.seller {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  padding: var(--cm-space-3) var(--cm-space-4);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-lg);
}

.seller--link {
  cursor: pointer;
  transition: background 160ms ease-out;

  &:hover {
    background: var(--cm-color-brand-bg);
  }
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex: none;
  object-fit: cover;
}

.seller__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-right: auto;
}

.nick {
  font-weight: 600;
}

.sub {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.sub em {
  font-style: normal;
  color: var(--cm-color-ok);
}

.notice {
  margin: var(--cm-space-4) 0 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.6;
  color: var(--cm-color-text-3);
}

/* ── 描述 ───────────────────────────────────────────────── */

.desc {
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.desc__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--cm-space-3);
}

.desc h2 {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.desc__acts {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.desc__text {
  margin: 0;
  white-space: pre-line;
  line-height: 1.6;
  color: var(--cm-color-text-2);
}

/* ── 商品评论：售前问答，扁平一层不套楼 ── */
.cmt {
  margin-top: var(--cm-space-4);
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.cmt__hd {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.cmt__hd h2 {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.cmt__n {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.cmt__empty {
  margin: var(--cm-space-4) 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.cmt__list {
  margin: 0 0 var(--cm-space-4);
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

.cmt__row {
  display: flex;
  gap: var(--cm-space-3);
}

.cmt__ava {
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.cmt__body {
  flex: 1;
  min-width: 0;
}

.cmt__top {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
}

.cmt__nick {
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.cmt__time {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.cmt__text {
  margin: 2px 0 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  color: var(--cm-color-text-2);
  word-break: break-word;
}

.cmt__at {
  color: var(--cm-color-brand-dark);
}

.cmt__acts {
  display: flex;
  gap: var(--cm-space-3);
  margin-top: var(--cm-space-1);
}

.cmt__reply {
  padding: 0;
  border: 0;
  background: none;
  cursor: pointer;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.cmt__reply:hover {
  color: var(--cm-color-brand-dark);
}

.cmt__editor {
  padding-top: var(--cm-space-4);
  border-top: 1px solid var(--cm-color-border);
}

.cmt__replyto {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--cm-space-2);
  padding: 0 var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-brand-dark);
}

.cmt__replyto button {
  border: 0;
  background: none;
  cursor: pointer;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.cmt__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--cm-space-3);
}

.cmt__hint {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

@media (max-width: 1023px) {
  .top {
    flex-direction: column;
  }

  .gallery {
    width: 100%;
  }
}
</style>
