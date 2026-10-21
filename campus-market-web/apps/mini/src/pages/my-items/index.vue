<script setup lang="ts">
/**
 * 我的商品（卖家视角，与 Web 端 MyItemsView 同口径）
 *
 * <p>含草稿等全部状态（首页只出在售）。操作按状态给出，与后端 delete / offline /
 * update 的准入一一对应：草稿=继续编辑 / 提交送审 / 删除；在售=编辑 / 下架；
 * 已下架 / 审核未过=编辑 / 删除；其余只读。
 */

import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { userItemApi, type ApiItemVO } from '@/api/user'

const STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待审核',
  ON_SALE: '在售',
  SOLD: '已售出',
  OFFLINE: '已下架',
  REJECTED: '审核未过',
  FORCED_OFFLINE: '已强制下架'
}

const TABS = [
  { label: '全部', status: '' },
  { label: '草稿', status: 'DRAFT' },
  { label: '待审核', status: 'PENDING' },
  { label: '在售', status: 'ON_SALE' },
  { label: '已下架', status: 'OFFLINE' },
  { label: '审核未过', status: 'REJECTED' },
  { label: '已售出', status: 'SOLD' }
]

const activeTab = ref(0)
const list = ref<ApiItemVO[]>([])

async function load(): Promise<void> {
  try {
    const page = await userItemApi.mine(TABS[activeTab.value].status)
    list.value = page.list
  } catch {
    // request 层已提示；未登录（1001）会跳登录页
  }
}

function switchTab(index: number): void {
  activeTab.value = index
  void load()
}

function edit(id: string): void {
  void uni.navigateTo({ url: `/pages/publish/index?edit=${id}` })
}

function remove(item: ApiItemVO): void {
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
        void load()
      } catch {
        // request 层已提示（在售 / 已售出不可删）
      }
    }
  })
}

function offline(item: ApiItemVO): void {
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
        void load()
      } catch {
        // request 层已提示
      }
    }
  })
}

/** 草稿提交送审：拉详情原样提交（draft=false） */
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
    void load()
  } catch {
    // request 层已提示
  }
}

/** onShow 而非 onMounted：从编辑页返回时要看到最新状态 */
onShow(load)
</script>

<template>
  <view class="page">
    <scroll-view scroll-x class="tabs" enhanced show-scrollbar>
      <view class="tabs__in">
        <text
          v-for="(t, i) in TABS"
          :key="t.label"
          class="tab"
          :class="{ 'tab--on': activeTab === i }"
          @click="switchTab(i)"
        >
          {{ t.label }}
        </text>
      </view>
    </scroll-view>

    <view v-if="list.length === 0" class="empty">这个状态下还没有商品</view>

    <view v-for="item in list" :key="item.id" class="card">
      <view class="card__main" @click="edit(item.id)">
        <image v-if="item.mainImage" class="card__img" :src="item.mainImage" mode="aspectFill" />
        <view v-else class="card__img card__img--empty">无图</view>
        <view class="card__meta">
          <text class="card__tt">{{ item.title }}</text>
          <text class="card__pr">¥{{ item.price }}</text>
          <text class="card__sub">{{ STATUS_LABELS[item.status] }} · {{ item.campusName }}</text>
        </view>
      </view>
      <view class="card__acts">
        <template v-if="item.status === 'DRAFT'">
          <text class="act" @click="edit(item.id)">继续编辑</text>
          <text class="act act--pri" @click="submitDraft(item)">提交送审</text>
          <text class="act act--danger" @click="remove(item)">删除</text>
        </template>
        <template v-else-if="item.status === 'ON_SALE'">
          <text class="act" @click="edit(item.id)">编辑</text>
          <text class="act act--danger" @click="offline(item)">下架</text>
        </template>
        <template v-else-if="item.status === 'OFFLINE' || item.status === 'REJECTED'">
          <text class="act" @click="edit(item.id)">编辑</text>
          <text class="act act--danger" @click="remove(item)">删除</text>
        </template>
        <text v-else class="card__none">暂无操作</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-3 $cm-space-3 160rpx;
  background: $cm-color-bg;
  min-height: 100vh;
}

.tabs {
  white-space: nowrap;
  margin-bottom: $cm-space-3;
}

.tabs__in {
  display: inline-flex;
  gap: $cm-space-2;
}

.tab {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 $cm-space-4;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border-radius: $cm-radius-pill;
}

.tab--on {
  font-weight: 700;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border: 1rpx solid $cm-color-brand;
}

.empty {
  padding: $cm-space-8 0;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.card {
  margin-bottom: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
  overflow: hidden;
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
</style>
