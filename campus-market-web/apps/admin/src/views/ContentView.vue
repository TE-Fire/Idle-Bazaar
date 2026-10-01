<script setup lang="ts">
/**
 * A7 内容管理 · 五张字典（business/03 A-B6 / F-A7）
 *
 * <p>F-A7 判据：这五张字典是**全端一致性的源头**，必须由后台下发，三端不得本地硬编码。
 * 所以本页虽然是 mock 数据，交互按「后台返回 + 写接口」的形状做——
 * 后端 `/admin/dict/*` 就绪后只换数据源，页面不用改。
 *
 * <p>五个 Tab 共用一套「表格 + 启停 + 编辑」的骨架，但**每种字典的字段不同**
 * （分类有父子两级、成色有颜色、面交点有开放时段），
 * 所以字段表单用 v-if 分别写，不做强行抽象——5 张表各写一份比一份泛型配置更好读。
 *
 * <p>为什么「停用」而不是「删除」：字典被商品/用户引用着（如某分类下有 186 件商品），
 * 真删会留下孤儿数据。停用既保留了历史数据的可解释性，也留下了恢复的余地。
 */

import { computed, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES } from '@campus/shared'
import {
  CAMPUSES,
  CATEGORIES,
  COLLEGES,
  GRADES,
  MEET_POINTS,
  campusName
} from '@/mock/dict'
import type {
  DictCampusItem,
  DictCategoryItem,
  DictCollegeItem,
  DictGradeItem,
  DictMeetPointItem,
  DictType
} from '@campus/shared'

type TabKey = DictType

const TABS: { key: TabKey; label: string }[] = [
  { key: 'category', label: '分类' },
  { key: 'grade', label: '成色等级' },
  { key: 'meetPoint', label: '面交点' },
  { key: 'college', label: '学院' },
  { key: 'campus', label: '校区' }
]

const active = ref<TabKey>('category')

/* ── 五张表的数据源（可写：启停/编辑直接改这里，模拟写接口） ── */

const categories = ref<DictCategoryItem[]>(CATEGORIES.map((c) => ({ ...c })))
const grades = ref<DictGradeItem[]>(GRADES.map((g) => ({ ...g })))
const meetPoints = ref<DictMeetPointItem[]>(MEET_POINTS.map((m) => ({ ...m })))
const colleges = ref<DictCollegeItem[]>(COLLEGES.map((c) => ({ ...c })))
const campuses = ref<DictCampusItem[]>(CAMPUSES.map((c) => ({ ...c })))

/** 分类是树：把二级挂到一级的 children 上，a-table 会自动渲染成可展开的树 */
const categoryTree = computed(() => {
  const roots = categories.value
    .filter((c) => c.parentId === null)
    .map((c) => ({ ...c, children: [] as (DictCategoryItem & { children?: unknown[] })[] }))
  for (const child of categories.value.filter((c) => c.parentId !== null)) {
    const parent = roots.find((r) => r.id === child.parentId)
    if (parent) parent.children.push({ ...child })
  }
  return roots
})

const enabledCampusOptions = computed(() => campuses.value.filter((c) => c.enabled))

/* ── 列定义 ── */

const categoryColumns: TableColumnsType = [
  { title: '分类名称', dataIndex: 'name', width: 240 },
  { title: '排序', dataIndex: 'sort', width: 80 },
  { title: '在售商品', dataIndex: 'itemCount', width: 100 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 200 }
]

const gradeColumns: TableColumnsType = [
  { title: '等级', dataIndex: 'code', width: 80 },
  { title: '文案', dataIndex: 'label', width: 160 },
  { title: '说明', dataIndex: 'desc' },
  { title: '颜色', key: 'color', width: 100 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 120 }
]

const meetPointColumns: TableColumnsType = [
  { title: '面交点', dataIndex: 'name', width: 260 },
  { title: '所属校区', key: 'campus', width: 140 },
  { title: '开放时段', dataIndex: 'openHours', width: 160 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 120 }
]

const collegeColumns: TableColumnsType = [
  { title: '学院', dataIndex: 'name', width: 220 },
  { title: '所属校区', key: 'campus', width: 140 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 120 }
]

