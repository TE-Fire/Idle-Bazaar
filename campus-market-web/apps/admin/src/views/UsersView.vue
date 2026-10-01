<script setup lang="ts">
/**
 * A4 用户管理（business/03 A-B5 / F-A5）
 *
 * <p>三条来自业务文档的硬约束，页面必须体现：
 *   ① **认证审核不做批量通过**——误通过会破坏整个校园信任机制（F-A5 判据），
 *      所以认证只能逐条看证件，不提供「全选通过」；
 *   ② **认证状态与账号状态是两件事**：认证没过的人照样能正常登录，
 *      封禁的人认证可能是通过的，所以它们分成两列，不合并成一个「状态」；
 *   ③ 信用分 < 60 的商品**强制人工审核**（§4.5），所以低信用要显眼地标出来。
 *
 * <p>写操作都带 `v-perm`：user.verify 管认证，user.ban 管封禁与警告。
 * 审核员角色两者都没有，页面上就只剩「详情」——这正是要验证的隔离效果。
 */

import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import {
  ACCOUNT_STATUS_LABELS,
  AUTH_STATUS_LABELS,
  PERM_CODES,
  type AccountStatus,
  type AppUser,
  type AuthStatus
} from '@campus/shared'
import { USERS, creditLogsOf } from '@/mock/users'
import { COLLEGES, campusName, collegeName } from '@/mock/dict'
import { tintStyle } from '@/utils/tint'

const loading = ref(false)
const list = ref<AppUser[]>(USERS.map((u) => ({ ...u })))

const filters = reactive<{
  keyword: string
  authStatus: AuthStatus | 'ALL'
  accountStatus: AccountStatus | 'ALL'
  collegeId: string | 'ALL'
}>({
  keyword: '',
  authStatus: 'ALL',
  accountStatus: 'ALL',
  collegeId: 'ALL'
})

const view = computed<AppUser[]>(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return list.value.filter((u) => {
    if (filters.authStatus !== 'ALL' && u.authStatus !== filters.authStatus) return false
    if (filters.accountStatus !== 'ALL' && u.accountStatus !== filters.accountStatus) return false
    if (filters.collegeId !== 'ALL' && u.collegeId !== filters.collegeId) return false
    if (!kw) return true
    return (
      u.nick.toLowerCase().includes(kw) ||
      u.realName.toLowerCase().includes(kw) ||
      u.studentNo.includes(kw)
    )
  })
})

const reset = (): void => {
  filters.keyword = ''
  filters.authStatus = 'ALL'
  filters.accountStatus = 'ALL'
  filters.collegeId = 'ALL'
}

const columns: TableColumnsType<AppUser> = [
  { title: '用户', key: 'user', width: 200 },
  { title: '学号', dataIndex: 'studentNo', width: 120 },
  { title: '学院 / 校区', key: 'org', width: 180 },
  { title: '信用分', key: 'credit', width: 110 },
  { title: '认证状态', key: 'auth', width: 110 },
  { title: '账号状态', key: 'account', width: 110 },
  { title: '发布 / 成交', key: 'counts', width: 110 },
  { title: '操作', key: 'action', width: 220 }
]

/* ── 详情抽屉 ── */

const detailOpen = ref(false)
const current = ref<AppUser | null>(null)

const openDetail = (row: AppUser): void => {
  current.value = row
  detailOpen.value = true
}

const creditLogs = computed(() => (current.value ? creditLogsOf(current.value.id) : []))

/* ── 认证审核（逐条，不做批量） ── */

const auditOpen = ref(false)
const rejectReason = ref('')

const openAudit = (row: AppUser): void => {
  current.value = row
  rejectReason.value = ''
  auditOpen.value = true
}

const passAuth = (): void => {
  const u = current.value
  if (!u) return
  u.authStatus = 'PASSED'
  auditOpen.value = false
  message.success(`${u.realName} 已通过学号认证`)
}

const rejectAuth = (): void => {
  const u = current.value
  if (!u) return
  if (!rejectReason.value.trim()) {
    message.warning('请填写驳回原因，用户要据此重新提交')
    return
  }
  u.authStatus = 'REJECTED'
  u.rejectReason = rejectReason.value.trim()
  auditOpen.value = false
  message.success('已驳回，用户可重新提交认证')
}

