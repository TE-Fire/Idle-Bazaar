<script setup lang="ts">
/**
 * 订单详情（状态机动作都落在这一页，矩阵见 notes/13 第七节）
 *
 * <p>动作按「状态 × 角色」给出，规则：
 * - 待付款：买家付款 / 取消（取消即作废，不产生退款单）；
 * - 待面交：任一方出示/输入核销码完成核销 → 已验货；买家可申请退款（卖家不处理则 24h 自动同意）；
 * - 已验货：买家确认收货 → 已完成（必须先核销，不能跳过面交）；
 * - 退款中：卖家同意（已退款，商品恢复在售）/ 拒绝（转纠纷中，管理端裁决）；
 * - 纠纷中与终态：只读，等平台处理。
 *
 * <p>退款 / 纠纷要填理由：用可输入弹窗收，不另起页面（一条理由不值得一页）。
 */

import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { userOrderApi, type OrderStatus, type OrderVO } from '@/api/user'

const id = ref('')
const role = ref<'buyer' | 'seller'>('buyer')
const order = ref<OrderVO | null>(null)
const loading = ref(false)

const isBuyer = computed(() => role.value === 'buyer')

const STATUS_TEXT: Record<OrderStatus, string> = {
  WAIT_PAY: '等待买家付款',
  WAIT_MEET: '已付款，等待面交核销',
  VERIFIED: '已验货，等待买家确认收货',
  COMPLETED: '交易完成',
  CANCELED: '订单已取消',
  REFUNDING: '退款申请处理中',
  REFUNDED: '已退款',
  DISPUTING: '纠纷处理中，平台将介入裁决'
}

/** 待付款剩余时间（分钟）：expire_time - now，负数表示已被超时取消任务扫掉 */
const expireLeft = computed(() => {
  if (!order.value || order.value.status !== 'WAIT_PAY' || !order.value.expireTime) return null
  const ms = new Date(order.value.expireTime.replace(' ', 'T')).getTime() - Date.now()
  return Math.max(0, Math.ceil(ms / 60000))
})

async function fetchDetail(): Promise<void> {
  loading.value = true
  try {
    order.value = await userOrderApi.detail(id.value)
  } catch {
    uni.showToast({ title: '订单加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 带理由的确认弹窗（取消 / 申请退款 / 拒绝退款 / 发起纠纷共用） */
function promptReason(title: string, placeholder: string): Promise<string | null> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      editable: true,
      placeholderText: placeholder,
      confirmText: '确定',
      cancelText: '取消',
      success: (r) => {
        if (r.confirm) resolve((r.content ?? '').trim() || '买家未填写理由')
        else resolve(null)
      },
      fail: () => resolve(null)
    })
  })
}

function run(action: () => Promise<unknown>, ok: string): void {
  uni.showLoading({ title: '处理中…', mask: true })
  action()
    .then(() => {
      uni.hideLoading()
      uni.showToast({ title: ok, icon: 'success' })
      void fetchDetail()
    })
    .catch(() => uni.hideLoading())
}

async function onPay(): Promise<void> {
  await userOrderApi.pay(id.value)
}

async function onCancel(): Promise<void> {
  const reason = await promptReason('取消订单', '请输入取消原因（选填）')
  if (reason === null) return
  await userOrderApi.cancel(id.value, reason)
}

async function onRefund(): Promise<void> {
  const reason = await promptReason('申请退款', '请说明退款原因，例如：商品与描述不符')
  if (reason === null) return
  await userOrderApi.cancel(id.value, reason)
}

async function onAgree(): Promise<void> {
  await userOrderApi.refund(id.value, true)
}

async function onReject(): Promise<void> {
  const reason = await promptReason('拒绝退款', '请说明拒绝原因，将展示给买家')
  if (reason === null) return
  await userOrderApi.refund(id.value, false, reason)
}

async function onDispute(): Promise<void> {
  const reason = await promptReason('发起纠纷', '请说明纠纷原因，平台将介入裁决')
  if (reason === null) return
  await userOrderApi.dispute(id.value, reason)
}

