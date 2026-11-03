<script setup lang="ts">
/**
 * 订单详情（学生端，notes/13 第二、七节的状态机）
 *
 * <p>动作按「状态 × 角色」给出，与后端各接口的准入一一对应：
 * - 待付款：买家付款 / 取消（取消即作废，不产生退款单）；卖家只读；
 * - 待面交：卖家持有核销码并向买家出示，买家输入后 → 已验货；买家可申请退款，卖家只读；
 * - 已验货：买家确认收货 → 已完成；卖家只读；
 * - 退款中：**卖家同意退款（已退款，商品恢复在售）/ 拒绝（纠纷中，转管理端裁决）**；
 *   买家只读，等卖家处理，24 小时未处理系统自动同意；
 * - 纠纷中与终态：双方只读，等平台裁决。
 *
 * <p>防单方核销：核销码只向卖家暴露（卖家持有），只能由买家录入核销；任一方都无法单独完成。
 *
 * <p>role 从 query 带入：同一单两个视角，动作集合不同，后端也按角色校验
 * （卖家去点付款会被拒），所以不能靠页面猜。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  userOrderApi,
  userReviewApi,
  type OrderReviewVO,
  type OrderRole,
  type UserOrderStatus,
  type UserOrderVO
} from '@campus/api'
import { confirmPay } from '@/utils/confirm-pay'

const route = useRoute()
const router = useRouter()

const order = ref<UserOrderVO | null>(null)
const loading = ref(false)
const acting = ref(false)
const verifyCode = ref('')
/** 已完成订单的互评状态：我评没评、对方评没评、还能不能评（notes/13 O18） */
const review = ref<OrderReviewVO | null>(null)

const role = computed<OrderRole>(() => (route.query.role === 'seller' ? 'seller' : 'buyer'))
const isBuyer = computed(() => role.value === 'buyer')

/**
 * 交易对方（与当前视角相对的另一方）：买家视角的对方是卖家，卖家视角是买家。
 * 头像优先用后端下发的真实 URL，缺失时按 id 派生 demo 占位图，避免裂图。
 */
const peer = computed(() => {
  const o = order.value
  if (!o) return { id: '', nick: '', avatar: '', role: '' }
  const id = isBuyer.value ? o.sellerId : o.buyerId
  const nick = isBuyer.value ? o.sellerNick || '卖家' : o.buyerNick || '买家'
  const avatar = isBuyer.value ? o.sellerAvatar : o.buyerAvatar
  return { id, nick, avatar: avatar || '', role: isBuyer.value ? '卖家' : '买家' }
})
const peerAvatarUrl = computed(() =>
  peer.value.avatar || (peer.value.id ? `/api/file/demo/avatar/${peer.value.id}.png` : '')
)

/** 找对方聊一聊：带商品与对方身份参数，让聊天页直接开起对应会话（首条消息才建会话） */
const chatTo = computed(() => ({
  name: 'chat' as const,
  query: {
    itemId: order.value?.itemId,
    peerId: isBuyer.value ? order.value?.sellerId : order.value?.buyerId,
    itemTitle: order.value?.itemTitle || undefined,
    itemImage: order.value?.itemMainImage || undefined,
    itemPrice: order.value?.itemPrice || undefined,
    peerNick: peer.value.nick || undefined
  }
}))

const STATUS_TEXT: Record<UserOrderStatus, string> = {
  WAIT_PAY: '等待买家付款',
  WAIT_MEET: '已付款，等待面交核销',
  VERIFIED: '已验货，等待买家确认收货',
  COMPLETED: '交易完成',
  CANCELED: '订单已取消',
  REFUNDING: '退款申请处理中',
  REFUNDED: '已退款',
  DISPUTING: '纠纷处理中，平台将介入裁决',
  WAIT_SHIP: '已付款，等待卖家发货',
  SHIPPED: '卖家已发货，等待确认收货',
  WAIT_PICKUP: '已付款，等待到店自提'
}

/** 收货方式文案：面交 / 自提 / 送货上门 */
const DELIVERY_TEXT: Record<string, string> = { MEET: '面交', PICKUP: '自提', DELIVERY: '送货上门' }
const deliveryText = computed(() => DELIVERY_TEXT[order.value?.deliveryType ?? 'MEET'] ?? '面交')
/** 有地址快照才展示地址块（面交无地址，只有面交点） */
const hasAddress = computed(() => !!order.value?.addressDetail)

