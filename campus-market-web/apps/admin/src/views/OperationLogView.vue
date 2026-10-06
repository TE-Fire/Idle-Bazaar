<script setup lang="ts">
/**
 * A9 操作日志（business/03 F-A11，接口 `/admin/log/list`）
 *
 * <p>为什么要独立成页（2026-10-01 从 A9 系统设置里拆出）：
 *   ① 高频查询场景（巡盘 / 追责 / 答辩演示审计留痕）藏在低频配置项里不好找；
 *   ② 页面门禁只能用 log.view，于是「运营」（有 log.view、无 settings.edit）
 *      能进系统设置页，里面却放着 SLA 阈值与敏感词的编辑表单。
 *      拆开后：本页 log.view，系统设置页 settings.edit，权限才对得上矩阵。
 *
 * <p>业务口径（F-A11）：记录**有业务语义的写操作**（审核 / 举报 / 申诉 / 下架 / 字典 / 角色），
 * 读操作不在这里——那是访问日志的活（Filter 落日志文件），两者分工不可互相替代。
 * 本表只追加、不可删改，所以本页**只读**：没有编辑、没有删除，后端也没提供。
 *
 * <p>2026-10-06 从 mock 换成真接口。两处与后端的分工值得记住：
 *   - `module` / `opType` 后端只给**英文码**，中文标签在前端
 *     （`LOG_MODULE_LABELS` / `OP_TYPE_LABELS`，见 `packages/shared/src/admin.ts`）。
 *   - 时间筛选传**日期**（`yyyy-MM-dd`），后端把起止展开成当天 00:00:00 / 23:59:59；
 *     前端自己拼时分秒反而绑不上。
 */

import { onMounted, reactive, ref } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { LOG_MODULES, LOG_MODULE_LABELS, OP_TYPE_LABELS, ROLE_META } from '@campus/shared'
import type { RoleCode } from '@campus/shared'
import { logApi, type OperationLogVO } from '@campus/api'

const PAGE_SIZE = 15

/* ── 数据源 ── */

const rows = ref<OperationLogVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)

/**
 * 时间区间。`value-format="YYYY-MM-DD"` 已把 Date 转成字符串，所以这里直接存字符串数组，
 * 不引 dayjs（它是 antd 的传递依赖，pnpm 严格模式下不该由我们直接 import）。
 */
const dateRange = ref<[string, string] | undefined>(undefined)

const filters = reactive<{
  module: string
  result: '' | 1 | 0
  keyword: string
}>({ module: '', result: '', keyword: '' })

