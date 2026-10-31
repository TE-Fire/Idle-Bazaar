<script setup lang="ts">
/**
 * 我的商品（卖家视角，business/01 §四 商品（卖家视角））
 *
 * <p>与首页列表的关键差异：**含草稿等全部状态**（首页只出在售），按最近更新倒序。
 * 操作按钮按状态给出，和后端 `delete` / `offline` / `update` 的准入一一对应：
 *
 * - 草稿：继续编辑 / 提交送审 / 删除
 * - 在售：编辑 / 下架
 * - 已下架 / 审核未过：编辑 / 删除
 * - 待审核 / 已售出 / 已强制下架：只读（强制下架的恢复走申诉，不在这页）
 *
 * <p>「提交送审」的实现是拉详情原样提交（draft=false）：草稿进队是编辑接口的
 * 既有语义（不论字段是否变化，此前从没进过），列表页不重复组装表单。
 */

import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userItemApi, type ApiItemVO } from '@campus/api'
import { ITEM_STATUS_LABELS, type ItemStatus } from '@campus/shared'

const router = useRouter()

const TABS: Array<{ label: string; status?: ItemStatus }> = [
  { label: '全部' },
  { label: '草稿', status: 'DRAFT' },
  { label: '待审核', status: 'PENDING' },
  { label: '在售', status: 'ON_SALE' },
  { label: '已下架', status: 'OFFLINE' },
  { label: '审核未过', status: 'REJECTED' },
  { label: '已售出', status: 'SOLD' },
  { label: '已强制下架', status: 'FORCED_OFFLINE' }
]

const activeTab = ref(0)
const list = ref<ApiItemVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await userItemApi.mine({
      status: TABS[activeTab.value].status,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page.list
    total.value = page.total
  } catch {
    // http 层已提示；未登录（1001）会跳登录页
  } finally {
    loading.value = false
  }
}

function switchTab(index: number): void {
  activeTab.value = index
  pageNum.value = 1
  void load()
}

function edit(id: string): void {
  void router.push({ path: '/publish', query: { edit: id } })
}