/**
 * 需要出示/录入核销码的状态：面交（WAIT_MEET）与自提（WAIT_PICKUP）都要双方到场交接，
 * 送货上门由物流承担交接，不需要核销码。
 */
const needCode = computed(() => {
  const s = order.value?.status
  return s === 'WAIT_MEET' || s === 'WAIT_PICKUP'
})
/** 核销卡标题跟着收货方式走：自提时写「面交核销」会让人以为要去面交点见面 */
const verifyTitle = computed(() =>
  order.value?.deliveryType === 'PICKUP' ? '自提核销' : '面交核销'
)

/** 待付款剩余分钟数；负数表示已被超时任务扫掉 */
const expireLeft = computed(() => {
  const o = order.value
  if (!o || o.status !== 'WAIT_PAY' || !o.expireTime) return null
  const ms = new Date(o.expireTime.replace(' ', 'T')).getTime() - Date.now()
  return Math.max(0, Math.ceil(ms / 60000))
})

const orderId = computed(() => String(route.params.id ?? ''))

async function load(): Promise<void> {
  loading.value = true
  try {
    order.value = await userOrderApi.detail(orderId.value)
    // 评价状态只在已完成的单上取：其余状态问一次也是白问
    if (order.value.status === 'COMPLETED') {
      await loadReview()
    }
  } catch {
    order.value = null
  } finally {
    loading.value = false
  }
}

async function loadReview(): Promise<void> {
  try {
    review.value = await userReviewApi.ofOrder(orderId.value)
  } catch {
    review.value = null
  }
}

/* ── 互评弹窗：评分 + 标签 + 文字，一单一评，提交后不回改 ── */

const reviewOpen = ref(false)
const reviewSaving = ref(false)
const reviewForm = reactive<{ rating: number; tags: string[]; comment: string }>({
  rating: 5,
  tags: [],
  comment: ''
})

const tagOptions = computed(() => {
  const t = review.value?.tagTable
  return [...(t?.positive ?? []), ...(t?.negative ?? [])].map((i) => i.label)
})

function openReview(): void {
  reviewForm.rating = 5
  reviewForm.tags = []
  reviewForm.comment = ''
  reviewOpen.value = true
}

async function submitReview(): Promise<void> {
  if (reviewSaving.value) return
  if (reviewForm.rating < 1) {
    ElMessage.warning('请先选择评分')
    return
  }
  reviewSaving.value = true
  try {
    await userReviewApi.submit(orderId.value, {
      rating: reviewForm.rating,
      tags: reviewForm.tags.length ? reviewForm.tags : undefined,
      comment: reviewForm.comment.trim() || undefined
    })
    ElMessage.success('评价已提交')
    reviewOpen.value = false
    await loadReview()
  } catch {
    // http 层已提示（2010 未完成 / 2011 过期 / 4024 敏感词）
  } finally {
    reviewSaving.value = false
  }
}

/**
 * 统一执行器：动作成功后刷新详情。
 * 失败不额外提示——http 层已按后端错误码给消息（如「当前状态不可操作」）。
 */
async function run(action: () => Promise<unknown>, ok: string): Promise<void> {
  acting.value = true
  try {
    await action()
    ElMessage.success({ message: ok, duration: 2000 })
    await load()
  } catch {
    // http 层已提示
  } finally {
    acting.value = false
  }
}

/** 带输入框的确认弹窗：取消 / 申请退款 / 拒绝退款 / 发起纠纷共用 */
async function promptReason(title: string, placeholder: string, required = false): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt(placeholder, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: placeholder,
      inputValidator: (v: string) => (required && !v?.trim() ? '请填写理由' : true)
    })
    return (value ?? '').trim()
  } catch {
    return null
  }
}

async function onPay(): Promise<void> {
  const o = order.value
  const ok = await confirmPay({
    title: '确认付款',
    headline: '确认支付这笔订单？',
    lines: [
      { label: '商品', value: o?.itemTitle ?? '本商品' },
      { label: '应付', value: `¥${o?.amount ?? '—'}`, strong: true },
      ...(o?.meetPointName ? [{ label: '面交点', value: o.meetPointName }] : [])
    ],
    note: '付款后订单进入待面交，款项由平台代管；确认收货或核销后才会结算给卖家。',
    confirmText: o?.amount ? `支付 ¥${o.amount}` : '确认支付'
  })
  if (!ok) return
  await run(() => userOrderApi.pay(orderId.value), '支付成功')
}

