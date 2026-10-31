<script setup lang="ts">
/**
 * 我的订单（学生端，notes/13 第七节）
 *
 * <p>买家与卖家共用一页：同一个用户两个身份都有，`role` 决定查哪一边，
 * 后端按 buyer_id / seller_id 走各自索引。tab 切换即换 role 并回到第一页。
 *
 * <p>状态筛选走**服务端**（接口支持 `status` 枚举名），不用 mini 那种一页拉 50 条
 * 再本地过滤的做法——桌面端页宽、订单多，跨状态分页会让 chip 和总数对不上。
 *
 * <p>只读列表，状态动作都在详情页（`/order/:id`）：列表塞满按钮既挤又容易误点。
 */

import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userOrderApi, type OrderRole, type UserOrderStatus, type UserOrderVO } from '@campus/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const session = useUserStore()

/**
 * 状态 chips：全部 + 八个状态（CANCELED 单 L，与后端枚举名逐字一致）。
 * 不比 mini 合并「已完成 / 已取消 / 已退款」——服务端筛选一次只能一个状态，
 * 合并就得前端二次过滤，跨页时总数会撒谎。
 */
const FILTERS: Array<{ label: string; status?: UserOrderStatus }> = [
  { label: '全部' },
  { label: '待付款', status: 'WAIT_PAY' },
  { label: '待发货', status: 'WAIT_SHIP' },
  { label: '待自提', status: 'WAIT_PICKUP' },
  { label: '已发货', status: 'SHIPPED' },
  { label: '待面交', status: 'WAIT_MEET' },
  { label: '已验货', status: 'VERIFIED' },
  { label: '退款中', status: 'REFUNDING' },
  { label: '纠纷中', status: 'DISPUTING' },
  { label: '已完成', status: 'COMPLETED' },
  { label: '已退款', status: 'REFUNDED' },
  { label: '已取消', status: 'CANCELED' }
]

/** 状态文案按角色分叉：同一个 WAIT_PAY，买家看到「待付款」，卖家看到「等待买家付款」 */
const STATUS_TEXT: Record<UserOrderStatus, { buyer: string; seller: string }> = {
  WAIT_PAY: { buyer: '待付款', seller: '等待买家付款' },
  WAIT_MEET: { buyer: '待面交', seller: '待面交' },
  VERIFIED: { buyer: '已验货', seller: '已验货' },
  COMPLETED: { buyer: '已完成', seller: '已完成' },
  CANCELED: { buyer: '已取消', seller: '已取消' },
  REFUNDING: { buyer: '退款中', seller: '退款待处理' },
  REFUNDED: { buyer: '已退款', seller: '已退款' },
  DISPUTING: { buyer: '纠纷中', seller: '纠纷中' },
  WAIT_SHIP: { buyer: '待发货', seller: '待发货' },
  SHIPPED: { buyer: '已发货', seller: '已发货' },
  WAIT_PICKUP: { buyer: '待自提', seller: '待自提' }
}

const role = ref<OrderRole>('buyer')
const filterIdx = ref(0)
const list = ref<UserOrderVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)

const activeFilter = computed(() => FILTERS[filterIdx.value])

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await userOrderApi.list({
      role: role.value,
      status: activeFilter.value.status,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page.list
    total.value = page.total
  } catch {
    // http 层已提示；未登录（1001）会被切成登录页
  } finally {
    loading.value = false
  }
}

function switchRole(next: OrderRole): void {
  if (role.value === next) return
  role.value = next
  filterIdx.value = 0
  pageNum.value = 1
  void load()
}

function switchFilter(index: number): void {
  if (filterIdx.value === index) return
  filterIdx.value = index
  pageNum.value = 1
  void load()
}

/** 详情页要带 role：同一单两个视角，动作集合不同 */
function openDetail(o: UserOrderVO): void {
  void router.push({ name: 'order-detail', params: { id: o.id }, query: { role: role.value } })
}

function statusText(s: UserOrderStatus): string {
  return STATUS_TEXT[s][role.value]
}

/** 配色：待办用品牌色，异常处理中用警示色，终态置灰 */
function statusClass(s: UserOrderStatus): string {
  if (s === 'WAIT_PAY' || s === 'REFUNDING') return 'warn'
  if (s === 'DISPUTING') return 'danger'
  if (s === 'WAIT_MEET' || s === 'VERIFIED' || s === 'WAIT_SHIP' || s === 'SHIPPED' || s === 'WAIT_PICKUP')
    return 'ok'
  return 'muted'
}

function formatTime(iso?: string): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => {
  if (!session.isLogin) {
    ElMessage.info({ message: '请先登录', duration: 2000 })
    void router.push({ name: 'login', query: { redirect: '/orders' } })
    return
  }
  void load()
})
</script>

