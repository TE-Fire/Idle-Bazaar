<script setup lang="ts">
/**
 * 我的交易（合并「我发布的 / 我买到的 / 我卖出的」）
 *
 * <p>原先这三项散在「我的」页三个入口，分别跳「我的商品」与「订单」两个页，
 * 同一件已售商品既出现在「我发布的」又出现在「我卖出的」订单里（重复内容），
 * 且两套 tab 样式不统一。这里合并成一个带三个顶部 tab 的页：切换统一、样式统一。
 *
 * <p>去重：卖家视角的「我发布的」默认只出在售 / 草稿 / 下架等库存类状态，
 * 已售（SOLD）不再出现——已售商品以订单形态归到「我卖出的」，避免同一商品两边重复。
 *
 * <p>role 由顶部 tab 决定（tab=1 买家 / tab=2 卖家），与后端 OrderController.list 的
 * role 参数一致；缺了它卖家看不到「同意退款 / 拒绝退款」（见 notes/13）。
 *
 * <p>状态过滤为前端本地过滤：一次拉 50 条（演示期数据量内），数据量大后再改服务端
 * status 参数（接口已支持）。onShow 刷新：从详情 / 编辑页返回要看到最新状态。
 */

import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { userItemApi, userOrderApi, type ApiItemVO, type OrderStatus, type OrderVO } from '@/api/user'
import CmNav from '@/components/CmNav.vue'

const TABS = ['我发布的', '我买到的', '我卖出的']
const activeTab = ref(0)
/** 三类数量徽标：挂载时一次性拉取（published 去已售，与列表口径一致） */
const counts = ref<number[]>([0, 0, 0])

const loading = ref(false)
const loadError = ref(false)

/* ── 我发布的：库存类状态，已售不在此列（去重） ── */
const ITEM_TABS = [
  { label: '全部', status: '' },
  { label: '草稿', status: 'DRAFT' },
  { label: '待审核', status: 'PENDING' },
  { label: '在售', status: 'ON_SALE' },
  { label: '已下架', status: 'OFFLINE' },
  { label: '审核未过', status: 'REJECTED' }
]
const ITEM_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  ON_SALE: '在售',
  SOLD: '已售出',
  OFFLINE: '已下架',
  REJECTED: '审核未过',
  FORCED_OFFLINE: '已强制下架'
}
const itemStatus = ref('')

/* ── 我买到的 / 我卖出的：订单状态过滤 ── */
const ORDER_FILTERS: Array<{ label: string; match: (s: OrderStatus) => boolean }> = [
  { label: '全部', match: () => true },
  { label: '待付款', match: (s) => s === 'WAIT_PAY' },
  { label: '待面交', match: (s) => s === 'WAIT_MEET' || s === 'VERIFIED' },
  { label: '退款中', match: (s) => s === 'REFUNDING' },
  { label: '纠纷中', match: (s) => s === 'DISPUTING' },
  { label: '已完成', match: (s) => s === 'COMPLETED' || s === 'REFUNDED' || s === 'CANCELED' }
]
const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  WAIT_PAY: '待付款',
  WAIT_MEET: '待面交',
  VERIFIED: '已验货',
  COMPLETED: '已完成',
  CANCELED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  DISPUTING: '纠纷中'
}
const orderFilterIdx = ref(0)

const role = computed<'buyer' | 'seller'>(() => (activeTab.value === 2 ? 'seller' : 'buyer'))

const items = ref<ApiItemVO[]>([])
const orders = ref<OrderVO[]>([])
const shownOrders = computed(() => orders.value.filter((o) => ORDER_FILTERS[orderFilterIdx.value].match(o.status)))

