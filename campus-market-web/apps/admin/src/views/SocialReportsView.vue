<script setup lang="ts">
/**
 * 社交举报（notes/22 批次 3）—— 动态与评论的举报处置台
 *
 * <p>**为什么不放进审核队列**：队列的排序权重按「商品举报次数」累加、SLA 按审核单据统计，
 * 动态和评论解析不出商品，混进去会污染排序规则。所以单开一页，处置动作只有一个：下架内容。
 *
 * <p>**裁决即处置**，没有中间态：成立就把 `audit_status` 置为「举报下架」，
 * 内容随即从广场流与评论列表消失，并给作者推一条系统通知；不成立什么都不动。
 *
 * <p>权限借用审核的两个点（查询 `review.view`、处置 `review.judge`），
 * 只读角色能看不能动——按钮挂 `v-perm`。
 */

import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES } from '@campus/shared'
import { adminSocialReportApi, type SocialReportVO } from '@campus/api'
import { formatRelativeTime } from '@campus/shared'

const STATUS_LABELS: Record<number, string> = { 1: '待受理', 2: '处理中', 3: '已处理' }
const STATUS_COLORS: Record<number, string> = { 1: 'orange', 2: 'blue', 3: 'default' }
const TYPE_OPTIONS = [
  { label: '全部类型', value: undefined },
  { label: '动态', value: 3 },
  { label: '商品评论', value: 4 },
  { label: '动态评论', value: 5 }
] as const
const STATUS_OPTIONS = [
  { label: '全部状态', value: undefined },
  { label: '待受理', value: 1 },
  { label: '已处理', value: 3 }
] as const

const rows = ref<SocialReportVO[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const statusFilter = ref<number | undefined>(undefined)
const typeFilter = ref<number | undefined>(undefined)

const columns: TableColumnsType = [
  { title: '类型', dataIndex: 'targetTypeName', width: 96 },
  { title: '被举报内容', dataIndex: 'targetSummary', ellipsis: true },
  { title: '作者', dataIndex: 'targetAuthorNick', width: 110 },
  { title: '举报人', dataIndex: 'reporterNick', width: 110 },
  { title: '原因', dataIndex: 'reason', width: 120, ellipsis: true },
  { title: '状态', key: 'status', width: 96 },
  { title: '提交时间', key: 'createTime', width: 130 },
  { title: '操作', key: 'act', width: 160 }
]

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await adminSocialReportApi.list({
      status: statusFilter.value,
      targetType: typeFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    rows.value = p.list
    total.value = Number(p.total)
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

onMounted(load)

function onFilter(): void {
  pageNum.value = 1
  void load()
}

function onPage(p: number): void {
  pageNum.value = p
  void load()
}

/* ── 裁决弹窗 ── */

const dlgOpen = ref(false)
const acting = ref<SocialReportVO | null>(null)
const result = ref<1 | 2>(1)
const reason = ref('')
const note = ref('')
const submitting = ref(false)

function openHandle(row: SocialReportVO): void {
  acting.value = row
  result.value = 1
  reason.value = '内容违规'
  note.value = ''
  dlgOpen.value = true
}

async function submit(): Promise<void> {
  if (!acting.value || submitting.value) return
  // 成立要下架内容，处置依据必须留下——操作日志与通知文案都用它
  if (result.value === 1 && !reason.value.trim()) {
    message.warning('举报成立需要填写处置依据')
    return
  }
  submitting.value = true
  try {
    await adminSocialReportApi.handle(acting.value.id, {
      result: result.value,
      reason: result.value === 1 ? reason.value.trim() : undefined,
      note: note.value.trim() || undefined
    })
    message.success(result.value === 1 ? '已下架内容并通知作者' : '已裁定不成立')
    dlgOpen.value = false
    await load()
  } catch {
    // 请求层已提示（3001 已处理等）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="social">
    <header class="social__hd">
      <div>
        <h2>社交举报</h2>
        <p class="social__sub">动态与评论的举报单。成立即下架内容并通知作者，不做中间受理态。</p>
      </div>
      <div class="social__filters">
        <a-select
          v-model:value="typeFilter"
          :options="TYPE_OPTIONS as unknown as { label: string; value: number }[]"
          style="width: 140px"
          @change="onFilter"
        />
        <a-select
          v-model:value="statusFilter"
          :options="STATUS_OPTIONS as unknown as { label: string; value: number }[]"
          style="width: 140px"
          @change="onFilter"
        />
        <a-button :loading="loading" @click="load">刷新</a-button>
      </div>
    </header>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="{
        current: pageNum,
        pageSize,
        total,
        showSizeChanger: false,
        onChange: onPage
      }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status]">{{ STATUS_LABELS[record.status] ?? record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="social__time">{{ formatRelativeTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'act'">
          <a-button
            v-if="record.status !== 3"
            v-perm="PERM_CODES.REVIEW_JUDGE"
            type="link"
            size="small"
            @click="openHandle(record)"
          >
            裁决
          </a-button>
          <span v-else class="social__done">已处理</span>
        </template>
      </template>
    </a-table>

    <a-modal v-model:open="dlgOpen" title="举报裁决" :confirm-loading="submitting" @ok="submit">
      <div class="dlg">
        <p class="dlg__target">
          <a-tag>{{ acting?.targetTypeName }}</a-tag>
          {{ acting?.targetSummary || '（内容已删除）' }}
        </p>
        <p class="dlg__meta">
          作者 {{ acting?.targetAuthorNick || '—' }} · 举报人 {{ acting?.reporterNick || '—' }} ·
          原因「{{ acting?.reason }}」
        </p>

        <a-radio-group v-model:value="result">
          <a-radio :value="1">成立（下架内容）</a-radio>
          <a-radio :value="2">不成立（内容保留）</a-radio>
        </a-radio-group>

        <template v-if="result === 1">
          <a-input v-model:value="reason" placeholder="处置依据（成立时必填）" />
          <a-input v-model:value="note" placeholder="补充说明（会写进通知与操作日志）" />
        </template>
        <p class="dlg__tip">成立会通知作者；不成立不影响内容。</p>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.social {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

.social__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

.social__hd h2 {
  margin: 0 0 4px;
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.social__sub {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.social__filters {
  display: flex;
  gap: var(--cm-space-2);
}

.social__time,
.social__done {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.dlg {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.dlg__target {
  margin: 0;
  padding: var(--cm-space-3);
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-md);
  word-break: break-word;
}

.dlg__meta,
.dlg__tip {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