function onVerify(): void {
  uni.showModal({
    title: '扫码核销',
    editable: true,
    placeholderText: '输入对方出示的 6 位核销码',
    confirmText: '核销',
    success: (r) => {
      if (!r.confirm) return
      const code = (r.content ?? '').trim()
      if (!code) {
        uni.showToast({ title: '核销码不能为空', icon: 'none' })
        return
      }
      run(() => userOrderApi.verify(id.value, code), '核销成功')
    }
  })
}

async function onReceive(): Promise<void> {
  await userOrderApi.receive(id.value)
}

/** 评价入口：评价状态由评价页自己拉（能不能评、评没评都在那边判） */
function goReview(): void {
  void uni.navigateTo({ url: `/pages/review/index?orderId=${id.value}` })
}

onLoad((query) => {
  const q = query as { id?: string; role?: string }
  id.value = q.id ?? ''
  role.value = q.role === 'seller' ? 'seller' : 'buyer'
})

onShow(() => {
  if (id.value) void fetchDetail()
})
</script>

<template>
  <view v-if="order" class="page">
    <!-- 状态横幅 -->
    <view class="banner" :class="`banner--${order.status.toLowerCase()}`">
      <text class="banner__t">{{ STATUS_TEXT[order.status] }}</text>
      <text v-if="expireLeft !== null" class="banner__sub">
        {{ expireLeft > 0 ? `请在 ${expireLeft} 分钟内付款，超时自动取消` : '已超时，即将自动取消' }}
      </text>
    </view>

    <!-- 商品 -->
    <view class="card">
      <image v-if="order.itemMainImage" class="card__pic" :src="order.itemMainImage" mode="aspectFill" />
      <view v-else class="card__pic card__pic--empty" />
      <view class="card__body">
        <text class="card__title">{{ order.itemTitle ?? '商品已下架' }}</text>
        <text class="card__grade">{{ order.itemGrade ? `${order.itemGrade} 级成色` : '' }}</text>
        <text class="card__amount">¥{{ order.amount }}</text>
      </view>
    </view>

    <!-- 核销码：付款后生成，面交时一方出示一方扫描 -->
    <view v-if="order.status === 'WAIT_MEET' || order.status === 'VERIFIED'" class="code">
      <text class="code__label">面交核销码</text>
      <text class="code__value">{{ order.verifyCode }}</text>
      <text class="code__hint">面交时向对方出示此码，或输入对方提供的码完成核销</text>
    </view>

    <!-- 订单信息 -->
    <view class="info">
      <view class="info__row"><text class="info__k">订单编号</text><text class="info__v">{{ order.orderNo }}</text></view>
      <view class="info__row"><text class="info__k">创建时间</text><text class="info__v">{{ order.createTime }}</text></view>
      <view v-if="order.payTime" class="info__row"><text class="info__k">付款时间</text><text class="info__v">{{ order.payTime }}</text></view>
      <view v-if="order.meetTime" class="info__row"><text class="info__k">约定面交</text><text class="info__v">{{ order.meetTime }}</text></view>
      <view v-if="order.cancelReason" class="info__row"><text class="info__k">{{ order.status === 'REFUNDED' ? '退款原因' : '取消原因' }}</text><text class="info__v">{{ order.cancelReason }}</text></view>
    </view>

    <!-- 操作区：状态 × 角色 -->
    <view class="actions">
      <template v-if="order.status === 'WAIT_PAY'">
        <view v-if="isBuyer" class="btn btn--main" hover-class="btn--active" @click="run(onPay, '支付成功')">立即付款</view>
        <view v-if="isBuyer" class="btn btn--ghost" hover-class="btn--active" @click="run(onCancel, '已取消')">取消订单</view>
        <view v-else class="hint">等待买家付款，超时将自动取消</view>
      </template>

      <template v-else-if="order.status === 'WAIT_MEET'">
        <view class="btn btn--main" hover-class="btn--active" @click="onVerify">扫码核销</view>
        <view v-if="isBuyer" class="btn btn--ghost" hover-class="btn--active" @click="run(onRefund, '退款申请已提交')">申请退款</view>
        <view class="btn btn--ghost" hover-class="btn--active" @click="run(onDispute, '纠纷已提交')">发起纠纷</view>
      </template>

      <template v-else-if="order.status === 'VERIFIED'">
        <view v-if="isBuyer" class="btn btn--main" hover-class="btn--active" @click="run(onReceive, '已确认收货')">确认收货</view>
        <view v-else class="hint">已核销，等待买家确认收货</view>
      </template>

      <template v-else-if="order.status === 'REFUNDING'">
        <template v-if="!isBuyer">
          <view class="btn btn--main" hover-class="btn--active" @click="run(onAgree, '已同意退款')">同意退款</view>
          <view class="btn btn--ghost" hover-class="btn--active" @click="run(onReject, '已拒绝退款')">拒绝退款</view>
        </template>
        <view v-else class="hint">退款申请已提交，等待卖家处理（24 小时未处理将自动同意）</view>
        <view class="btn btn--ghost" hover-class="btn--active" @click="run(onDispute, '纠纷已提交')">发起纠纷</view>
      </template>

      <template v-else-if="order.status === 'DISPUTING'">
        <view class="hint">纠纷已提交，平台客服将介入裁决，请留意处理结果</view>
      </template>

      <!-- 已完成才给评价入口：待面交/已验货/退款中都还没走完交易，评人没有依据 -->
      <template v-else-if="order.status === 'COMPLETED'">
        <view class="btn btn--main" hover-class="btn--active" @click="goReview">评价这笔交易</view>
        <view class="hint">面交后 30 天内可评，一单一评，提交后不能修改</view>
      </template>

      <template v-else>
        <view class="hint">该订单已结束</view>
      </template>
    </view>
  </view>

  <view v-else class="state">
    <text class="state__t">{{ loading ? '加载中…' : '订单不存在' }}</text>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: $cm-color-bg;
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.banner {
  padding: $cm-space-6 $cm-space-4;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.banner__t {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: #fff;
}

.banner__sub {
  font-size: $cm-font-size-xs;
  color: rgb(255 255 255 / 80%);
}

.banner--wait_pay {
  background: linear-gradient(135deg, #ff8f1f, #ff5000);
}

.banner--wait_meet,
.banner--verified {
  background: linear-gradient(135deg, #34a853, #1e8e3e);
}

.banner--refunding,
.banner--disputing {
  background: linear-gradient(135deg, #d97706, #b45309);
}

.banner--completed {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
}

.banner--cancelled,
.banner--refunded {
  background: linear-gradient(135deg, #94a3b8, #64748b);
}

.card {
  display: flex;
  gap: $cm-space-3;
  margin: 0 $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.card__pic {
  width: 160rpx;
  height: 160rpx;
  border-radius: $cm-radius-md;
  flex: none;
  background: $cm-color-bg;
}

.card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.card__title {
  font-size: $cm-font-size-base;
  font-weight: 500;
  color: $cm-color-text-1;
}

.card__grade {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.card__amount {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-price;
  margin-top: auto;
}

.code {
  margin: $cm-space-3 $cm-space-3 0;
  padding: $cm-space-5 $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $cm-space-2;
}

.code__label {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.code__value {
  font-size: 56rpx;
  font-weight: 700;
  letter-spacing: 12rpx;
  color: $cm-color-text-1;
  font-family: 'DIN Alternate', 'SF Mono', monospace;
}

.code__hint {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-align: center;
}

.info {
  margin: $cm-space-3 $cm-space-3 0;
  padding: $cm-space-3 $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
}

.info__row {
  display: flex;
  justify-content: space-between;
  gap: $cm-space-4;
}

.info__k {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  flex: none;
}

.info__v {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  text-align: right;
  word-break: break-all;
}

.actions {
  margin: $cm-space-4 $cm-space-3 0;
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.btn {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  border-radius: $cm-radius-lg;
  font-size: $cm-font-size-base;
  font-weight: 600;
  transition: transform 120ms ease-out;
}

.btn--active {
  transform: scale(0.98);
}

.btn--main {
  background: $cm-color-brand;
  color: #fff;
}

.btn--ghost {
  background: $cm-color-surface;
  color: $cm-color-text-1;
  border: 1rpx solid $cm-color-border;
}

.hint {
  padding: $cm-space-3 $cm-space-4;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
  text-align: center;
}

.state {
  padding: 200rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>