async function onCancel(): Promise<void> {
  const reason = await promptReason('取消订单', '请输入取消原因（选填）')
  if (reason === null) return
  await run(() => userOrderApi.cancel(orderId.value, reason || undefined), '已取消')
}

/** 已付款后的「取消」在后端就是发起退款申请，所以入口文案叫申请退款 */
async function onRefund(): Promise<void> {
  const reason = await promptReason('申请退款', '请说明退款原因，例如：商品与描述不符', true)
  if (reason === null) return
  await run(() => userOrderApi.cancel(orderId.value, reason), '退款申请已提交')
}

async function onAgree(): Promise<void> {
  try {
    await ElMessageBox.confirm('同意退款后订单将转为已退款，商品会自动恢复在售。确定同意？', '同意退款', {
      type: 'warning',
      confirmButtonText: '同意退款',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  await run(() => userOrderApi.refund(orderId.value, true), '已同意退款')
}

async function onReject(): Promise<void> {
  const note = await promptReason('拒绝退款', '请说明拒绝原因，将展示给买家，并转平台裁决', true)
  if (note === null) return
  await run(() => userOrderApi.refund(orderId.value, false, note), '已拒绝退款，订单转纠纷中')
}

async function onDispute(): Promise<void> {
  const reason = await promptReason('发起纠纷', '请说明纠纷原因，平台将介入裁决', true)
  if (reason === null) return
  await run(() => userOrderApi.dispute(orderId.value, reason), '纠纷已提交')
}

async function onVerify(): Promise<void> {
  const code = verifyCode.value.trim()
  if (!code) {
    ElMessage.warning({ message: '请输入对方出示的核销码', duration: 2000 })
    return
  }
  await run(() => userOrderApi.verify(orderId.value, code), '核销成功')
  verifyCode.value = ''
}

/** 卖家发货（仅送货上门）：待发货 → 已发货 */
async function onShip(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认已发货？买家收到后会确认收货，交易完成。', '标记已发货', {
      type: 'warning',
      confirmButtonText: '确认发货',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await run(() => userOrderApi.ship(orderId.value), '已标记发货')
}

async function onReceive(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认收货后交易完成，货款归卖家，不可再申请退款。确定收到货了？', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
      cancelButtonText: '还没有'
    })
  } catch {
    return
  }
  await run(() => userOrderApi.receive(orderId.value), '已确认收货')
}

/** 复制核销码 / 订单号：桌面端没有长按菜单，得显式给按钮 */
async function copy(text?: string, label = ''): Promise<void> {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success({ message: `${label}已复制`, duration: 1500 })
  } catch {
    ElMessage.warning({ message: '复制失败，请手动选中', duration: 2000 })
  }
}

