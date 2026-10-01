<script setup lang="ts">
/**
 * 操作日志（business/03 F-A11）——2026-10-01 从 A9 系统设置里拆出独立页
 *
 * <p>为什么要拆：原先它是系统设置的一个 Tab，导致两个问题——
 *   ① 高频查询场景（巡盘 / 追责 / 答辩演示审计留痕）藏在低频配置项里不好找；
 *   ② 页面门禁只能用 log.view，于是「运营」（有 log.view、无 settings.edit）
 *      能进系统设置页，里面却放着 SLA 阈值与敏感词的编辑表单。
 *      拆开后：本页 log.view，系统设置页 settings.edit，权限才对得上矩阵。
 *
 * <p>业务口径（F-A11）：记录**有业务语义的写操作**（审核 / 举报 / 申诉 / 封禁 / 裁决 / 配置），
 * 读操作不在这里——那是访问日志的活（Filter 落日志文件），两者分工不可互相替代。
 * 本表只追加、不可删改，接口也不提供删除。
 *
 * <p>数据现在是 `mock/settings.ts` 的 OPERATION_LOGS；
 * 换成真实接口时只改这里的数据获取，表格与筛选不动。
 */

import { computed, reactive } from 'vue'
import type { TableColumnsType } from 'ant-design-vue'
import { LOG_MODULES, OPERATION_LOGS } from '@/mock/settings'

const filters = reactive<{ module: string; keyword: string }>({ module: 'ALL', keyword: '' })

const logs = computed(() => {
  const kw = filters.keyword.trim().toLowerCase()
  return OPERATION_LOGS.filter((l) => {
    if (filters.module !== 'ALL' && l.module !== filters.module) return false
    if (!kw) return true
    return (
      l.operator.toLowerCase().includes(kw) ||
      l.action.toLowerCase().includes(kw) ||
      l.target.toLowerCase().includes(kw)
    )
  })
})

const columns: TableColumnsType = [
  { title: '时间', key: 'at', width: 140 },
  { title: '操作者', dataIndex: 'operator', width: 120 },
  { title: '模块', key: 'module', width: 90 },
  { title: '动作', dataIndex: 'action', width: 110 },
  { title: '对象', dataIndex: 'target', width: 260 },
  { title: '依据', key: 'basis' },
  { title: 'IP', dataIndex: 'ip', width: 120 }
]

/** 模块色只用于快速扫读，模块名文字本身也在标签里（不靠颜色单独表意） */
const MODULE_COLORS: Record<string, string> = {
  审核: 'blue',
  申诉: 'purple',
  举报: 'orange',
  用户: 'cyan',
  商品: 'geekblue',
  订单: 'magenta',
  内容: 'green',
  系统: 'red'
}

const moduleColor = (m: string): string => MODULE_COLORS[m] ?? 'default'
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">操作日志</h1>
      <p class="cm-page__sub">管理端所有写操作的留痕，只追加不可删改</p>
    </header>

    <div class="cm-card logs__card">
      <div class="logs__filter">
        <a-space wrap :size="12">
          <a-select v-model:value="filters.module" style="width: 130px">
            <a-select-option value="ALL">模块：全部</a-select-option>
            <a-select-option v-for="m in LOG_MODULES" :key="m" :value="m">{{ m }}</a-select-option>
          </a-select>
          <a-input
            v-model:value="filters.keyword"
            allow-clear
            placeholder="操作者 / 动作 / 对象"
            style="width: 240px"
          />
        </a-space>
        <span class="logs__count">共 {{ logs.length }} 条</span>
      </div>

      <a-table
        :columns="columns"
        :data-source="logs"
        row-key="id"
        size="middle"
        :pagination="{ pageSize: 15, showTotal: (t: number) => `共 ${t} 条` }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'at'">
            {{ record.at.slice(0, 16).replace('T', ' ') }}
          </template>
          <template v-else-if="column.key === 'module'">
            <a-tag :color="moduleColor(record.module)">{{ record.module }}</a-tag>
          </template>
          <template v-else-if="column.key === 'basis'">
            <span v-if="record.basis">{{ record.basis }}</span>
            <span v-else class="logs__muted">—</span>
          </template>
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

.logs__muted {
  color: var(--cm-color-text-3);
}
</style>
