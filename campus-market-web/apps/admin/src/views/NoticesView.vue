<script setup lang="ts">
/**
 * A11 平台公告（business/03 A-B13 / F-A12，技术口径 notes/22 S12）
 *
 * <p>**与处置通知不是一回事**：那条是某次裁决的副产品（`type=4`），这条是运营主动对外说的话
 * （`type=6`）。学生端通知页正是靠 `type` 分标签，混用会让「你的动态被下架了」和
 * 「期末跳蚤市场开始报名」长得一样。
 *
 * <p>**发布即送达**：没有定时器；收件人在后端按范围一次性查出、逐行写 `msg_message`，
 * 一条公告一个 `batchId`。列表按批次聚合，所以这里一行的 `receiverCount` 是写下的行数。
 *
 * <p>**撤回是软删收件行**：学生端立刻看不到，本页那一行仍保留并标「已撤回」——
 * 凭空消失会让人怀疑自己是不是没发过。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES } from '@campus/shared'
import { adminNoticeApi, dictApi, type AdminNoticeVO, type DictCampusVO } from '@campus/api'
import { formatRelativeTime } from '@campus/shared'

const rows = ref<AdminNoticeVO[]>([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const campuses = ref<DictCampusVO[]>([])

const campusName = (id?: string | null): string =>
  !id ? '全员' : campuses.value.find((c) => c.id === id)?.name ?? `校区 ${id}`

const columns: TableColumnsType = [
  { title: '标题 / 正文', key: 'content', ellipsis: true },
  { title: '范围', key: 'scope', width: 120 },
  { title: '发送人数', dataIndex: 'receiverCount', width: 100 },
  { title: '已读', key: 'read', width: 100 },
  { title: '发布时间', key: 'createTime', width: 130 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'act', width: 100 }
]

async function load(): Promise<void> {
  loading.value = true
  try {
    const p = await adminNoticeApi.list(pageNum.value, pageSize.value)
    rows.value = p.list
    total.value = Number(p.total)
  } catch {
    // 请求层已提示
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  void load()
  try {
    campuses.value = await dictApi.campuses()
  } catch {
    // 拿不到校区就只显示「全员」，不影响列表
  }
})

function onPage(p: number): void {
  pageNum.value = p
  void load()
}

/* ── 发布弹窗 ── */

const dlgOpen = ref(false)
const submitting = ref(false)
const form = reactive<{ title: string; content: string; campusId?: string }>({
  title: '',
  content: '',
  campusId: undefined
})

const campusOptions = computed(() => [
  { label: '全员（全部校区）', value: '' },
  ...campuses.value.filter((c) => c.enabled).map((c) => ({ label: c.name, value: c.id }))
])

function openPublish(): void {
  form.title = ''
  form.content = ''
  form.campusId = undefined
  dlgOpen.value = true
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!form.content.trim()) {
    message.warning('公告正文不能为空')
    return
  }
  submitting.value = true
  try {
    await adminNoticeApi.publish({
      title: form.title.trim() || undefined,
      content: form.content.trim(),
      // 下拉里「全员」的值是空串，转成 undefined 才是后端的"不限校区"
      campusId: form.campusId || undefined
    })
    message.success('已发布，学生端通知页立即可见')
    dlgOpen.value = false
    pageNum.value = 1
    await load()
  } catch {
    // 请求层已提示（4034 接收人超限 / 4024 敏感词）
  } finally {
    submitting.value = false
  }
}

/* ── 撤回 ── */

function confirmRevoke(row: AdminNoticeVO): void {
  Modal.confirm({
    title: '撤回这条公告？',
    content: `「${row.title || (row.content ?? '').slice(0, 20)}」将从 ${
      row.aliveCount ?? 0
    } 个学生的通知里消失，已读记录一并作废，操作不可撤销。`,
    okText: '撤回',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      await adminNoticeApi.revoke(row.batchId)
      message.success('已撤回')
      await load()
    }
  })
}
</script>

<template>
  <div class="notice">
    <header class="notice__hd">
      <div>
        <h2>平台公告</h2>
        <p class="notice__sub">
          发布即送达学生端通知页。撤回只删学生的收件行，本页保留记录并标「已撤回」。
        </p>
      </div>
      <div class="notice__filters">
        <a-button :loading="loading" @click="load">刷新</a-button>
        <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="primary" @click="openPublish">
          发布公告
        </a-button>
      </div>
    </header>

    <a-table
      :columns="columns"
      :data-source="rows"
      :loading="loading"
      row-key="batchId"
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
        <template v-if="column.key === 'content'">
          <p class="notice__title">{{ record.title || '（无标题）' }}</p>
          <p class="notice__body">{{ record.content }}</p>
        </template>
        <template v-else-if="column.key === 'scope'">
          <a-tag :color="record.campusId ? 'blue' : 'default'">
            {{ campusName(record.campusId) }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'read'">
          <span class="notice__num">
            {{ record.revoked ? 0 : record.readCount }} / {{ record.receiverCount }}
          </span>
        </template>
        <template v-else-if="column.key === 'createTime'">
          <span class="notice__time">{{ formatRelativeTime(record.createTime) }}</span>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag v-if="record.revoked" color="default">已撤回</a-tag>
          <a-tag v-else color="green">已发布</a-tag>
        </template>
        <template v-else-if="column.key === 'act'">
          <a-button
            v-if="!record.revoked"
            v-perm="PERM_CODES.CONTENT_EDIT"
            type="link"
            size="small"
            danger
            @click="confirmRevoke(record)"
          >
            撤回
          </a-button>
          <span v-else class="notice__time">—</span>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="dlgOpen"
      title="发布平台公告"
      :confirm-loading="submitting"
      ok-text="发布"
      @ok="submit"
    >
      <div class="dlg">
        <a-input v-model:value="form.title" :maxlength="60" placeholder="标题（可空，60 字内）" />
        <a-textarea
          v-model:value="form.content"
          :rows="6"
          :maxlength="2000"
          show-count
          placeholder="正文，纯文本，2000 字内"
        />
        <a-select
          v-model:value="form.campusId"
          :options="campusOptions"
          placeholder="接收范围：全员 / 指定校区"
          allow-clear
        />
        <p class="dlg__tip">
          封禁账号不在接收范围内。单次上限 5000 人——超了请按校区分批发。
        </p>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.notice {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

.notice__hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

.notice__hd h2 {
  margin: 0 0 4px;
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.notice__sub {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.notice__filters {
  display: flex;
  gap: var(--cm-space-2);
}

.notice__title {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
}

.notice__body {
  margin: 2px 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.notice__num {
  font-size: var(--cm-font-size-sm);
  font-variant-numeric: tabular-nums;
}

.notice__time {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.dlg {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.dlg__tip {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