const campusColumns: TableColumnsType = [
  { title: '校区', dataIndex: 'name', width: 260 },
  { title: '状态', key: 'enabled', width: 100 },
  { title: '操作', key: 'action', width: 120 }
]

/* ── 启停 ── */

const toggle = (row: { name?: string; label?: string; enabled: boolean }, kind: TabKey): void => {
  row.enabled = !row.enabled
  const who = row.name ?? row.label ?? '该项'
  message.success(`${who} 已${row.enabled ? '启用' : '停用'}`)
  // 停用校区时，它下面的面交点会一并失效——这是字典间的引用约束，UI 上要提示
  if (kind === 'campus' && !row.enabled) {
    message.warning('该校区下的面交点仍在使用中，请一并处理')
  }
}

/* ── 新增 / 编辑 ── */

const editing = ref(false)
const form = reactive<{
  id: string | null
  name: string
  code: DictGradeItem['code']
  label: string
  desc: string
  color: string
  sort: number
  parentId: string | null
  campusId: string
  openHours: string
}>({
  id: null,
  name: '',
  code: 'A',
  label: '',
  desc: '',
  color: '#34C759',
  sort: 99,
  parentId: null,
  campusId: 'cp1',
  openHours: '08:00–22:00'
})

const modalTitle = computed(() => (form.id ? '编辑' : '新增'))

const openCreate = (): void => {
  form.id = null
  form.name = ''
  form.label = ''
  form.desc = ''
  form.sort = 99
  form.parentId = null
  form.openHours = '08:00–22:00'
  editing.value = true
}

const openEdit = (row: Record<string, unknown>): void => {
  form.id = String(row.id)
  form.name = String(row.name ?? '')
  form.code = (row.code as DictGradeItem['code']) ?? 'A'
  form.label = String(row.label ?? '')
  form.desc = String(row.desc ?? '')
  form.color = String(row.color ?? '#34C759')
  form.sort = Number(row.sort ?? 99)
  form.parentId = row.parentId ? String(row.parentId) : null
  form.campusId = String(row.campusId ?? 'cp1')
  form.openHours = String(row.openHours ?? '08:00–22:00')
  editing.value = true
}

const submit = (): void => {
  if (!form.name && active.value !== 'grade') {
    message.warning('请填写名称')
    return
  }
  editing.value = false
  message.success(`${modalTitle.value}成功（mock：仅更新本地状态）`)
}

/** 删除只开放给「没有商品挂在上面」的二级分类，避免制造孤儿数据 */
const canDeleteCategory = (row: DictCategoryItem): boolean =>
  row.parentId !== null && row.itemCount === 0

