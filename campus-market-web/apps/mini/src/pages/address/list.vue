<script setup lang="ts">
/**
 * 地址簿（收货 SHIP / 自提 PICKUP 两个分栏）
 *
 * <p>两种用法：
 * ① 管理入口（「我的 → 地址簿」）：两栏都能看、能增删改、能设默认；
 * ② 选择入口（下单选收货地址、发布选自提点）：带 `?pick=1` 进来，只显示目标栏，
 *    点一行就把选中的地址回传给上一页——回传走 `uni.$emit`，不用 eventChannel：
 *    H5 与小程序的 eventChannel 行为有差异，全局事件两边一致，且不依赖页面栈。
 *
 * <p>删除只动地址簿：订单里的地址是下单那一刻的快照，删了不影响历史订单。
 */

import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ADDRESS_KIND, addressApi, addressText, type AddressVO } from '@/api/address'
import CmNav from '@/components/CmNav.vue'
import CmDialogHost from '@/components/CmDialogHost.vue'
import CmIcon from '@/components/CmIcon.vue'
import { cmConfirm } from '@/utils/dialog'

const kind = ref<number>(ADDRESS_KIND.ship)
/** 选择模式：点一行即回传并返回 */
const pickMode = ref(false)
const list = ref<AddressVO[]>([])
const loading = ref(false)

onLoad((query) => {
  pickMode.value = query?.pick === '1'
  kind.value = Number(query?.kind) === ADDRESS_KIND.pickup ? ADDRESS_KIND.pickup : ADDRESS_KIND.ship
})

/** onShow 而非 onLoad：从编辑页返回要看到最新一条 */
onShow(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = await addressApi.list(kind.value)
  } catch {
    // request 层已提示
  } finally {
    loading.value = false
  }
}

function switchKind(k: number): void {
  if (kind.value === k) return
  kind.value = k
  void load()
}

function onTap(a: AddressVO): void {
  if (!pickMode.value) {
    goEdit(a)
    return
  }
  uni.$emit('cm:address-picked', a)
  void uni.navigateBack()
}

function goCreate(): void {
  void uni.navigateTo({ url: `/pages/address/edit?kind=${kind.value}` })
}

function goEdit(a: AddressVO): void {
  void uni.navigateTo({ url: `/pages/address/edit?kind=${a.kind}&id=${a.id}` })
}

async function setDefault(a: AddressVO): Promise<void> {
  if (a.isDefault === 1) return
  try {
    await addressApi.setDefault(a.id)
    uni.showToast({ title: '已设为默认', icon: 'success' })
    void load()
  } catch {
    // request 层已提示
  }
}

async function remove(a: AddressVO): Promise<void> {
  const ok = await cmConfirm({
    title: '删除地址',
    lines: [
      { label: '联系人', value: `${a.contactName} ${a.contactPhone}` },
      { label: '地址', value: addressText(a) }
    ],
    note: '只删除地址簿记录，历史订单里的地址快照不受影响。',
    danger: true,
    confirmText: '删除'
  })
  if (!ok) return
  try {
    await addressApi.remove(a.id)
    void load()
  } catch {
    // request 层已提示
  }
}
</script>