<template>
  <div class="cm-shell orders">
    <header class="head">
      <h1 class="head__title">我的订单</h1>
      <RouterLink :to="{ name: 'my-items' }" class="head__link">我的商品 →</RouterLink>
    </header>

    <!-- 角色 tab：买 / 卖 -->
    <div class="roles">
      <button
        v-for="r in (['buyer', 'seller'] as OrderRole[])"
        :key="r"
        type="button"
        class="roles__item"
        :class="{ on: role === r }"
        @click="switchRole(r)"
      >
        {{ r === 'buyer' ? '我买到的' : '我卖出的' }}
      </button>
    </div>

    <nav class="tabs">
      <button
        v-for="(f, i) in FILTERS"
        :key="f.label"
        type="button"
        class="tabs__item"
        :class="{ on: filterIdx === i }"
        @click="switchFilter(i)"
      >
        {{ f.label }}
      </button>
    </nav>

    <p v-if="!loading && list.length === 0" class="empty">
      {{ role === 'buyer' ? '还没有买到的订单' : '还没有卖出的订单' }}
    </p>

    <div v-loading="loading" class="list">
      <article v-for="o in list" :key="o.id" class="card" @click="openDetail(o)">
        <div class="card__ph">
          <img v-if="o.itemMainImage" :src="o.itemMainImage" alt="" />
          <div v-else class="card__ph-empty">无图</div>
        </div>

        <div class="card__bd">
          <div class="card__top">
            <h3 class="card__tt">{{ o.itemTitle || '商品已下架' }}</h3>
            <span class="card__status" :class="statusClass(o.status)">{{ statusText(o.status) }}</span>
          </div>
          <p class="card__meta">
            订单号 {{ o.orderNo }}
            <template v-if="o.itemGrade"> · {{ o.itemGrade }} 级成色</template>
          </p>
          <p class="card__meta">
            {{ formatTime(o.createTime) }} 下单
            <template v-if="o.meetTime"> · 约定面交 {{ formatTime(o.meetTime) }}</template>
          </p>
        </div>

        <div class="card__right">
          <b class="card__amount">¥{{ o.amount }}</b>
          <!-- 退款中的单在列表上直接给卖家人口，省得进详情才发现要处理 -->
          <span
            v-if="role === 'seller' && (o.status === 'REFUNDING' || o.status === 'WAIT_SHIP')"
            class="card__todo"
          >
            待你处理
          </span>
          <span v-else class="card__go">查看详情 →</span>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="pager"
      layout="prev, pager, next, total"
      :total="total"
      :page-size="pageSize"
      :current-page="pageNum"
      @current-change="(p: number) => { pageNum = p; load() }"
    />
  </div>
</template>

<style scoped lang="scss">
.orders {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.head__title {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 800;
}

.head__link {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  text-decoration: none;

  &:hover {
    color: var(--cm-color-text-1);
  }
}

/* ── 角色 tab：比状态 chips 更重的一层，用下划线区分 ───── */

.roles {
  display: flex;
  gap: var(--cm-space-6);
  border-bottom: 1px solid var(--cm-color-border);
}

.roles__item {
  height: 38px;
  padding: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;

  &.on {
    color: var(--cm-color-text-1);
    border-bottom-color: var(--cm-color-brand);
  }
}

/* ── 状态 chips：与我的商品同款 ───────────────────────── */

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
    color: var(--cm-color-text-1);
    background: var(--cm-color-brand-bg);
    border-color: var(--cm-color-brand-border);
  }
}

.empty {
  margin: var(--cm-space-6) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* ── 列表 ─────────────────────────────────────────────── */

.list {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.card {
  display: flex;
  gap: var(--cm-space-4);
  padding: var(--cm-space-3);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
  cursor: pointer;
  transition: border-color 160ms ease-out, box-shadow 160ms ease-out;

  &:hover {
    border-color: var(--cm-color-brand-border);
    box-shadow: var(--cm-shadow-1);
  }
}

.card__ph {
  width: 88px;
  height: 88px;
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

.card__ph-empty {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__bd {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.card__top {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.card__tt {
  margin: 0;
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  color: var(--cm-color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card__status {
  flex: none;
  padding: 2px 8px;
  font-size: var(--cm-font-size-xs);
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-bg);
  color: var(--cm-color-text-2);

  &.warn {
    color: #d46b08;
    background: #fff7e6;
  }

  &.danger {
    color: #cf1322;
    background: #fff1f0;
  }

  &.ok {
    color: #1e8e3e;
    background: #f0faf4;
  }

  &.muted {
    color: var(--cm-color-text-3);
  }
}

.card__meta {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.card__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  flex: none;
  gap: var(--cm-space-2);
}

.card__amount {
  font-size: var(--cm-font-size-md);
  font-weight: 700;
  color: var(--cm-color-price);
}

.card__todo {
  padding: 2px 8px;
  font-size: var(--cm-font-size-xs);
  color: #fff;
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-sm);
}

.card__go {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.pager {
  justify-content: center;
}
</style>