function formatTime(iso?: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

function back(): void {
  void router.push({ name: 'orders' })
}

/** 取消原因在已退款时叫退款原因，其余叫取消原因 */
const reasonLabel = computed(() => (order.value?.status === 'REFUNDED' ? '退款原因' : '取消原因'))

onMounted(load)
</script>

<template>
  <div class="cm-shell detail">
    <button type="button" class="back" @click="back">← 返回订单列表</button>

    <div v-if="loading" v-loading="true" class="holder" />

    <p v-else-if="!order" class="empty">订单不存在，或你没有权限查看</p>

    <template v-else>
      <!-- 状态横幅 -->
      <section class="banner" :class="`banner--${order.status.toLowerCase()}`">
        <div class="banner__main">
          <h1 class="banner__t">{{ STATUS_TEXT[order.status] }}</h1>
          <p v-if="expireLeft !== null" class="banner__sub">
            {{ expireLeft > 0 ? `请在 ${expireLeft} 分钟内付款，超时自动取消` : '已超时，即将自动取消' }}
          </p>
          <p v-else-if="order.status === 'REFUNDING' && !isBuyer" class="banner__sub">
            买家已申请退款，请在 24 小时内处理，超时系统自动同意
          </p>
        </div>
        <span class="banner__role">{{ isBuyer ? '我是买家' : '我是卖家' }}</span>
      </section>

      <div class="grid">
        <div class="col">
          <!-- 商品快照：下单那一刻的标题 / 价格 / 成色，不随卖家事后改商品而变 -->
          <section class="card">
            <h2 class="card__h">商品快照</h2>
            <div class="goods">
              <div class="goods__ph">
                <img v-if="order.itemMainImage" :src="order.itemMainImage" alt="" />
                <div v-else class="goods__ph-empty">无图</div>
              </div>
              <div class="goods__bd">
                <p class="goods__tt">{{ order.itemTitle || '商品已下架' }}</p>
                <p class="goods__meta">
                  <template v-if="order.itemGrade">{{ order.itemGrade }} 级成色</template>
                  <template v-if="order.itemGrade && order.itemPrice"> · </template>
                  <template v-if="order.itemPrice">下单时标价 ¥{{ order.itemPrice }}</template>
                </p>
                <p class="goods__amount">成交价 <b>¥{{ order.amount }}</b></p>
              </div>
              <RouterLink :to="{ name: 'item', params: { id: order.itemId } }" class="goods__link">
                看当前商品 →
              </RouterLink>
            </div>
            <p class="tip">快照是纠纷取证依据：卖家事后改价改描述，不影响这里的记录。</p>
          </section>

          <!-- 交易对方：与当前视角相对的另一方，头像 + 昵称 -->
          <section class="card">
            <h2 class="card__h">交易对方</h2>
            <div class="party">
              <img v-if="peerAvatarUrl" class="party__ava" :src="peerAvatarUrl" :alt="peer.nick" />
              <span v-else class="party__ava party__ava--empty" />
              <div class="party__meta">
                <p class="party__nick">{{ peer.nick }}</p>
                <p class="party__role">
                  <span class="party__tag">{{ peer.role }}</span>
                  <span class="party__id">ID {{ peer.id }}</span>
                </p>
              </div>
            </div>
            <p class="tip">{{ order.deliveryType === 'PICKUP' ? '提货点请核对对方身份，确认无误再出示核销码。' : '面交现场请核对对方身份，确认无误再出示核销码。' }}</p>
          </section>

          <!-- 核销：卖家持码出示 / 买家输入核销（防单方核销）。面交与自提都要双方到场交接 -->
          <section v-if="needCode || order.status === 'VERIFIED'" class="card">
            <h2 class="card__h">{{ verifyTitle }}</h2>

            <!-- 卖家：持有核销码，向买家出示 -->
            <template v-if="!isBuyer && needCode">
              <div class="code">
                <span class="code__v">{{ order.verifyCode || '——' }}</span>
                <el-button size="small" @click="copy(order.verifyCode, '核销码')">复制</el-button>
              </div>
              <p class="tip">向买家出示此码，买家在你的页面输入后即可完成核销。码仅你可见，买家看不到。</p>
            </template>

            <!-- 买家：输入卖家出示的码 -->
            <template v-else-if="isBuyer && needCode">
              <div class="verify">
                <el-input
                  v-model="verifyCode"
                  class="verify__in"
                  maxlength="6"
                  placeholder="输入卖家出示的 6 位核销码"
                  @keyup.enter="onVerify"
                />
                <el-button type="primary" :loading="acting" @click="onVerify">核销</el-button>
              </div>
              <p class="tip">{{ order.deliveryType === 'PICKUP' ? '到提货点后，请当面核对卖家身份，输入卖家出示的核销码完成交接。' : '请当面核对卖家身份，输入卖家出示的核销码完成核销。' }}</p>
            </template>

            <p v-else class="tip">本单已核销，等待买家确认收货。</p>
          </section>

          <!-- 订单信息 -->
          <section class="card">
            <h2 class="card__h">订单信息</h2>
            <dl class="info">
              <div class="info__row">
                <dt>订单编号</dt>
                <dd>
                  {{ order.orderNo }}
                  <a class="info__copy" @click="copy(order.orderNo, '订单号')">复制</a>
                </dd>
              </div>
              <div class="info__row"><dt>下单时间</dt><dd>{{ formatTime(order.createTime) }}</dd></div>
              <div v-if="order.payTime" class="info__row"><dt>付款时间</dt><dd>{{ formatTime(order.payTime) }}</dd></div>
              <div v-if="order.meetTime" class="info__row"><dt>约定面交</dt><dd>{{ formatTime(order.meetTime) }}</dd></div>
              <div v-if="order.meetPointName" class="info__row">
                <dt>面交点</dt>
                <dd>
                  {{ order.meetPointName }}
                  <template v-if="order.meetPointCampus">（{{ order.meetPointCampus }}）</template>
                </dd>
              </div>
              <div v-else-if="order.meetPointId" class="info__row"><dt>面交点</dt><dd>面交点 #{{ order.meetPointId }}</dd></div>
              <div class="info__row"><dt>收货方式</dt><dd>{{ deliveryText }}</dd></div>
              <div v-if="hasAddress" class="info__row">
                <dt>{{ order.deliveryType === 'PICKUP' ? '提货地址' : '收货地址' }}</dt>
                <dd>
                  {{ order.addressDetail }}
                  <template v-if="order.addressContact">
                    （{{ order.addressContact }}<template v-if="order.addressPhone"> {{ order.addressPhone }}</template>）
                  </template>
                </dd>
              </div>
              <div v-if="order.verifyTime" class="info__row"><dt>核销时间</dt><dd>{{ formatTime(order.verifyTime) }}</dd></div>
              <div v-if="order.expireTime && order.status === 'WAIT_PAY'" class="info__row">
                <dt>付款截止</dt><dd>{{ formatTime(order.expireTime) }}</dd>
              </div>
              <div v-if="order.cancelReason" class="info__row">
                <dt>{{ reasonLabel }}</dt><dd>{{ order.cancelReason }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <!-- 操作区：状态 × 角色 -->
        <aside class="col col--side">
          <section class="card card--act">
            <h2 class="card__h">可执行操作</h2>

            <template v-if="order.status === 'WAIT_PAY'">
              <template v-if="isBuyer">
                <el-button type="primary" class="act" :loading="acting" @click="onPay">立即付款</el-button>
                <el-button class="act" :loading="acting" @click="onCancel">取消订单</el-button>
                <p class="tip">取消即作废，不产生退款单；超时未付也会自动取消。</p>
              </template>
              <p v-else class="tip">等待买家付款，超时将自动取消。</p>
            </template>

            <template v-else-if="order.status === 'WAIT_MEET'">
              <el-button v-if="isBuyer" class="act" :loading="acting" @click="onRefund">申请退款</el-button>
              <el-button class="act" :loading="acting" @click="onDispute">发起纠纷</el-button>
              <p class="tip">
                退款申请提交后由卖家处理，24 小时未处理系统自动同意；
                谈不拢可直接发起纠纷，订单会冻结等平台裁决。
              </p>
            </template>

            <template v-else-if="order.status === 'VERIFIED'">
              <template v-if="isBuyer">
                <el-button type="primary" class="act" :loading="acting" @click="onReceive">确认收货</el-button>
                <p class="tip">必须先完成{{ verifyTitle }}才能确认收货，不能跳过这一步。</p>
              </template>
              <p v-else class="tip">已核销，等待买家确认收货。</p>
            </template>

            <!-- 送货上门·待发货：卖家发货，买家可退款/纠纷 -->
            <template v-else-if="order.status === 'WAIT_SHIP'">
              <template v-if="!isBuyer">
                <el-button type="primary" class="act" :loading="acting" @click="onShip">标记已发货</el-button>
                <p class="tip">买家已选收货地址并付款，发货后由买家确认收货。</p>
              </template>
              <template v-else>
                <el-button class="act" :loading="acting" @click="onRefund">申请退款</el-button>
                <el-button class="act" :loading="acting" @click="onDispute">发起纠纷</el-button>
                <p class="tip">等待卖家发货；发货前可申请退款或发起纠纷。</p>
              </template>
            </template>

            <!-- 送货上门·已发货：买家确认收货 -->
            <template v-else-if="order.status === 'SHIPPED'">
              <template v-if="isBuyer">
                <el-button type="primary" class="act" :loading="acting" @click="onReceive">确认收货</el-button>
                <el-button class="act" :loading="acting" @click="onRefund">申请退款</el-button>
                <p class="tip">收到货且确认无误后点确认收货，交易完成。</p>
              </template>
              <p v-else class="tip">已发货，等待买家确认收货。</p>
            </template>

            <!-- 自提·待自提：双方到场交接，卖家出示码、买家核销后才能确认收货 -->
            <template v-else-if="order.status === 'WAIT_PICKUP'">
              <template v-if="isBuyer">
                <el-button class="act" :loading="acting" @click="onRefund">申请退款</el-button>
                <el-button class="act" :loading="acting" @click="onDispute">发起纠纷</el-button>
                <p class="tip">请在上方「{{ verifyTitle }}」卡片输入卖家出示的核销码，核销通过后再确认收货。</p>
              </template>
              <p v-else class="tip">等待买家到提货地址自取。请向买家出示上方核销码，由买家录入完成核销。</p>
            </template>

            <!-- 卖家处理退款：这一条就是卖家侧的退款入口 -->
            <template v-else-if="order.status === 'REFUNDING'">
              <template v-if="!isBuyer">
                <el-button type="primary" class="act" :loading="acting" @click="onAgree">同意退款</el-button>
                <el-button class="act" :loading="acting" @click="onReject">拒绝退款</el-button>
                <p class="tip">
                  同意后订单转已退款，商品自动恢复在售；
                  拒绝需填理由，订单转纠纷中并进入管理端审核队列裁决。
                </p>
              </template>
              <template v-else>
                <p class="tip">退款申请已提交，等待卖家处理（24 小时未处理将自动同意）。</p>
                <el-button class="act" :loading="acting" @click="onDispute">发起纠纷</el-button>
              </template>
            </template>

            <template v-else-if="order.status === 'DISPUTING'">
              <p class="tip">纠纷已提交，平台客服将介入裁决，请留意处理结果。裁决前双方都不能再操作。</p>
            </template>

            <template v-else>
              <p class="tip">该订单已结束，没有可执行的操作。</p>
            </template>
          </section>

          <RouterLink :to="chatTo" class="card card--link">找对方聊一聊 →</RouterLink>

          <!-- 互评：只有已完成的单才有这一块（notes/13 O18） -->
          <section v-if="order.status === 'COMPLETED'" class="card card--review">
            <h2 class="card__h">交易评价</h2>

            <template v-if="review?.mine">
              <div class="rv">
                <el-rate :model-value="review.mine.rating" disabled />
                <p v-if="review.mine.tags?.length" class="rv__tags">
                  <span v-for="t in review.mine.tags" :key="t" class="rv__tag">{{ t }}</span>
                </p>
                <p v-if="review.mine.comment" class="rv__text">{{ review.mine.comment }}</p>
                <p class="tip">评价已提交，不能修改——公开内容允许回改会变成报复手段。</p>
              </div>
            </template>
            <template v-else-if="review?.canReview">
              <el-button type="primary" class="act" @click="openReview">去评价</el-button>
              <p class="tip">面交后 30 天内可评，双方各评一条，互不影响。</p>
            </template>
            <p v-else class="tip">评价窗口已关闭。</p>

            <div v-if="review?.peer" class="rv rv--peer">
              <p class="rv__cap">对方的评价</p>
              <el-rate :model-value="review.peer.rating" disabled />
              <p v-if="review.peer.comment" class="rv__text">{{ review.peer.comment }}</p>
            </div>
          </section>
        </aside>
      </div>
    </template>

    <!-- 互评弹窗 -->
    <el-dialog v-model="reviewOpen" title="评价这笔交易" width="420px">
      <div class="rform">
        <div class="rform__row">
          <span class="rform__label">评分</span>
          <el-rate v-model="reviewForm.rating" />
        </div>
        <div v-if="tagOptions.length" class="rform__row">
          <span class="rform__label">标签（最多 3 个）</span>
          <el-checkbox-group v-model="reviewForm.tags" :max="3" class="rform__tags">
            <el-checkbox v-for="t in tagOptions" :key="t" :value="t" :label="t" />
          </el-checkbox-group>
        </div>
        <el-input
          v-model="reviewForm.comment"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="说说这次交易的感受（选填）"
        />
      </div>
      <template #footer>
        <el-button @click="reviewOpen = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
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

.holder {
  height: 320px;
}

.empty {
  margin: var(--cm-space-8) 0;
  text-align: center;
  color: var(--cm-color-text-3);
}

/* ── 状态横幅 ─────────────────────────────────────────── */

.banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  padding: var(--cm-space-5);
  border-radius: var(--cm-radius-2xl);
  color: #fff;
  background: linear-gradient(135deg, #8a8f98, #6b7078);
}

.banner--wait_pay {
  background: linear-gradient(135deg, #ff8f1f, #ff5000);
}

.banner--wait_meet,
.banner--verified,
.banner--wait_ship,
.banner--shipped,
.banner--wait_pickup {
  background: linear-gradient(135deg, #34a853, #1e8e3e);
}

.banner--refunding,
.banner--disputing {
  background: linear-gradient(135deg, #d97706, #b45309);
}

.banner--completed,
.banner--canceled,
.banner--refunded {
  background: linear-gradient(135deg, #8a8f98, #6b7078);
}

.banner__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.banner__t {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
}

.banner__sub {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  color: rgb(255 255 255 / 85%);
}

.banner__role {
  flex: none;
  padding: 4px 10px;
  font-size: var(--cm-font-size-xs);
  border: 1px solid rgb(255 255 255 / 45%);
  border-radius: var(--cm-radius-pill);
}

/* ── 两栏 ─────────────────────────────────────────────── */

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: var(--cm-space-4);
  align-items: start;
}

.col {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  min-width: 0;
}

.card {
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-2xl);
}

.card__h {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-base);
  font-weight: 700;
}

.card--act {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.card--link {
  display: block;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  text-decoration: none;

  &:hover {
    color: var(--cm-color-text-1);
    border-color: var(--cm-color-brand-border);
  }
}

.act {
  width: 100%;
  margin-left: 0 !important;
}

.tip {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.7;
  color: var(--cm-color-text-3);
}

/* ── 商品快照 ─────────────────────────────────────────── */

.goods {
  display: flex;
  align-items: center;
  gap: var(--cm-space-4);
}

.goods__ph {
  width: 96px;
  height: 96px;
  flex: none;
  border-radius: var(--cm-radius-lg);
  overflow: hidden;
  background: var(--cm-color-bg);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.goods__ph-empty {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.goods__bd {
  flex: 1;
  min-width: 0;
}

.goods__tt {
  margin: 0 0 6px;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.goods__meta {
  margin: 0 0 6px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.goods__amount {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);

  b {
    font-size: var(--cm-font-size-md);
    color: var(--cm-color-price);
  }
}

.goods__link {
  flex: none;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
  text-decoration: none;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 核销码 ───────────────────────────────────────────── */

.code {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  margin-bottom: var(--cm-space-3);
  padding: var(--cm-space-4);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-lg);
}

.code__v {
  font-family: ui-monospace, 'Cascadia Mono', Consolas, monospace;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.18em;
  color: var(--cm-color-text-1);
}

.verify {
  display: flex;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.verify__in {
  max-width: 220px;
}

/* ── 订单信息 ─────────────────────────────────────────── */

.info {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.info__row {
  display: flex;
  gap: var(--cm-space-3);
  font-size: var(--cm-font-size-sm);

  dt {
    flex: none;
    width: 76px;
    color: var(--cm-color-text-3);
  }

  dd {
    margin: 0;
    color: var(--cm-color-text-1);
    word-break: break-all;
  }
}

.info__copy {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  text-decoration: underline;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 交易对方 ─────────────────────────────────────────── */

.party {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.party__ava {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  flex: none;
  background: var(--cm-color-bg);

  &--empty {
    background: var(--cm-color-border);
  }
}

.party__meta {
  min-width: 0;
}

.party__nick {
  margin: 0 0 4px;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.party__role {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin: 0;
}

.party__tag {
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  color: #fff;
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-pill);
}

.party__id {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 交易评价 ─────────────────────────────────────────── */

.rv {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

/* 对方的评价单独一档：用浅底隔开，避免看成自己写的那段 */
.rv--peer {
  margin-top: var(--cm-space-3);
  padding-top: var(--cm-space-3);
  border-top: 1px dashed var(--cm-color-border);
}

.rv__cap {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.rv__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-1);
  margin: 0;
}

.rv__tag {
  padding: 0 6px;
  font-size: var(--cm-font-size-xs);
  line-height: 20px;
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-sm);
}

.rv__text {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.7;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

.rform {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.rform__row {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);
}

.rform__label {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.rform__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-1);
}
</style>
