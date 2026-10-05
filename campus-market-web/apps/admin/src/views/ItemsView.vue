<script setup lang="ts">
/**
 * A3 商品管理（business/03 A-B4，接口清单见 notes/08 §四）
 *
 * <p>与 C 端商品列表的最大区别：**这里只能下架，不能代编辑**。
 * business/03 §一 明确「不做替用户改价改描述」——只能强制下架，不能代编辑，避免责任不清。
 * 所以详情页有「下架」没有「编辑」，页面上还要把这句话写出来，否则会被当成功能缺失。
 *
 * <p>价格列额外给出**同类目成交均价**：审核员要判断「这个价合不合理」，
 * 偏离 3 倍以上标红，同时也是审核权重里「价格异常分」的来源（F-A2）。
 *
 * <p>权限：item.view 开页面，item.offline 管下架，item.delete 管删除。
 * 审核员只有 item.view——能看到但不能处置，商品处置必须走 A5 审核队列。
 *
 * <p>筛选下拉走 `dictApi.options()`（登录即可读）而不是字典维护接口：
 * 后者全挂 `content.edit`（只有超管），运营与审核员进来会直接 403。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ITEM_STATUS_LABELS,
  OFFLINE_REASONS,
  PERM_CODES,
  isPriceAbnormal
} from '@campus/shared'
import type { ItemStatus, OfflineReason } from '@campus/shared'
import {
  dictApi,
  itemApi,
  type DictCampusVO,
  type DictCategoryVO,
  type ItemDetailVO,
  type ItemListVO
} from '@campus/api'

type StatusKey = ItemStatus

/** 状态配色：与 C 端一致，正常绿、待办蓝、异常红、终态灰 */
const STATUS_COLORS: Record<StatusKey, string> = {
  DRAFT: 'default',
  PENDING: 'blue',
  ON_SALE: 'green',
  SOLD: 'default',
  OFFLINE: 'default',
  REJECTED: 'red',
  FORCED_OFFLINE: 'red'
}

const PAGE_SIZE = 10

/* ── 数据源 ── */

const rows = ref<ItemListVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

const filters = reactive<{
  keyword: string
  categoryId: string
  status: StatusKey | ''
  campusId: string
}>({ keyword: '', categoryId: '', status: '', campusId: '' })

/** 筛选下拉：只取启用项（options 接口只下发启用项），一级分类用 !parentId 判 */
const categories = ref<DictCategoryVO[]>([])
const campuses = ref<DictCampusVO[]>([])

const rootCategories = computed(() => categories.value.filter((c) => !c.parentId))

const queryParams = () => ({
  keyword: filters.keyword.trim() || undefined,
  categoryId: filters.categoryId || undefined,
  status: filters.status || undefined,
  campusId: filters.campusId || undefined,
  pageNum: pageNum.value,
  pageSize: PAGE_SIZE
})

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const r = await itemApi.list(queryParams())
    rows.value = r?.list ?? []
    total.value = r?.total ?? 0
  } catch {
    // 请求层已提示（含 traceId）。保留上一次的数据不清空：
    // 拉失败时看到旧数据，比看到一张空表更容易判断是"没拉到"而不是"真没有"
  } finally {
    loading.value = false
  }
}

const loadDict = async (): Promise<void> => {
  try {
    const d = await dictApi.options()
    categories.value = d?.categories ?? []
    campuses.value = d?.campuses ?? []
  } catch {
    // 下拉拉不到就留空，表格本身还能看
  }
}

onMounted(() => {
  void Promise.all([loadDict(), load()])
})

/** 筛选条件变了要回到第一页：停在第 3 页改条件会看到"没有数据"的错觉 */
const onSearch = (): void => {
  pageNum.value = 1
  void load()
}

const reset = (): void => {
  filters.keyword = ''
  filters.categoryId = ''
  filters.status = ''
  filters.campusId = ''
  pageNum.value = 1
  void load()
}

const onPageChange = (page: number): void => {
  pageNum.value = page
  void load()
}

/* ── 展示辅助 ── */

/** 金额是后端给的定长字符串，比较前显式转 number */
const priceAbnormal = (row: ItemListVO): boolean =>
  isPriceAbnormal({ price: Number(row.price), categoryAvgPrice: Number(row.categoryAvgPrice) })

/**
 * 主图加载失败的 id 集合。演示数据里的图片是 `cdn.campus.local` 外链，
 * 本地解析不到，不兜底会满屏碎图标。
 */
const thumbFailed = reactive<Record<string, boolean>>({})

const columns: TableColumnsType<ItemListVO> = [
  { title: '商品', key: 'item', width: 300 },
  { title: '价格', key: 'price', width: 150 },
  { title: '成色', key: 'condition', width: 90 },
  { title: '校区 / 面交点', key: 'place', width: 200 },
  { title: '卖家', key: 'seller', width: 150 },
  { title: '状态', key: 'status', width: 110 },
  { title: '浏览 / 想要', key: 'heat', width: 110 },
  { title: '操作', key: 'action', width: 200 }
]

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const detail = ref<ItemDetailVO | null>(null)