const queryParams = () => ({
  module: filters.module || undefined,
  // result 可能是 0（筛失败），不能用真值判断
  result: filters.result === '' ? undefined : filters.result,
  keyword: filters.keyword.trim() || undefined,
  startTime: dateRange.value?.[0] || undefined,
  endTime: dateRange.value?.[1] || undefined,
  pageNum: pageNum.value,
  pageSize: PAGE_SIZE
})

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const r = await logApi.list(queryParams())
    rows.value = r?.list ?? []
    total.value = r?.total ?? 0
  } catch {
    // 请求层已提示（含 traceId）。保留上一次的数据不清空：
    // 拉失败时看到旧数据，比看到一张空表更容易判断是「没拉到」而不是「真没有」
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

/** 筛选条件变了要回到第一页：停在第 3 页改条件会看到「没有数据」的错觉 */
const onSearch = (): void => {
  pageNum.value = 1
  void load()
}

const reset = (): void => {
  filters.module = ''
  filters.result = ''
  filters.keyword = ''
  dateRange.value = undefined
  pageNum.value = 1
  void load()
}

const onPageChange = (page: number): void => {
  pageNum.value = page
  void load()
}

/**
 * 清空时 antd 给的 dateString 是 `['', '']`（不是 null），
 * 不收敛成 undefined 就会往后端发 `startTime=`，虽然后端也当空处理，
 * 但 `dateRange` 里留着空串会让「重置」按钮的判断变得含糊。
 */
const onDateChange = (_value: unknown, dateString: [string, string]): void => {
  dateRange.value = dateString[0] || dateString[1] ? dateString : undefined
}

/* ── 展示辅助 ── */

/** 模块配色只用于快速扫读；模块名文字本身也在标签里，不靠颜色单独表意 */
const MODULE_COLORS: Record<string, string> = {
  item: 'geekblue',
  review: 'blue',
  content: 'green',
  system: 'red',
  order: 'magenta'
}

const moduleLabel = (code: string): string => LOG_MODULE_LABELS[code] ?? code

/** 兜底显示原码：标签表漏配时看到一个英文码，好过看到空白 */
const opTypeLabel = (code: string): string => OP_TYPE_LABELS[code] ?? code

const roleLabel = (code?: string | null): string =>
  code ? (ROLE_META[code as RoleCode]?.label ?? code) : '—'

const roleColor = (code?: string | null): string =>
  code ? (ROLE_META[code as RoleCode]?.color ?? 'default') : 'default'

/** 脱敏后的入参快照是 JSON 字符串，展开成缩进形式才好读；不是 JSON 就原样显示 */
const prettyJson = (raw?: string | null): string => {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

const columns: TableColumnsType = [
  { title: '时间', dataIndex: 'createTime', width: 170 },
  { title: '操作者', key: 'operator', width: 160 },
  { title: '模块', key: 'module', width: 90 },
  { title: '动作', key: 'action', width: 130 },
  { title: '业务对象', key: 'biz', width: 180 },
  { title: '结果', key: 'result', width: 140 },
  { title: 'IP', dataIndex: 'ip', width: 130 }
]
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">操作日志</h1>
      <p class="cm-page__sub">
        管理端所有写操作的留痕，只追加不可删改；读操作走访问日志，不在这里。
      </p>
    </header>

    <div class="cm-card logs__card">
      <div class="logs__filter">
        <a-space wrap :size="12">
          <a-select v-model:value="filters.module" style="width: 130px">
            <a-select-option value="">模块：全部</a-select-option>
            <a-select-option v-for="m in LOG_MODULES" :key="m" :value="m">
              {{ moduleLabel(m) }}
            </a-select-option>
          </a-select>
          <a-select v-model:value="filters.result" style="width: 120px">
            <a-select-option value="">结果：全部</a-select-option>
            <a-select-option :value="1">成功</a-select-option>
            <a-select-option :value="0">失败</a-select-option>
          </a-select>
          <a-input
            v-model:value="filters.keyword"
            allow-clear
            placeholder="操作者 / 动作 / 说明"
            style="width: 220px"
            @press-enter="onSearch"
          />
          <a-range-picker
            :value="dateRange"
            value-format="YYYY-MM-DD"
            :placeholder="['开始日期', '结束日期']"
            style="width: 250px"
            @change="onDateChange"
          />
          <a-button type="primary" shape="round" @click="onSearch">查询</a-button>
          <a-button shape="round" @click="reset">重置</a-button>
        </a-space>
        <span class="logs__count">共 {{ total }} 条</span>
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
          showSizeChanger: false,
          showTotal: (t: number) => `共 ${t} 条`,
          onChange: onPageChange
        }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'operator'">
            <div class="logs__operator">
              <span>{{ record.adminName ?? '—' }}</span>
              <a-tag :color="roleColor(record.roleCode)">{{ roleLabel(record.roleCode) }}</a-tag>
            </div>
          </template>

          <template v-else-if="column.key === 'module'">
            <a-tag :color="MODULE_COLORS[record.module] ?? 'default'">
              {{ moduleLabel(record.module) }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'action'">
            {{ opTypeLabel(record.opType) }}
          </template>

          <template v-else-if="column.key === 'biz'">
            <template v-if="record.bizId">
              <span class="logs__muted">#</span>{{ record.bizId }}
            </template>
            <span v-else class="logs__muted">—</span>
          </template>

          <template v-else-if="column.key === 'result'">
            <a-tag v-if="record.result === 1" color="green">成功</a-tag>
            <a-tag v-else color="red">失败</a-tag>
            <span v-if="record.result !== 1 && record.errorCode" class="logs__error">
              {{ record.errorCode }}
            </span>
          </template>
        </template>

        <!-- 展开行：整条记录的下钻，列表列宽有限，细信息都放这里 -->
        <template #expandedRowRender="{ record }">
          <div class="logs__detail">
            <a-descriptions :column="3" size="small" bordered>
              <a-descriptions-item label="动作说明">
                {{ record.description ?? '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="耗时">{{ record.costMs ?? '—' }} ms</a-descriptions-item>
              <a-descriptions-item label="traceId">
                {{ record.traceId ?? '—' }}
              </a-descriptions-item>
              <a-descriptions-item label="User-Agent" :span="3">
                {{ record.ua ?? '—' }}
              </a-descriptions-item>
            </a-descriptions>

            <template v-if="record.afterValue">
              <h4 class="logs__detail-sub">变更内容（已脱敏）</h4>
              <pre class="logs__json">{{ prettyJson(record.afterValue) }}</pre>
            </template>
            <p v-else class="logs__muted logs__no-diff">
              该接口未开启变更留痕（`saveDiff = false`），只记录动作本身。
            </p>
          </div>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped>
.logs__card {
  padding: var(--cm-space-5);
}

.logs__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  margin-bottom: var(--cm-space-4);
}

.logs__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.logs__operator {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.logs__muted {
  color: var(--cm-color-text-3);
}

/* 失败错误码紧跟结果标签，是「同一错误码反复出现」这条线索的入口 */
.logs__error {
  margin-left: var(--cm-space-2);
  color: var(--cm-color-red-600, #d92c2c);
  font-size: var(--cm-font-size-xs);
}

.logs__detail-sub {
  margin: var(--cm-space-4) 0 var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
}

.logs__json {
  margin: 0;
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  font-size: var(--cm-font-size-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 260px;
  overflow: auto;
}

.logs__no-diff {
  margin: var(--cm-space-4) 0 0;
  font-size: var(--cm-font-size-sm);
}
</style>