const onDeleteCategory = (row: DictCategoryItem): void => {
  Modal.confirm({
    title: `删除分类「${row.name}」？`,
    content: '删除后不可恢复。该分类下没有在售商品，可以安全删除。',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => {
      categories.value = categories.value.filter((c) => c.id !== row.id)
      message.success('已删除')
    }
  })
}
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head content__head">
      <div>
        <h1 class="cm-page__title">内容管理</h1>
        <p class="cm-page__sub">
          五张字典是全端一致性的源头，由后台下发，三端不得本地硬编码。
        </p>
      </div>
      <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="primary" shape="round" @click="openCreate">
        新增
      </a-button>
    </header>

    <div class="cm-card content__card">
      <a-tabs v-model:active-key="active">
        <a-tab-pane v-for="t in TABS" :key="t.key" :tab="t.label" />
      </a-tabs>

      <!-- 分类：两级树 -->
      <a-table
        v-if="active === 'category'"
        :columns="categoryColumns"
        :data-source="categoryTree"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              size="small"
              @change="toggle(record, 'category')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="link" size="small" @click="openEdit(record)">
                编辑
              </a-button>
              <a-button
                v-if="canDeleteCategory(record)"
                v-perm="PERM_CODES.CONTENT_EDIT"
                type="link"
                size="small"
                danger
                @click="onDeleteCategory(record)"
              >
                删除
              </a-button>
              <span v-else-if="record.parentId" class="content__muted">有商品在售</span>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 成色等级 -->
      <a-table
        v-else-if="active === 'grade'"
        :columns="gradeColumns"
        :data-source="grades"
        row-key="code"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'color'">
            <span class="content__swatch" :style="{ background: record.color }" />
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              size="small"
              @change="toggle(record, 'grade')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="link" size="small" @click="openEdit(record)">
              编辑
            </a-button>
          </template>
        </template>
      </a-table>

      <!-- 面交点 -->
      <a-table
        v-else-if="active === 'meetPoint'"
        :columns="meetPointColumns"
        :data-source="meetPoints"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'campus'">{{ campusName(record.campusId) }}</template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              size="small"
              @change="toggle(record, 'meetPoint')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="link" size="small" @click="openEdit(record)">
              编辑
            </a-button>
          </template>
        </template>
      </a-table>

      <!-- 学院 -->
      <a-table
        v-else-if="active === 'college'"
        :columns="collegeColumns"
        :data-source="colleges"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'campus'">{{ campusName(record.campusId) }}</template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              size="small"
              @change="toggle(record, 'college')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="link" size="small" @click="openEdit(record)">
              编辑
            </a-button>
          </template>
        </template>
      </a-table>

      <!-- 校区 -->
      <a-table
        v-else
        :columns="campusColumns"
        :data-source="campuses"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              size="small"
              @change="toggle(record, 'campus')"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-perm="PERM_CODES.CONTENT_EDIT" type="link" size="small" @click="openEdit(record)">
              编辑
            </a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal v-model:open="editing" :title="`${modalTitle}${TABS.find((t) => t.key === active)?.label}`" ok-text="保存" cancel-text="取消" @ok="submit">
      <a-form layout="vertical" class="content__form">
        <template v-if="active === 'category'">
          <a-form-item label="上级分类">
            <a-select v-model:value="form.parentId" allow-clear placeholder="留空为一级分类">
              <a-select-option :value="null">（一级分类）</a-select-option>
              <a-select-option v-for="c in categoryTree" :key="c.id" :value="c.id">
                {{ c.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="分类名称">
            <a-input v-model:value="form.name" placeholder="如：专业课教材" />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number v-model:value="form.sort" :min="1" :max="999" />
          </a-form-item>
        </template>

        <template v-else-if="active === 'grade'">
          <a-form-item label="等级">
            <a-select v-model:value="form.code" :disabled="!!form.id">
              <a-select-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :value="g">{{ g }}</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="文案">
            <a-input v-model:value="form.label" placeholder="如：几乎全新" />
          </a-form-item>
          <a-form-item label="说明">
            <a-input v-model:value="form.desc" placeholder="如：使用极少，无明显痕迹" />
          </a-form-item>
          <a-form-item label="颜色">
            <a-input v-model:value="form.color" placeholder="#34C759" />
          </a-form-item>
        </template>

        <template v-else-if="active === 'meetPoint'">
          <a-form-item label="名称">
            <a-input v-model:value="form.name" placeholder="如：中心校区·图书馆东门" />
          </a-form-item>
          <a-form-item label="所属校区">
            <a-select v-model:value="form.campusId">
              <a-select-option v-for="c in enabledCampusOptions" :key="c.id" :value="c.id">
                {{ c.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="开放时段">
            <a-input v-model:value="form.openHours" placeholder="如：08:00–22:00" />
          </a-form-item>
        </template>

        <template v-else-if="active === 'college'">
          <a-form-item label="学院名称">
            <a-input v-model:value="form.name" placeholder="如：计算机学院" />
          </a-form-item>
          <a-form-item label="所属校区">
            <a-select v-model:value="form.campusId">
              <a-select-option v-for="c in enabledCampusOptions" :key="c.id" :value="c.id">
                {{ c.name }}
              </a-select-option>
            </a-select>
          </a-form-item>
        </template>

        <template v-else>
          <a-form-item label="校区名称">
            <a-input v-model:value="form.name" placeholder="如：中心校区" />
          </a-form-item>
        </template>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.content__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

/* Tab 与表格之间留白，靠间距分层而不是靠分隔线（Apple 的做法） */
.content__card {
  padding: 0 var(--cm-space-5) var(--cm-space-5);
}

.content__swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: var(--cm-radius-sm);
  vertical-align: middle;
}

.content__muted {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.content__form {
  margin-top: var(--cm-space-5);
}
</style>