const openDetail = async (row: ItemListVO): Promise<void> => {
  detail.value = null
  detailOpen.value = true
  try {
    detail.value = await itemApi.detail(row.id)
  } catch {
    detailOpen.value = false
  }
}

/* ── 强制下架：必须选原因 + 可补充文字（处置要能复盘） ── */

/** 与后端 `ItemStatusEnum#canForceOffline` 一致 */
const canOffline = (row: ItemListVO): boolean =>
  row.status === 'ON_SALE' || row.status === 'PENDING'

/** 与后端 `ItemStatusEnum#canDelete` 一致：不含在售与已售出 */
const canDelete = (row: ItemListVO): boolean =>
  row.status === 'DRAFT' ||
  row.status === 'OFFLINE' ||
  row.status === 'REJECTED' ||
  row.status === 'FORCED_OFFLINE'

const offlineOpen = ref(false)
const offlineTarget = ref<ItemListVO | null>(null)
const offlineForm = reactive<{ reason: OfflineReason | ''; note: string }>({ reason: '', note: '' })
const submitting = ref(false)

const openOffline = (row: ItemListVO): void => {
  offlineTarget.value = row
  offlineForm.reason = ''
  offlineForm.note = ''
  offlineOpen.value = true
}

const submitOffline = async (): Promise<void> => {
  const row = offlineTarget.value
  if (!row) return
  if (!offlineForm.reason) {
    message.warning('请选择下架原因，处置依据要留痕')
    return
  }
  submitting.value = true
  try {
    await itemApi.offline(row.id, {
      reason: offlineForm.reason,
      note: offlineForm.note.trim() || undefined
    })
    message.success(`已下架「${row.title}」，原因：${offlineForm.reason}`)
    offlineOpen.value = false
    detailOpen.value = false
    await load()
  } catch {
    // 请求层已提示
  } finally {
    submitting.value = false
  }
}

const onDelete = (row: ItemListVO): void => {
  Modal.confirm({
    title: `删除「${row.title}」？`,
    content: '删除后不可恢复。建议先下架、观察一段时间再清理。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      await itemApi.remove(row.id)
      message.success('已删除')
      await load()
    }
  })
}