<template>
  <view class="page">
    <CmNav :title="pickMode ? '选择地址' : '地址簿'" />

    <!-- 选择模式不切栏：下单只该看到收货地址、发布只该看到提货地址 -->
    <view v-if="!pickMode" class="tabs">
      <view
        class="tab"
        :class="{ 'tab--on': kind === ADDRESS_KIND.ship }"
        @click="switchKind(ADDRESS_KIND.ship)"
      >
        <text class="tab__t">收货地址</text>
      </view>
      <view
        class="tab"
        :class="{ 'tab--on': kind === ADDRESS_KIND.pickup }"
        @click="switchKind(ADDRESS_KIND.pickup)"
      >
        <text class="tab__t">提货地址</text>
      </view>
    </view>

    <text class="hint">
      {{
        kind === ADDRESS_KIND.pickup
          ? '自提商品的提货点，发布时从中选一个；买家看不到你的其他地址'
          : '送货上门订单的收货地址，下单时从中选一个；卖家看不到'
      }}
    </text>

    <view v-if="loading && !list.length" class="empty">加载中…</view>

    <view v-else-if="!list.length" class="empty">
      <CmIcon name="location" tone="muted" size="lg" />
      <text class="empty__t">{{ kind === ADDRESS_KIND.pickup ? '还没有提货地址' : '还没有收货地址' }}</text>
      <text class="empty__h">添加后可在此一键设为默认，下单/发布时自动带出</text>
    </view>

    <view v-else class="list">
      <view
        v-for="a in list"
        :key="a.id"
        class="addr"
        hover-class="addr--on"
        @click="onTap(a)"
      >
        <view class="addr__top">
          <text class="addr__name">{{ a.contactName }}</text>
          <text class="addr__phone">{{ a.contactPhone }}</text>
          <text v-if="a.isDefault === 1" class="addr__def">默认</text>
        </view>
        <text class="addr__txt">{{ addressText(a) }}</text>

        <view v-if="!pickMode" class="addr__ops">
          <text v-if="a.isDefault !== 1" class="addr__op" @click.stop="setDefault(a)">设为默认</text>
          <text class="addr__op" @click.stop="goEdit(a)">编辑</text>
          <text class="addr__op addr__op--danger" @click.stop="remove(a)">删除</text>
        </view>
      </view>
    </view>

    <view class="dock">
      <view class="dock__btn" hover-class="dock__btn--on" @click="goCreate">
        <text class="dock__plus">＋</text>
        <text class="dock__t">新增{{ kind === ADDRESS_KIND.pickup ? '提货' : '收货' }}地址</text>
      </view>
    </view>

    <CmDialogHost />
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-4 $cm-space-4 200rpx;
}

.tabs {
  display: flex;
  gap: $cm-space-2;
  padding: 6rpx;
  margin-bottom: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
}

.tab {
  flex: 1;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $cm-radius-pill;
  transition: background-color 160ms ease-out;
}

.tab--on {
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
}

.tab__t {
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
}

.tab--on .tab__t {
  color: #fff;
  font-weight: 600;
}

.hint {
  display: block;
  margin: 0 $cm-space-2 $cm-space-4;
  font-size: $cm-font-size-xs;
  line-height: 1.6;
  color: $cm-color-text-3;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $cm-space-2;
  padding: 120rpx $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
}

.empty__t {
  margin-top: $cm-space-2;
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: $cm-color-text-1;
}

.empty__h {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  text-align: center;
}

.list {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.addr {
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
  transition: transform 140ms ease-out;
}

.addr--on {
  transform: scale(0.99);
}

.addr__top {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  margin-bottom: $cm-space-2;
}

.addr__name {
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

.addr__phone {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.addr__def {
  margin-left: auto;
  padding: 0 10rpx;
  height: 34rpx;
  line-height: 34rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: $cm-radius-sm;
}

.addr__txt {
  display: block;
  font-size: $cm-font-size-sm;
  line-height: 1.6;
  color: $cm-color-text-2;
}

.addr__ops {
  display: flex;
  justify-content: flex-end;
  gap: $cm-space-5;
  margin-top: $cm-space-3;
  padding-top: $cm-space-3;
  border-top: 1rpx solid $cm-color-gray-100;
}

.addr__op {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.addr__op--danger {
  color: $cm-color-price;
}

.dock {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $cm-space-3 $cm-space-4 calc(#{$cm-space-3} + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 -2rpx 16rpx rgba(27, 27, 33, 0.06);
}

.dock__btn {
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $cm-space-2;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out;
}

.dock__btn--on {
  transform: scale(0.98);
}

.dock__plus {
  font-size: $cm-font-size-md;
  color: #fff;
}

.dock__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: #fff;
}
</style>