/* ── 封禁 / 解封 / 警告 ── */

const onBan = (row: AppUser): void => {
  Modal.confirm({
    title: `封禁「${row.nick}」？`,
    content: '封禁后该用户不能发布商品与下单，已发布的商品保持在售但不可编辑。可在用户列表解封。',
    okText: '封禁',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => {
      row.accountStatus = 'BANNED'
      message.success('已封禁')
    }
  })
}

const onUnban = (row: AppUser): void => {
  row.accountStatus = 'NORMAL'
  message.success('已解封')
}

const onWarn = (row: AppUser): void => {
  row.accountStatus = 'WARNED'
  message.warning('已发出警告（会计入信用分）')
}

/** 信用分低于 60：其商品强制人工审核（business/03 §4.5） */
const isLowCredit = (score: number): boolean => score < 60

/*
 * 表格插槽里的 `record` 在 antd 4.2.6 的类型里是 any，直接写
 * `authMeta(record.authStatus)` 会被判为隐式 any 索引（TS7053）。
 * 收进这两个小函数里让参数带上类型，索引就安全了——比在模板里到处 as AppUser 干净。
 */
const authMeta = (s: AuthStatus): { label: string; color: string } => AUTH_STATUS_LABELS[s]
const accountMeta = (s: AccountStatus): { label: string; color: string } => ACCOUNT_STATUS_LABELS[s]
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
          placeholder="昵称 / 姓名 / 学号"
          style="width: 220px"
        />
        <a-select v-model:value="filters.authStatus" style="width: 130px">
          <a-select-option value="ALL">认证：全部</a-select-option>
          <a-select-option value="NONE">未认证</a-select-option>
          <a-select-option value="PENDING">审核中</a-select-option>
          <a-select-option value="PASSED">已认证</a-select-option>
          <a-select-option value="REJECTED">未通过</a-select-option>
        </a-select>
        <a-select v-model:value="filters.accountStatus" style="width: 130px">
          <a-select-option value="ALL">账号：全部</a-select-option>
          <a-select-option value="NORMAL">正常</a-select-option>
          <a-select-option value="WARNED">已警告</a-select-option>
          <a-select-option value="BANNED">已封禁</a-select-option>
        </a-select>
        <a-select v-model:value="filters.collegeId" style="width: 160px">
          <a-select-option value="ALL">学院：全部</a-select-option>
          <a-select-option v-for="c in COLLEGES" :key="c.id" :value="c.id">{{ c.name }}</a-select-option>
        </a-select>
        <a-button shape="round" @click="reset">重置</a-button>
      </a-space>
      <span class="users__count">共 {{ view.length }} 人</span>
    </div>

    <a-table
      :columns="columns"
      :data-source="view"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{ pageSize: 10, showTotal: (t: number) => `共 ${t} 条` }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'user'">
          <div class="users__who">
            <span class="users__avatar" :style="tintStyle(record.tints)">
              {{ record.nick.slice(0, 1) }}
            </span>
            <span>
              <b>{{ record.nick }}</b>
              <span class="users__real">{{ record.realName }}</span>
            </span>
          </div>
        </template>

        <template v-else-if="column.key === 'org'">
          {{ collegeName(record.collegeId) }}
          <span class="users__muted">· {{ campusName(record.campusId) }}</span>
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
          <a-tag :color="accountMeta(record.accountStatus).color">
            {{ accountMeta(record.accountStatus).label }}
          </a-tag>
        </template>

        <template v-else-if="column.key === 'counts'">
          {{ record.itemCount }} / {{ record.orderCount }}
        </template>

        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
            <a-button type="link" size="small" @click="openDetail(record)">详情</a-button>
            <a-button
              v-if="record.authStatus === 'PENDING'"
              v-perm="PERM_CODES.USER_VERIFY"
              type="link"
              size="small"
              @click="openAudit(record)"
            >
              认证审核
            </a-button>
            <template v-if="record.accountStatus === 'BANNED'">
              <a-button v-perm="PERM_CODES.USER_BAN" type="link" size="small" @click="onUnban(record)">
                解封
              </a-button>
            </template>
            <template v-else>
              <a-button
                v-perm="PERM_CODES.USER_BAN"
                type="link"
                size="small"
                danger
                @click="onBan(record)"
              >
                封禁
              </a-button>
              <a-button
                v-if="record.accountStatus !== 'WARNED'"
                v-perm="PERM_CODES.USER_BAN"
                type="link"
                size="small"
                @click="onWarn(record)"
              >
                警告
              </a-button>
            </template>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 详情抽屉 -->
    <a-drawer v-model:open="detailOpen" title="用户详情" :width="440">
      <template v-if="current">
        <div class="users__profile">
          <span class="users__avatar users__avatar--lg" :style="tintStyle(current.tints)">
            {{ current.nick.slice(0, 1) }}
          </span>
          <div>
            <p class="users__name">{{ current.nick }}（{{ current.realName }}）</p>
            <p class="users__muted">{{ current.studentNo }} · {{ collegeName(current.collegeId) }}</p>
          </div>
        </div>

        <a-descriptions :column="1" bordered size="small" class="users__desc">
          <a-descriptions-item label="校区">{{ campusName(current.campusId) }}</a-descriptions-item>
          <a-descriptions-item label="信用分">
            {{ current.creditScore }}
            <span v-if="isLowCredit(current.creditScore)" class="users__low">（低于 60，商品强制人工审核）</span>
          </a-descriptions-item>
          <a-descriptions-item label="认证状态">
            {{ authMeta(current.authStatus).label }}
          </a-descriptions-item>
          <a-descriptions-item label="账号状态">
            {{ accountMeta(current.accountStatus).label }}
          </a-descriptions-item>
          <a-descriptions-item label="发布 / 成交">
            {{ current.itemCount }} 件 / {{ current.orderCount }} 单
          </a-descriptions-item>
          <a-descriptions-item v-if="current.rejectReason" label="上次驳回原因">
            {{ current.rejectReason }}
          </a-descriptions-item>
        </a-descriptions>

        <h3 class="users__sub">信用分流水</h3>
        <a-empty v-if="creditLogs.length === 0" description="暂无变动记录" />
        <a-timeline v-else>
          <a-timeline-item v-for="l in creditLogs" :key="l.id" :color="l.delta > 0 ? 'green' : 'red'">
            <span class="users__delta" :class="{ 'is-up': l.delta > 0 }">
              {{ l.delta > 0 ? `+${l.delta}` : l.delta }}
            </span>
            {{ l.reason }}
            <span class="users__muted">（{{ l.at.slice(0, 10) }}，变动后 {{ l.after }}）</span>
          </a-timeline-item>
        </a-timeline>
      </template>
    </a-drawer>

    <!-- 认证审核：逐条看证件 -->
    <a-modal
      v-model:open="auditOpen"
      title="学号认证审核"
      ok-text="通过"
      cancel-text="驳回"
      :ok-button-props="{ disabled: false }"
      @ok="passAuth"
      @cancel="rejectAuth"
    >
      <template v-if="current">
        <p class="users__check">
          核对三项：<b>姓名 / 学号 / 学院</b> 与证件是否一致。一学号多账号转为人工标记。
        </p>
        <a-descriptions :column="1" bordered size="small" class="users__desc">
          <a-descriptions-item label="姓名">{{ current.realName }}</a-descriptions-item>
          <a-descriptions-item label="学号">{{ current.studentNo }}</a-descriptions-item>
          <a-descriptions-item label="学院">{{ collegeName(current.collegeId) }}</a-descriptions-item>
        </a-descriptions>
        <div class="users__material" :style="tintStyle(current.material?.tints ?? current.tints)">
          {{ current.material?.kind ?? '学生证' }}（图片占位）
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

/* 证件占位：够大才看得出「逐条核对」这件事 */
.users__material {
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: var(--cm-space-4) 0;
  border-radius: var(--cm-radius-md);
  color: var(--cm-color-text-2);
  font-size: var(--cm-font-size-sm);
}
</style>
