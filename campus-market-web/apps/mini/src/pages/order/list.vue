<script setup lang="ts">
/**
 * 订单列表（我买到的 / 我卖出的）
 *
 * <p>role 决定查哪一边：同一个用户既是买家又是卖家，后端按 buyer_id / seller_id
 * 走两个索引（OrderController.list 的 role 参数）。tab 切换即切 role。
 *
 * <p>role 有两个来源：**入口 query**（「我的」页点「我卖出的」带 ?role=seller）与页内 tab。
 * query 必须在 onLoad 读掉——漏掉的话从「我卖出的」进来仍是买家视角，
 * 卖家的「同意退款 / 拒绝退款」在详情页就永远出不来。
 *
 * <p>状态 chips 是**前端本地过滤**：一次拉一页 50 条（演示期数据量内），
 * 避免每个 chips 一次请求；数据量大后再改服务端 status 参数（接口已支持）。
 *
 * <p>onShow 刷新：从详情页做完操作（付款/核销/退款）返回，列表要看到新状态。
 */

import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { userOrderApi, type OrderStatus, type OrderVO } from '@/api/user'

const role = ref<'buyer' | 'seller'>('buyer')
const orders = ref<OrderVO[]>([])
const loading = ref(false)
const loadError = ref(false)

const FILTERS: Array<{ label: string; match: (s: OrderStatus) => boolean }> = [
  { label: '全部', match: () => true },
  { label: '待付款', match: (s) => s === 'WAIT_PAY' },
  { label: '待面交', match: (s) => s === 'WAIT_MEET' || s === 'VERIFIED' },
  { label: '退款中', match: (s) => s === 'REFUNDING' },
  { label: '纠纷中', match: (s) => s === 'DISPUTING' },
  { label: '已完成', match: (s) => s === 'COMPLETED' || s === 'REFUNDED' || s === 'CANCELED' }
]
const filterIdx = ref(0)

const STATUS_LABEL: Record<OrderStatus, string> = {
  WAIT_PAY: '待付款',
  WAIT_MEET: '待面交',
  VERIFIED: '已验货',
  COMPLETED: '已完成',
  CANCELED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  DISPUTING: '纠纷中'
}

const shown = computed(() => orders.value.filter((o) => FILTERS[filterIdx.value].match(o.status)))

async function fetchList(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    const res = await userOrderApi.list(role.value, 1, 50)
    orders.value = res.list
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function switchRole(r: 'buyer' | 'seller'): void {
  if (role.value === r) return
  role.value = r
  filterIdx.value = 0
  void fetchList()
}

function goDetail(o: OrderVO): void {
  void uni.navigateTo({ url: `/pages/order/detail?id=${o.id}&role=${role.value}` })
}

onLoad((query) => {
  const q = query as { role?: string }
  if (q.role === 'seller') role.value = 'seller'
})

onShow(() => {
  void fetchList()
})
</script>

<template>
  <view class="page">
    <!-- 角色 tab：买 / 卖 -->
    <view class="tabs">
      <view class="tab" :class="{ 'tab--on': role === 'buyer' }" @click="switchRole('buyer')">我买到的</view>
      <view class="tab" :class="{ 'tab--on': role === 'seller' }" @click="switchRole('seller')">我卖出的</view>
    </view>

    <!-- 状态 chips -->
    <scroll-view class="chips" scroll-x>
      <view class="chips__in">
        <text
          v-for="(f, i) in FILTERS"
          :key="f.label"
          class="chip"
          :class="{ 'chip--on': filterIdx === i }"
          @click="filterIdx = i"
        >
          {{ f.label }}
        </text>
      </view>
    </scroll-view>

    <view v-if="loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="loadError" class="state"><text class="state__t">加载失败，退出重进试试</text></view>
    <view v-else-if="!shown.length" class="state"><text class="state__t">没有相关订单</text></view>

    <view v-else class="list">
      <view v-for="o in shown" :key="o.id" class="card" hover-class="card--active" @click="goDetail(o)">
        <image v-if="o.itemMainImage" class="card__pic" :src="o.itemMainImage" mode="aspectFill" />
        <view v-else class="card__pic card__pic--empty" />
        <view class="card__body">
          <text class="card__title">{{ o.itemTitle ?? '商品已下架' }}</text>
          <text class="card__no">订单号 {{ o.orderNo }}</text>
          <view class="card__row">
            <text class="card__amount">¥{{ o.amount }}</text>
            <text class="card__status" :class="`st st--${o.status.toLowerCase()}`">{{ STATUS_LABEL[o.status] }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: $cm-color-bg;
}

.tabs {
  display: flex;
  background: $cm-color-surface;
  border-bottom: 1rpx solid $cm-color-border;
}

.tab {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
}

.tab--on {
  color: $cm-color-brand-text;
  font-weight: 600;
  border-bottom: 4rpx solid $cm-color-brand;
}

.chips {
  padding: $cm-space-3 0;
  background: $cm-color-bg;
  white-space: nowrap;
}

.chips__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 0 $cm-space-3;
}

.chip {
  padding: 0 $cm-space-4;
  height: 52rpx;
  line-height: 52rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border-radius: 26rpx;
}

.chip--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border: 1rpx solid $cm-color-brand;
}

.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
  padding: 0 $cm-space-3 $cm-space-4;
}

.card {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  transition: transform 120ms ease-out;
}

.card--active {
  transform: scale(0.98);
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
  color: $cm-color-text-1;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__no {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.card__amount {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-price;
}

.card__status {
  font-size: $cm-font-size-xs;
  padding: 4rpx $cm-space-2;
  border-radius: 8rpx;
  background: $cm-color-bg;
  color: $cm-color-text-2;
}

.st--wait_pay {
  color: $cm-color-price;
  background: #fff1f0;
}

.st--wait_meet,
.st--verified {
  color: $cm-color-ok;
  background: #f0faf4;
}

.st--refunding,
.st--disputing {
  color: #d46b08;
  background: #fff7e6;
}

.state {
  padding: 160rpx $cm-space-6;
  text-align: center;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>
