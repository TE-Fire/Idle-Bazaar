<script setup lang="ts">
/**
 * A4 用户管理（business/03 A-B5 / F-A5，接口笔记 notes/20 D6）
 *
 * <p>三条硬约束，页面必须体现：
 *   ① **认证审核不做批量通过**——误通过会破坏校园信任机制，所以只能逐条看证件，
 *      没有「全选通过」；
 *   ② **认证状态与账号状态是两件事**：认证没过的人照样能登录，封禁的人认证可能是通过的，
 *      分成两列而不是合并成一个「状态」；
 *   ③ 信用分 < 60 的商品强制人工审核，所以低信用要显眼标出来。
 *
 * <p>筛选与分页都在服务端：这一页的数据会随用户量增长，前端过滤迟早要改成服务端，
 * 一开始就按服务端口径写，省一次返工。
 */

import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ACCOUNT_STATUS_LABELS,
  AUTH_STATUS_LABELS,
  PERM_CODES,
  type AccountStatus,
  type AuthStatus
} from '@campus/shared'
import {
  adminUserApi,
  dictApi,
  type AdminAppUserVO,
  type AdminAppUserDetailVO,
  type AdminUserAuthVO,
  type DictCollegeVO
} from '@campus/api'
import { tintStyle } from '@/utils/tint'

const AUTH_BY_CODE: Record<number, AuthStatus> = { 0: 'NONE', 1: 'PENDING', 2: 'PASSED', 3: 'REJECTED' }
const ACCOUNT_BY_CODE: Record<number, AccountStatus> = { 0: 'NORMAL', 1: 'WARNED', 2: 'BANNED' }

const loading = ref(false)
const list = ref<AdminAppUserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const colleges = ref<DictCollegeVO[]>([])