/* ── 状态筛选弹层：横向胶囊被 flex 挤压只剩一字，改为触发条 + 底部弹层（完整标签） ── */
const filterOpen = ref(false)
const filterOptions = computed<string[]>(() =>
  activeTab.value === 0 ? ITEM_TABS.map((t) => t.label) : ORDER_FILTERS.map((f) => f.label)
)
const currentFilterIdx = computed<number>(() =>
  activeTab.value === 0 ? ITEM_TABS.findIndex((t) => t.status === itemStatus.value) : orderFilterIdx.value
)
const currentFilterLabel = computed<string>(() => filterOptions.value[currentFilterIdx.value] ?? '全部')

function selectFilter(i: number): void {
  filterOpen.value = false
  if (activeTab.value === 0) pickItemStatus(ITEM_TABS[i].status)
  else orderFilterIdx.value = i
}

async function loadItems(): Promise<void> {
  const page = await userItemApi.mine(itemStatus.value)
  let list = page.list
  if (!itemStatus.value) list = list.filter((i) => i.status !== 'SOLD')
  items.value = list
}

async function loadOrders(): Promise<void> {
  const res = await userOrderApi.list(role.value, 1, 50)
  orders.value = res.list
}

async function loadActive(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    if (activeTab.value === 0) await loadItems()
    else await loadOrders()
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function switchTab(i: number): void {
  if (i === activeTab.value) return
  activeTab.value = i
  if (i !== 0) orderFilterIdx.value = 0
  void loadActive()
}

function pickItemStatus(s: string): void {
  if (s === itemStatus.value) return
  itemStatus.value = s
  void loadItems()
}

/* ── 我发布的：商品操作（与 my-items 同口径） ── */
function editItem(id: string): void {
  void uni.navigateTo({ url: `/pages/publish/index?edit=${id}` })
}

function removeItem(item: ApiItemVO): void {
  uni.showModal({
    title: '删除商品',
    content: `删除后不可恢复，「${item.title}」将不再展示。确定删除？`,
    confirmText: '删除',
    confirmColor: '#FF6A00',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await userItemApi.remove(item.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        void loadItems()
      } catch {
        // request 层已提示（在售 / 已售出不可删）
      }
    }
  })
}

function offlineItem(item: ApiItemVO): void {
  uni.showModal({
    title: '下架商品',
    content: `下架后停止展示，可重新编辑上架。确定下架「${item.title}」？`,
    confirmText: '下架',
    confirmColor: '#FF6A00',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await userItemApi.offline(item.id)
        uni.showToast({ title: '已下架', icon: 'success' })
        void loadItems()
      } catch {
        // request 层已提示
      }
    }
  })
}

async function submitDraft(item: ApiItemVO): Promise<void> {
  try {
    const detail = await userItemApi.detail(item.id)
    await userItemApi.edit(item.id, {
      title: detail.title,
      categoryId: detail.categoryId,
      grade: detail.grade,
      price: Number(detail.price),
      originPrice: detail.originPrice ? Number(detail.originPrice) : undefined,
      campusId: detail.campusId,
      meetPointId: detail.meetPointId,
      description: detail.description,
      images: (detail.images ?? []).map((i) => i.url),
      draft: false
    })
    uni.showToast({ title: '已提交审核', icon: 'success' })
    void loadItems()
  } catch {
    // request 层已提示
  }
}

function goOrder(o: OrderVO): void {
  void uni.navigateTo({ url: `/pages/order/detail?id=${o.id}&role=${role.value}` })
}

onLoad((query) => {
  const q = query as { tab?: string }
  const t = Number(q.tab)
  if (t >= 0 && t <= 2) activeTab.value = t
  void loadCounts()
})

async function loadCounts(): Promise<void> {
  try {
    const [mine, bought, sold] = await Promise.all([
      userItemApi.mine('', 1, 50),
      userOrderApi.list('buyer', 1, 1),
      userOrderApi.list('seller', 1, 1)
    ])
    const pub = mine.list.filter((i) => i.status !== 'SOLD').length
    counts.value = [pub, bought.total, sold.total]
  } catch {
    // 计数失败不阻塞主流程
  }
}

onShow(() => {
  void loadActive()
})
</script>