const conditionLabel = (code: string): string =>
  ({ A: '全新未拆', B: '几乎全新', C: '轻微使用痕迹', D: '明显使用痕迹' })[code] ?? code
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">商品管理</h1>
      <p class="cm-page__sub">
        平台只能下架、不能代用户改价改描述；价格异常按同类目成交均价判定。
      </p>
    </header>

    <div class="cm-card items__filter">
      <a-space wrap :size="12">
        <a-input
          v-model:value="filters.keyword"
          allow-clear
          placeholder="标题 / 卖家昵称"
          style="width: 220px"
          @press-enter="onSearch"
        />
        <a-select v-model:value="filters.categoryId" style="width: 150px" placeholder="分类：全部">
          <a-select-option value="">分类：全部</a-select-option>
          <a-select-option v-for="c in rootCategories" :key="c.id" :value="c.id">
            {{ c.name }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="filters.status" style="width: 150px" placeholder="状态：全部">
          <a-select-option value="">状态：全部</a-select-option>
          <a-select-option v-for="(label, key) in ITEM_STATUS_LABELS" :key="key" :value="key">
            {{ label }}
          </a-select-option>
        </a-select>
        <a-select v-model:value="filters.campusId" style="width: 150px" placeholder="校区：全部">
          <a-select-option value="">校区：全部</a-select-option>
          <a-select-option v-for="c in campuses" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
        </a-select>
        <a-button type="primary" shape="round" @click="onSearch">查询</a-button>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="items__count">共 {{ total }} 件</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{
        current: pageNum,
        pageSize: PAGE_SIZE,
        total,
        showTotal: (t: number) => `共 ${t} 条`,
        onChange: onPageChange
      }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'item'">
          <div class="items__cell">
            <span class="items__thumb">
              <img
                v-if="record.mainImage && !thumbFailed[record.id]"
                :src="record.mainImage"
                alt=""
                @error="thumbFailed[record.id] = true"
              />
            </span>
            <span class="items__title">
              {{ record.title }}
              <span class="items__muted">{{ record.categoryName }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'price'">
          <span :class="{ 'items__abnormal': priceAbnormal(record) }">¥{{ record.price }}</span>
          <span class="items__muted">/ 均价 ¥{{ record.categoryAvgPrice }}</span>
        </template>

        <template v-else-if="column.key === 'condition'">
          <a-tag>{{ record.condition }}</a-tag>
        </template>

        <template v-else-if="column.key === 'place'">
          {{ record.campusName }}
          <span class="items__muted">· {{ record.meetPointName ?? '—' }}</span>
        </template>

        <template v-else-if="column.key === 'seller'">
          {{ record.sellerNick }}
          <span class="items__muted">信用 {{ record.sellerCredit }}</span>
        </template>

        <template v-else-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status as StatusKey]">
            {{ ITEM_STATUS_LABELS[record.status as StatusKey] }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'heat'">
          {{ record.viewCount }} / {{ record.wantCount }}
          <span v-if="record.reportCount > 0" class="items__report">举报 {{ record.reportCount }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="canOffline(record)"
              v-perm="PERM_CODES.ITEM_OFFLINE"
              type="link"
              size="small"
              danger
              @click="openOffline(record)"
            >
              下架
            </a-button>
            <a-button
              v-if="canDelete(record)"
              v-perm="PERM_CODES.ITEM_DELETE"
              type="link"
              size="small"
              danger
              @click="onDelete(record)"
            >
              删除
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 详情：只读，没有「编辑」入口 -->
    <a-drawer v-model:open="detailOpen" title="商品详情" :width="520">
      <template v-if="detail">
        <div class="items__gallery">
          <template v-if="detail.images?.length">
            <img
              v-for="img in detail.images"
              :key="img.id"
              :src="img.url"
              alt=""
              @error="thumbFailed[img.id] = true"
            />
          </template>
          <span v-else>（暂无图片）</span>
        </div>

        <h2 class="items__title-lg">{{ detail.title }}</h2>

        <a-descriptions :column="2" bordered size="small" class="items__desc">
          <a-descriptions-item label="价格">
            ¥{{ detail.price }}
            <span v-if="priceAbnormal(detail)" class="items__abnormal">异常</span>
          </a-descriptions-item>
          <a-descriptions-item label="同类目均价">¥{{ detail.categoryAvgPrice }}</a-descriptions-item>
          <a-descriptions-item label="成色">
            {{ detail.condition }} · {{ conditionLabel(detail.condition) }}
          </a-descriptions-item>
          <a-descriptions-item label="原价">
            {{ detail.originalPrice ? `¥${detail.originalPrice}` : '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="分类">{{ detail.categoryName }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            {{ ITEM_STATUS_LABELS[detail.status as StatusKey] }}
          </a-descriptions-item>
          <a-descriptions-item label="面交点">{{ detail.meetPointName ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="卖家">
            {{ detail.sellerNick }}（信用 {{ detail.sellerCredit }}）
          </a-descriptions-item>
          <a-descriptions-item label="举报次数">{{ detail.reportCount }}</a-descriptions-item>
          <a-descriptions-item label="图片风险分">{{ detail.riskScore }}</a-descriptions-item>
        </a-descriptions>

        <h3 class="items__sub">商品描述</h3>
        <p class="items__text">{{ detail.description ?? '—' }}</p>

        <p class="items__note">
          平台不代用户修改价格与描述——如需更正，请下架后由卖家自行修改重提。
        </p>

        <div class="items__actions">
          <a-button
            v-if="canOffline(detail)"
            v-perm="PERM_CODES.ITEM_OFFLINE"
            danger
            shape="round"
            @click="openOffline(detail)"
          >
            强制下架
          </a-button>
        </div>
      </template>
      <template v-else>
        <p class="items__note">加载中…</p>
      </template>
    </a-drawer>

    <a-modal
      v-model:open="offlineOpen"
      title="强制下架"
      ok-text="确认下架"
      cancel-text="取消"
      :confirm-loading="submitting"
      @ok="submitOffline"
    >
      <p class="items__note">处置依据会记入操作日志，卖家可据此申诉。</p>
      <a-form layout="vertical">
        <a-form-item label="下架原因" required>
          <a-select v-model:value="offlineForm.reason" placeholder="选择预设原因">
            <a-select-option v-for="r in OFFLINE_REASONS" :key="r" :value="r">{{ r }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="补充说明">
          <a-textarea v-model:value="offlineForm.note" :rows="3" placeholder="可补充具体依据（选填）" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.items__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  padding: var(--cm-space-4) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.items__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.items__cell {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.items__thumb {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  overflow: hidden;
}

/* 演示数据的图片是外链，加载失败时 thumbFailed 会把 img 摘掉，露出上面的底色块 */
.items__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.items__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.items__muted {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-xs);
}

/* 价格异常：红字 + 角标，因为它是审核权重的输入之一 */
.items__abnormal {
  margin-left: var(--cm-space-2);
  padding: 0 6px;
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-red-100, #fcecec);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

span.items__abnormal {
  display: inline-block;
}

.items__report {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

.items__gallery {
  display: flex;
  gap: var(--cm-space-2);
  overflow-x: auto;
  margin-bottom: var(--cm-space-5);
}

.items__gallery img {
  width: 160px;
  height: 160px;
  flex: none;
  object-fit: cover;
  border-radius: var(--cm-radius-lg);
  background: var(--cm-color-surface-muted);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
}

.items__title-lg {
  margin: 0 0 var(--cm-space-4);
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.items__desc {
  margin-bottom: var(--cm-space-5);
}

.items__sub {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.items__text {
  margin: 0;
  color: var(--cm-color-text-2);
  line-height: 1.7;
}

.items__note {
  margin: var(--cm-space-4) 0 0;
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.items__actions {
  margin-top: var(--cm-space-5);
  display: flex;
  justify-content: flex-end;
}
</style>