const filters = reactive<{
  keyword: string
  authStatus: number | 'ALL'
  accountStatus: number | 'ALL'
  collegeId: string | 'ALL'
}>({
  keyword: '',
  authStatus: 'ALL',
  accountStatus: 'ALL',
  collegeId: 'ALL'
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await adminUserApi.list({
      keyword: filters.keyword.trim() || undefined,
      authStatus: filters.authStatus === 'ALL' ? undefined : filters.authStatus,
      status: filters.accountStatus === 'ALL' ? undefined : filters.accountStatus,
      collegeId: filters.collegeId === 'ALL' ? undefined : filters.collegeId,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = page.list
    total.value = page.total
  } catch {
    // http 层已提示
  } finally {
    loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function reset(): void {
  filters.keyword = ''
  filters.authStatus = 'ALL'
  filters.accountStatus = 'ALL'
  filters.collegeId = 'ALL'
  search()
}

function onPage(p: number): void {
  pageNum.value = p
  void load()
}

const columns: TableColumnsType = [
  { title: '用户', key: 'user', width: 220 },
  { title: '学院 / 校区', key: 'org', width: 180 },
  { title: '信用分', key: 'credit', width: 110 },
  { title: '认证状态', key: 'auth', width: 110 },
  { title: '账号状态', key: 'account', width: 110 },
  { title: '注册时间', key: 'regAt', width: 120 },
  { title: '操作', key: 'action', width: 240 }
]

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const detail = ref<AdminAppUserDetailVO | null>(null)

async function openDetail(row: AdminAppUserVO): Promise<void> {
  detailOpen.value = true
  detail.value = null
  try {
    detail.value = await adminUserApi.detail(row.id)
  } catch {
    detailOpen.value = false
  }
}

/** 待审的认证申请：详情里取 status=1 的那条，没有则 null */
function pendingAuthOf(d: AdminAppUserDetailVO | null): AdminUserAuthVO | null {
  return d?.authList.find((a) => a.status === 1) ?? null
}

/* ── 封禁 ── */

const banOpen = ref(false)
const banTarget = ref<AdminAppUserVO | null>(null)
const banReason = ref('')
const banDays = ref<number>(0)

function openBan(row: AdminAppUserVO): void {
  banTarget.value = row
  banReason.value = ''
  banDays.value = 0
  banOpen.value = true
}

async function confirmBan(): Promise<void> {
  const row = banTarget.value
  if (!row) return
  if (!banReason.value.trim()) {
    message.warning('请填写封禁原因')
    return
  }
  const banUntil =
    banDays.value > 0
      ? new Date(Date.now() + banDays.value * 86400000).toISOString().slice(0, 19)
      : undefined
  await adminUserApi.ban(row.id, { reason: banReason.value.trim(), banUntil })
  banOpen.value = false
  message.success('已封禁并踢下线')
  void load()
}

const onUnban = (row: AdminAppUserVO): void => {
  Modal.confirm({
    title: `解封「${row.nickname ?? '该用户'}」？`,
    content: '解封后账号恢复正常，可重新登录。信用分不受影响——封禁本就没动过它。',
    okText: '解封',
    cancelText: '取消',
    onOk: async () => {
      await adminUserApi.unban(row.id)
      message.success('已解封')
      void load()
    }
  })
}

/* ── 信用分调整 ── */

const creditOpen = ref(false)
const creditTarget = ref<AdminAppUserVO | null>(null)
const creditDelta = ref(0)
const creditReason = ref('')

function openCredit(row: AdminAppUserVO): void {
  creditTarget.value = row
  creditDelta.value = 0
  creditReason.value = ''
  creditOpen.value = true
}

async function confirmCredit(): Promise<void> {
  const row = creditTarget.value
  if (!row) return
  if (!creditDelta.value) {
    message.warning('请填写调整幅度')
    return
  }
  if (!creditReason.value.trim()) {
    message.warning('请填写调整理由，流水里要能解释分数为什么变了')
    return
  }
  await adminUserApi.adjustCredit(row.id, { delta: creditDelta.value, reason: creditReason.value.trim() })
  creditOpen.value = false
  message.success('已调整')
  void load()
  if (detail.value?.id === row.id) void openDetail(row)
}

/* ── 认证审核（逐条，不做批量） ── */

const auditOpen = ref(false)
const auditTarget = ref<AdminUserAuthVO | null>(null)
const rejectReason = ref('')

function openAudit(auth: AdminUserAuthVO): void {
  auditTarget.value = auth
  rejectReason.value = ''
  auditOpen.value = true
}

async function passAuth(): Promise<void> {
  const a = auditTarget.value
  if (!a) return
  await adminUserApi.judgeAuth(a.id, { pass: true })
  auditOpen.value = false
  message.success(`${a.realName} 已通过学号认证`)
  void load()
}

async function rejectAuth(): Promise<void> {
  const a = auditTarget.value
  if (!a) return
  if (!rejectReason.value.trim()) {
    message.warning('请填写驳回原因，用户要据此重新提交')
    return
  }
  await adminUserApi.judgeAuth(a.id, { pass: false, rejectReason: rejectReason.value.trim() })
  auditOpen.value = false
  message.success('已驳回，用户可重新提交认证')
  void load()
}

/** 信用分低于 60：其商品强制人工审核 */
const isLowCredit = (score: number): boolean => score < 60

const authMeta = (code: number): { label: string; color: string } =>
  AUTH_STATUS_LABELS[AUTH_BY_CODE[code] ?? 'NONE']
const accountMeta = (code: number): { label: string; color: string } =>
  ACCOUNT_STATUS_LABELS[ACCOUNT_BY_CODE[code] ?? 'NORMAL']

function collegeName(id?: string | null): string {
  if (!id) return '未设置'
  return colleges.value.find((c) => c.id === id)?.name ?? '未设置'
}

function tintOf(id: string): [string, string] {
  const seed = Number(id.slice(-4)) || 7
  const palette: Array<[string, string]> = [
    ['#ffe9c7', '#fff6e6'],
    ['#dcecff', '#f0f7ff'],
    ['#e4f7ec', '#f3fcf7'],
    ['#f3e6ff', '#faf2ff']
  ]
  return palette[seed % palette.length]
}

function formatAt(iso?: string | null): string {
  return iso ? iso.replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => {
  void dictApi.colleges().then((rows) => (colleges.value = rows)).catch(() => undefined)
  void load()
})
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">用户管理</h1>
      <p class="cm-page__sub">
        学号认证逐条审核，不做批量通过；认证状态与账号状态相互独立。
      </p>
    </header>

    <!-- 筛选栏 -->
    <div class="cm-card users__filter">
      <a-space wrap :size="12">
        <a-input
          v-model:value="filters.keyword"
          allow-clear
          placeholder="昵称 / 手机号 / 学号"
          style="width: 220px"
          @press-enter="search"
        />
        <a-select v-model:value="filters.authStatus" style="width: 130px" @change="search">
          <a-select-option value="ALL">认证：全部</a-select-option>
          <a-select-option :value="0">未认证</a-select-option>
          <a-select-option :value="1">审核中</a-select-option>
          <a-select-option :value="2">已认证</a-select-option>
          <a-select-option :value="3">未通过</a-select-option>
        </a-select>
        <a-select v-model:value="filters.accountStatus" style="width: 130px" @change="search">
          <a-select-option value="ALL">账号：全部</a-select-option>
          <a-select-option :value="0">正常</a-select-option>
          <a-select-option :value="1">已警告</a-select-option>
          <a-select-option :value="2">已封禁</a-select-option>
        </a-select>
        <a-select v-model:value="filters.collegeId" style="width: 160px" @change="search">
          <a-select-option value="ALL">学院：全部</a-select-option>
          <a-select-option v-for="c in colleges" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
        </a-select>
        <a-button shape="round" @click="search">查询</a-button>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="users__count">共 {{ total }} 人</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{
        current: pageNum,
        pageSize,
        total,
        showTotal: (t: number) => `共 ${t} 条`,
        onChange: onPage
      }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'user'">
          <div class="users__who">
            <span class="users__avatar" :style="tintStyle(tintOf(record.id))">
              {{ (record.nickname ?? '?').slice(0, 1) }}
            </span>
            <span>
              <b>{{ record.nickname ?? '未命名' }}</b>
              <span class="users__real">{{ record.phoneMasked ?? '无手机号' }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'org'">
          {{ collegeName(record.collegeId) }}
        </template>

        <template v-else-if="column.key === 'credit'">
          <span :class="{ 'users__low': isLowCredit(record.creditScore) }">{{ record.creditScore }}</span>
          <span v-if="isLowCredit(record.creditScore)" class="users__flag">低</span>
        </template>

        <template v-else-if="column.key === 'auth'">
          <a-tag :color="authMeta(record.authStatus).color">
            {{ authMeta(record.authStatus).label }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'account'">
          <a-tag :color="accountMeta(record.status).color">
            {{ accountMeta(record.status).label }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'regAt'">
          <span class="users__muted">{{ formatAt(record.createTime).slice(0, 10) }}</span>
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.authStatus === 1"
              v-perm="PERM_CODES.USER_VERIFY"
              type="link"
              size="small"
              @click="openDetail(record)"
            >
              认证审核
            </a-button>
            <template v-if="record.status === 2">
              <a-button v-perm="PERM_CODES.USER_BAN" type="link" size="small" @click="onUnban(record)">
                解封
              </a-button>
            </template>
            <template v-else>
              <a-button v-perm="PERM_CODES.USER_BAN" type="link" size="small" danger @click="openBan(record)">
                封禁
              </a-button>
              <a-button v-perm="PERM_CODES.USER_BAN" type="link" size="small" @click="openCredit(record)">
                调分
              </a-button>
            </template>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 详情抽屉：认证历史 + 信用流水都在这里，回答「为什么是这个状态」 -->
    <a-drawer v-model:open="detailOpen" title="用户详情" :width="480">
      <a-spin v-if="!detail" />
      <template v-else>
        <div class="users__profile">
          <span class="users__avatar users__avatar--lg" :style="tintStyle(tintOf(detail.id))">
            {{ (detail.nickname ?? '?').slice(0, 1) }}
          </span>
          <div>
            <p class="users__name">{{ detail.nickname ?? '未命名' }}</p>
            <p class="users__muted">
              {{ detail.phone ?? '无手机号' }} · {{ collegeName(detail.collegeId) }}
            </p>
          </div>
        </div>

        <a-descriptions :column="1" bordered size="small" class="users__desc">
          <a-descriptions-item label="真实姓名">
            {{ detail.realName ?? '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="学号">{{ detail.studentNo ?? '—' }}</a-descriptions-item>
          <a-descriptions-item label="信用分">
            {{ detail.creditScore }}
            <span v-if="isLowCredit(detail.creditScore)" class="users__low">（低于 60，商品强制人工审核）</span>
          </a-descriptions-item>
          <a-descriptions-item label="认证状态">
            {{ authMeta(detail.authStatus).label }}
          </a-descriptions-item>
          <a-descriptions-item label="账号状态">
            {{ accountMeta(detail.status).label }}
          </a-descriptions-item>
          <a-descriptions-item v-if="detail.status === 2" label="封禁">
            {{ detail.banReason ?? '未填原因' }}
            <span class="users__muted">
              （{{ detail.banUntil ? `到期 ${formatAt(detail.banUntil)}` : '永久' }}）
            </span>
          </a-descriptions-item>
          <a-descriptions-item label="发布 / 订单">
            {{ detail.itemCount }} 件 / {{ detail.orderCount }} 单
          </a-descriptions-item>
          <a-descriptions-item label="注册时间">{{ formatAt(detail.createTime) }}</a-descriptions-item>
          <a-descriptions-item label="最近登录">{{ formatAt(detail.lastLoginAt) || '—' }}</a-descriptions-item>
        </a-descriptions>

        <a-button
          v-if="pendingAuthOf(detail)"
          v-perm="PERM_CODES.USER_VERIFY"
          type="primary"
          block
          @click="openAudit(pendingAuthOf(detail)!)"
        >
          审核待处理的认证申请
        </a-button>

        <h3 class="users__sub">认证历史</h3>
        <a-empty v-if="detail.authList.length === 0" description="没有提交过认证" />
        <a-timeline v-else>
          <a-timeline-item
            v-for="a in detail.authList"
            :key="a.id"
            :color="a.status === 2 ? 'green' : a.status === 3 ? 'red' : 'blue'"
          >
            <b>{{ a.realName }} · {{ a.studentNo }}</b>
            <span class="users__muted">
              （{{ a.status === 1 ? '审核中' : a.status === 2 ? '已通过' : '已驳回' }}，
              {{ formatAt(a.createTime).slice(0, 10) }} 提交）
            </span>
            <p v-if="a.rejectReason" class="users__muted">驳回原因：{{ a.rejectReason }}</p>
          </a-timeline-item>
        </a-timeline>

        <h3 class="users__sub">信用分流水</h3>
        <a-empty v-if="detail.creditLogs.length === 0" description="暂无变动记录" />
        <a-timeline v-else>
          <a-timeline-item
            v-for="l in detail.creditLogs"
            :key="l.id"
            :color="l.changeValue > 0 ? 'green' : 'red'"
          >
            <span class="users__delta" :class="{ 'is-up': l.changeValue > 0 }">
              {{ l.changeValue > 0 ? `+${l.changeValue}` : l.changeValue }}
            </span>
            {{ l.remark ?? l.reasonType }}
            <span class="users__muted">
              （{{ formatAt(l.createTime).slice(0, 10) }}，{{ l.beforeScore }} → {{ l.afterScore }}）
            </span>
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-drawer>

    <!-- 封禁 -->
    <a-modal
      v-model:open="banOpen"
      title="封禁账号"
      ok-text="确认封禁"
      cancel-text="取消"
      :ok-button-props="{ danger: true }"
      @ok="confirmBan"
    >
      <p class="users__check">
        封禁后该用户立即下线，不能发布与下单；已发布商品仍显示在售。
        <b>信用分不变</b>——封禁是账号处置，不是交易行为评价。
      </p>
      <a-textarea v-model:value="banReason" :rows="3" placeholder="封禁原因（必填，会记入操作日志）" />
      <a-select v-model:value="banDays" style="width: 100%; margin-top: 12px">
        <a-select-option :value="0">永久封禁</a-select-option>
        <a-select-option :value="7">7 天后自动到期</a-select-option>
        <a-select-option :value="30">30 天后自动到期</a-select-option>
      </a-select>
    </a-modal>

    <!-- 信用调整 -->
    <a-modal
      v-model:open="creditOpen"
      title="调整信用分"
      ok-text="确认调整"
      cancel-text="取消"
      @ok="confirmCredit"
    >
      <p class="users__check">
        当前 {{ creditTarget?.creditScore ?? 0 }} 分。单次最多 ±20，结果夹在 0–120；
        每次调整都会写一条流水。
      </p>
      <a-input-number v-model:value="creditDelta" :min="-20" :max="20" style="width: 140px" />
      <a-textarea
        v-model:value="creditReason"
        :rows="2"
        style="margin-top: 12px"
        placeholder="调整理由（必填，会进 credit_log，答辩时能解释分数为什么变了）"
      />
    </a-modal>

    <!-- 认证审核：逐条看证件 -->
    <a-modal
      v-model:open="auditOpen"
      title="学号认证审核"
      ok-text="通过"
      cancel-text="驳回"
      @ok="passAuth"
      @cancel="rejectAuth"
    >
      <template v-if="auditTarget">
        <p class="users__check">
          核对三项：<b>姓名 / 学号 / 学院</b> 与证件是否一致。通过时会校验该学号是否已被其他账号占用。
        </p>
        <a-descriptions :column="1" bordered size="small" class="users__desc">
          <a-descriptions-item label="申请人">{{ auditTarget.nickname ?? auditTarget.userId }}</a-descriptions-item>
          <a-descriptions-item label="姓名">{{ auditTarget.realName }}</a-descriptions-item>
          <a-descriptions-item label="学号">{{ auditTarget.studentNo }}</a-descriptions-item>
          <a-descriptions-item label="学院">{{ collegeName(auditTarget.collegeId) }}</a-descriptions-item>
        </a-descriptions>
        <div class="users__material">
          <img v-if="auditTarget.credentialUrl" :src="auditTarget.credentialUrl" alt="认证凭证" />
          <span v-else>未上传凭证（需人工确认身份）</span>
        </div>
        <a-textarea
          v-model:value="rejectReason"
          :rows="2"
          placeholder="驳回原因（点「驳回」时必填，用户据此重新提交）"
        />
      </template>
    </a-modal>
  </div>
</template>

<style scoped>
.users__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  padding: var(--cm-space-4) var(--cm-space-5);
  margin-bottom: var(--cm-space-4);
}

.users__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.users__who {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.users__avatar {
  width: 30px;
  height: 30px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: var(--cm-font-size-sm);
  font-weight: 500;
  color: var(--cm-color-text-1);
}

.users__avatar--lg {
  width: 48px;
  height: 48px;
  font-size: var(--cm-font-size-lg);
}

.users__real {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.users__muted {
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-xs);
}

/* 低信用：红字 + 角标，因为「低于 60 强制人工审核」是硬规则，必须一眼看到 */
.users__low {
  color: var(--cm-color-red-600, #d92c2c);
  font-weight: 500;
}

.users__flag {
  margin-left: var(--cm-space-2);
  padding: 0 6px;
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-red-100, #fcecec);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

.users__profile {
  display: flex;
  align-items: center;
  gap: var(--cm-space-4);
  margin-bottom: var(--cm-space-5);
}

.users__name {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.users__desc {
  margin-bottom: var(--cm-space-5);
}

.users__sub {
  margin: var(--cm-space-5) 0 var(--cm-space-3);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.users__delta {
  display: inline-block;
  min-width: 36px;
  font-variant-numeric: tabular-nums;
  color: var(--cm-color-red-600, #d92c2c);
}

.users__delta.is-up {
  color: var(--cm-color-green-600, #1d9e75);
}

.users__check {
  margin: 0 0 var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

/* 证件区：够大才看得出「逐条核对」这件事 */
.users__material {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: var(--cm-space-4) 0;
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-bg-subtle, #f6f6f8);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
  overflow: hidden;
}

.users__material img {
  max-width: 100%;
  max-height: 320px;
  object-fit: contain;
}
</style>