/** 删除确认：软删不可恢复（对卖家不可见），确认文案写清楚 */
async function remove(item: ApiItemVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除后不可恢复，「${item.title}」将不再展示。确定删除？`, '删除商品', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return // 用户取消
  }
  try {
    await userItemApi.remove(item.id)
    ElMessage.success({ message: '已删除', duration: 2000 })
    void load()
  } catch {
    // http 层已提示（在售 / 已售出不可删，1107）
  }
}

/** 下架确认：下架后可重新编辑上架，不是终态 */
async function offline(item: ApiItemVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`下架后商品停止展示，可重新编辑上架。确定下架「${item.title}」？`, '下架商品', {
      type: 'warning',
      confirmButtonText: '下架',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await userItemApi.offline(item.id)
    ElMessage.success({ message: '已下架', duration: 2000 })
    void load()
  } catch {
    // http 层已提示
  }
}

/** 草稿提交送审：拉详情原样提交，draft=false。D 档空描述会被后端拦（1000） */
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
      meetPointId: detail.meetPointId ?? undefined,
      description: detail.description ?? undefined,
      images: (detail.images ?? []).map((i) => i.url),
      negotiable: detail.negotiable ?? 0,
      deliveryType: detail.deliveryType ?? 1,
      pickupAddressId: detail.pickupAddressId ?? undefined,
      draft: false
    })
    ElMessage.success({ message: '已提交审核', duration: 2000 })
    void load()
  } catch {
    // http 层已提示
  }
}

/** 状态标签配色：终态与异常态用警示色，在售用成功色 */
function statusClass(status: ItemStatus): string {
  if (status === 'ON_SALE') return 'ok'
  if (status === 'SOLD') return 'muted'
  return 'warn'
}

function formatTime(iso: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(load)
</script>

<template>
  <div class="cm-shell my-items">
    <header class="head">
      <h1 class="head__title">我的商品</h1>
      <el-button type="primary" round @click="router.push({ name: 'publish' })">＋ 发布闲置</el-button>
    </header>

    <nav class="tabs">
      <button
        v-for="(t, i) in TABS"
        :key="t.label"
        type="button"
        class="tabs__item"
        :class="{ on: activeTab === i }"
        @click="switchTab(i)"
      >
        {{ t.label }}
      </button>
    </nav>

    <p v-if="!loading && list.length === 0" class="empty">这个状态下还没有商品</p>

    <div v-loading="loading" class="grid">
      <article v-for="item in list" :key="item.id" class="card">
        <div class="card__ph">
          <img v-if="item.mainImage" :src="item.mainImage" alt="" />
          <div v-else class="card__ph-empty">无图</div>
          <span class="card__status" :class="statusClass(item.status)">
            {{ ITEM_STATUS_LABELS[item.status] }}
          </span>
        </div>
        <div class="card__bd">
          <h3 class="card__tt">{{ item.title }}</h3>
          <p class="card__pr"><b>¥{{ item.price }}</b><s v-if="item.originPrice">¥{{ item.originPrice }}</s></p>
          <p class="card__mt">{{ item.campusName }} · {{ formatTime(item.publishAt) }}</p>
        </div>
        <footer class="card__ft">
          <template v-if="item.status === 'DRAFT'">
            <a @click="edit(item.id)">继续编辑</a>
            <a class="pri" @click="submitDraft(item)">提交送审</a>
            <a class="danger" @click="remove(item)">删除</a>
          </template>
          <template v-else-if="item.status === 'ON_SALE'">
            <a @click="edit(item.id)">编辑</a>
            <a class="danger" @click="offline(item)">下架</a>
          </template>
          <template v-else-if="item.status === 'OFFLINE' || item.status === 'REJECTED'">
            <a @click="edit(item.id)">编辑</a>
            <a class="danger" @click="remove(item)">删除</a>
          </template>
          <template v-else>
            <span class="card__none">暂无操作</span>
          </template>
        </footer>
      </article>
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="pager"
      layout="prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="(p: number) => { pageNum = p; load() }"
    />
  </div>
</template>

<style scoped lang="scss">
.my-items {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.head__title {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
}

/* ── 状态 tabs：与首页筛选条同款横排按钮 ───────────────── */

.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
}

.tabs__item {
  height: 30px;
  padding: 0 var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }

  &.on {
    font-weight: 700;
    color: var(--cm-color-text-1);
    background: var(--cm-color-brand);
    border-color: var(--cm-color-brand);
  }
}

.empty {
  margin: 0;
  padding: var(--cm-space-8) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* ── 卡片网格 ───────────────────────────────────────────── */

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: var(--cm-space-4);
}

.card {
  display: flex;
  flex-direction: column;
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-xl);
  overflow: hidden;
  box-shadow: var(--cm-shadow-1);
}

.card__ph {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--cm-color-bg);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.card__ph-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__status {
  position: absolute;
  left: var(--cm-space-2);
  top: var(--cm-space-2);
  padding: 1px 8px;
  font-size: var(--cm-font-size-xs);
  font-weight: 700;
  border-radius: var(--cm-radius-pill);

  &.ok {
    color: #fff;
    background: var(--cm-color-ok);
  }

  &.warn {
    color: var(--cm-color-text-1);
    background: var(--cm-color-brand);
  }

  &.muted {
    color: #fff;
    background: var(--cm-color-text-3);
  }
}

.card__bd {
  flex: 1;
  padding: var(--cm-space-3);
}

.card__tt {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__pr {
  margin: var(--cm-space-2) 0 0;

  b {
    font-size: var(--cm-font-size-md);
    font-weight: 800;
    color: var(--cm-color-price);
  }

  s {
    margin-left: var(--cm-space-2);
    font-size: var(--cm-font-size-xs);
    color: var(--cm-color-text-3);
  }
}

.card__mt {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__ft {
  display: flex;
  gap: var(--cm-space-4);
  padding: var(--cm-space-2) var(--cm-space-3);
  border-top: 1px solid var(--cm-color-border);
  font-size: var(--cm-font-size-xs);

  a {
    color: var(--cm-color-text-2);
    cursor: pointer;

    &:hover {
      color: var(--cm-color-text-1);
    }

    &.pri {
      font-weight: 700;
      color: var(--cm-color-price);
    }

    &.danger:hover {
      color: var(--cm-color-price);
    }
  }
}

.card__none {
  color: var(--cm-color-text-3);
}

.pager {
  justify-content: center;
}
</style>
