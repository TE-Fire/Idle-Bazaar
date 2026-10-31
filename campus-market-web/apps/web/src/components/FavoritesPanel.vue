<script setup lang="ts">
/**
 * 收藏列表（notes/24）：个人主页「收藏」Tab 的内容，**仅本人可见**。
 *
 * <p>只展示「商品还在」的收藏：已软删与被强制下架的商品后端就不返回，所以这里没有
 * 「失效收藏」这类行——商品没了，收藏指向的东西也不存在。已售出/已下架照常展示并标状态，
 * 用户要能看到「我收的那件已经被买走了」。
 *
 * <p>取消收藏后就地移除该行，不重新拉整页：重拉会让滚动位置跳回顶部。
 */

import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userItemApi, type ApiItemVO } from '@campus/api'
import { ITEM_STATUS_LABELS, formatPrice, type ItemStatus } from '@campus/shared'

const router = useRouter()

const list = ref<ApiItemVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await userItemApi.favorites({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = page.list
    total.value = page.total
  } catch {
    // http 层已提示；未登录（1001）会跳登录页
  } finally {
    loading.value = false
  }
}

function goDetail(id: string): void {
  void router.push({ name: 'item', params: { id } })
}

async function remove(id: string): Promise<void> {
  await userItemApi.unfavorite(id)
  list.value = list.value.filter((i) => i.id !== id)
  total.value = Math.max(total.value - 1, 0)
  ElMessage.success('已取消收藏')
}

function statusClass(status: ItemStatus): string {
  return status === 'ON_SALE' ? 'ok' : 'muted'
}

function formatTime(iso?: string | null): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

function onPage(p: number): void {
  pageNum.value = p
  void load()
}

onMounted(load)
</script>

<template>
  <div class="fav">
    <p v-if="!loading && list.length === 0" class="fav__empty">
      还没有收藏。看到喜欢的点一下「收藏」，过几天再回来比价。
    </p>

    <div v-loading="loading" class="fav__grid">
      <article v-for="item in list" :key="item.id" class="card">
        <button type="button" class="card__body" @click="goDetail(item.id)">
          <div class="card__ph">
            <img v-if="item.mainImage" :src="item.mainImage" alt="" />
            <div v-else class="card__ph-empty">无图</div>
          </div>
          <h3 class="card__title">{{ item.title }}</h3>
          <p class="card__price">{{ formatPrice(Number(item.price)) }}</p>
          <p class="card__meta">
            <span class="tag" :class="statusClass(item.status)">{{ ITEM_STATUS_LABELS[item.status] }}</span>
            <span>{{ item.campusName ?? '' }}</span>
          </p>
          <p class="card__time">收藏于 {{ formatTime(item.favoritedAt) }}</p>
        </button>
        <button type="button" class="card__out" @click="remove(item.id)">取消收藏</button>
      </article>
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="fav__pager"
      layout="prev, pager, next"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      @current-change="onPage"
    />
  </div>
</template>

<style scoped>
.fav__empty {
  margin: 64px 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.fav__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--cm-space-4);
  min-height: 120px;
}

.card {
  position: relative;
  overflow: hidden;
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  transition: box-shadow 0.18s ease, transform 0.18s ease;
}

.card:hover {
  box-shadow: var(--cm-shadow-2);
  transform: translateY(-2px);
}

.card__body {
  display: block;
  width: 100%;
  padding: 0 0 12px;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
}

.card__ph {
  aspect-ratio: 1 / 1;
  background: #f4f4f7;
}

.card__ph img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card__ph-empty {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--cm-color-text-3);
}

.card__title {
  margin: 10px 12px 4px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__price {
  margin: 0 12px;
  font-size: 17px;
  font-weight: 700;
  color: #e8543f;
}

.card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 6px 12px 0;
  font-size: 12px;
  color: var(--cm-color-text-3);
}

.tag {
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
}

.tag.ok {
  background: #eaf7f0;
  color: #0f9f68;
}

.tag.muted {
  background: #f1f1f4;
  color: #7a7a83;
}

.card__time {
  margin: 6px 12px 0;
  font-size: 11px;
  color: var(--cm-color-text-3);
}

.card__out {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 3px 9px;
  border: 1px solid var(--cm-color-border);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.94);
  font-size: 11px;
  color: var(--cm-color-text-2);
  cursor: pointer;
}

.card__out:hover {
  border-color: #e8543f;
  color: #e8543f;
}

.fav__pager {
  margin-top: var(--cm-space-6);
  justify-content: center;
}
</style>