<template>
  <view class="page">
    <CmNav title="我的交易" />

    <!-- 顶部三个 tab：分段滑动控件 + 数量徽标 -->
    <view class="seg">
      <view class="seg__bar" :style="{ transform: `translateX(${activeTab * 100}%)` }" />
      <view
        v-for="(t, i) in TABS"
        :key="t"
        class="seg__item"
        :class="{ 'seg__item--on': activeTab === i }"
        @click="switchTab(i)"
      >
        <text class="seg__t">{{ t }}</text>
        <text class="seg__n">{{ counts[i] }}</text>
      </view>
    </view>

    <view v-if="loading" class="state"><text class="state__t">加载中…</text></view>
    <view v-else-if="loadError" class="state"><text class="state__t">加载失败，退出重进试试</text></view>

    <!-- 我发布的 -->
    <block v-else-if="activeTab === 0">
      <view class="filter" hover-class="filter--active" @click="filterOpen = true">
        <text class="filter__k">状态</text>
        <text class="filter__v">{{ currentFilterLabel }} ▾</text>
      </view>

      <view v-if="items.length === 0" class="state"><text class="state__t">这个状态下还没有商品</text></view>

      <view v-else class="list">
        <view v-for="item in items" :key="item.id" class="card">
          <view class="card__main" @click="editItem(item.id)">
            <image v-if="item.mainImage" class="card__img" :src="item.mainImage" mode="aspectFill" />
            <view v-else class="card__img card__img--empty">无图</view>
            <view class="card__meta">
              <text class="card__tt">{{ item.title }}</text>
              <text class="card__pr">¥{{ item.price }}</text>
              <text class="card__sub">{{ ITEM_STATUS_LABEL[item.status] }} · {{ item.campusName }}</text>
            </view>
          </view>
          <view class="card__acts">
            <template v-if="item.status === 'DRAFT'">
              <text class="act" @click="editItem(item.id)">继续编辑</text>
              <text class="act act--pri" @click="submitDraft(item)">提交送审</text>
              <text class="act act--danger" @click="removeItem(item)">删除</text>
            </template>
            <template v-else-if="item.status === 'ON_SALE'">
              <text class="act" @click="editItem(item.id)">编辑</text>
              <text class="act act--danger" @click="offlineItem(item)">下架</text>
            </template>
            <template v-else-if="item.status === 'OFFLINE' || item.status === 'REJECTED'">
              <text class="act" @click="editItem(item.id)">编辑</text>
              <text class="act act--danger" @click="removeItem(item)">删除</text>
            </template>
            <text v-else class="card__none">暂无操作</text>
          </view>
        </view>
      </view>
    </block>

    <!-- 我买到的 / 我卖出的 -->
    <block v-else>
      <view class="filter" hover-class="filter--active" @click="filterOpen = true">
        <text class="filter__k">状态</text>
        <text class="filter__v">{{ currentFilterLabel }} ▾</text>
      </view>

      <view v-if="shownOrders.length === 0" class="state"><text class="state__t">没有相关订单</text></view>

      <view v-else class="list">
        <view v-for="o in shownOrders" :key="o.id" class="card" hover-class="card--active" @click="goOrder(o)">
          <image v-if="o.itemMainImage" class="card__img" :src="o.itemMainImage" mode="aspectFill" />
          <view v-else class="card__img card__img--empty" />
          <view class="card__body">
            <text class="card__tt">{{ o.itemTitle ?? '商品已下架' }}</text>
            <text class="card__no">订单号 {{ o.orderNo }}</text>
            <view class="card__row">
              <text class="card__amount">¥{{ o.amount }}</text>
              <text class="card__status" :class="`st st--${o.status.toLowerCase()}`">{{ ORDER_STATUS_LABEL[o.status] }}</text>
            </view>
          </view>
        </view>
      </view>
    </block>

    <!-- 状态筛选弹层：底部上滑，完整标签，选中高亮 -->
    <view v-if="filterOpen" class="mask" @click="filterOpen = false">
      <view class="sheet" @click.stop>
        <view class="sheet__head">
          <text class="sheet__title">选择状态</text>
          <text class="sheet__close" @click="filterOpen = false">✕</text>
        </view>
        <scroll-view scroll-y class="sheet__body">
          <view
            v-for="(opt, i) in filterOptions"
            :key="opt"
            class="opt"
            :class="{ 'opt--on': i === currentFilterIdx }"
            hover-class="opt--active"
            @click="selectFilter(i)"
          >
            <text class="opt__t">{{ opt }}</text>
            <text v-if="i === currentFilterIdx" class="opt__check">✓</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  background: $cm-color-bg;
}

/* ── 顶部三 tab：分段滑动控件（Segmented）+ 数量徽标 ── */
.seg {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  margin: $cm-space-3;
  height: 72rpx;
  padding: 4rpx;
  background: #efeff2;
  border-radius: 36rpx;
}

.seg__bar {
  position: absolute;
  top: 4rpx;
  bottom: 4rpx;
  left: 4rpx;
  width: calc((100% - 8rpx) / 3);
  border-radius: 32rpx;
  background: $cm-color-brand;
  box-shadow: 0 4rpx 12rpx rgba(255, 106, 0, 0.28);
  transform: translateX(0);
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.seg__item {
  position: relative;
  z-index: 1;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}

.seg__t {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-2;
  transition: color 0.2s ease;
}

.seg__n {
  font-size: 20rpx;
  font-weight: 600;
  color: $cm-color-text-3;
  transition: color 0.2s ease;
}

.seg__item--on .seg__t {
  color: #fff;
}

.seg__item--on .seg__n {
  color: rgba(255, 255, 255, 0.92);
}

/* ── 状态筛选触发条：单行，点开弹层 ── */
.filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80rpx;
  margin: $cm-space-2 $cm-space-3;
  padding: 0 $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-md;
}

.filter--active {
  background: $cm-color-bg;
}

.filter__k {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.filter__v {
  font-size: $cm-font-size-sm;
  font-weight: 700;
  color: $cm-color-brand;
}

/* ── 状态筛选弹层：底部上滑 ── */
.mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  background: $cm-color-surface;
  border-radius: 24rpx 24rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
  animation: sheet-up 0.22s ease-out;
}

@keyframes sheet-up {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $cm-space-4;
  border-bottom: 1rpx solid $cm-color-border;
}

.sheet__title {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-text-1;
}

.sheet__close {
  font-size: $cm-font-size-base;
  color: $cm-color-text-3;
}

.sheet__body {
  max-height: 60vh;
}

.opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 96rpx;
  padding: 0 $cm-space-4;
  border-bottom: 1rpx solid $cm-color-border;
}

.opt--active {
  background: $cm-color-bg;
}

.opt__t {
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.opt--on .opt__t {
  color: $cm-color-brand;
  font-weight: 700;
}

.opt__check {
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-brand;
}

/* ── 列表 ── */
.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
  padding: 0 $cm-space-3 $cm-space-4;
}

.card {
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  overflow: hidden;
  transition: transform 120ms ease-out;
}

.card--active {
  transform: scale(0.98);
}

.card__main {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3;
}

.card__img {
  width: 160rpx;
  height: 160rpx;
  flex: none;
  border-radius: $cm-radius-md;
  background: $cm-color-bg;
}

.card__img--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.card__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card__tt {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card__pr {
  font-size: $cm-font-size-md;
  font-weight: 800;
  color: $cm-color-price;
}

.card__sub {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.card__acts {
  display: flex;
  gap: $cm-space-5;
  padding: $cm-space-2 $cm-space-3;
  border-top: 1rpx solid $cm-color-border;
}

.act {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.act--pri {
  font-weight: 700;
  color: $cm-color-price;
}

.act--danger {
  color: $cm-color-text-3;
}

.card__none {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
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

.st--completed,
.st--refunded,
.st--canceled {
  color: $cm-color-text-3;
  background: $cm-color-bg;
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
