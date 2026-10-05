<script setup lang="ts">
/**
 * A7 内容管理 · 五张字典（business/03 A-B6 / F-A7，接口清单见 notes/07 §4）
 *
 * <p>数据全部来自 `/admin/dict/**`，**读接口也挂 `content.edit`**：
 * 本页对运营 / 审核员 / 只读角色是整页不可达（菜单不显示，直连接口返回 1002）。
 *
 * <p>五个 Tab 共用「表格 + 启停 + 编辑」的骨架，但**每种字典的字段不同**
 * （分类有父子两级、成色有颜色、面交点有开放时段），
 * 所以字段表单用 v-if 分别写，不做强行抽象——5 张表各写一份比一份泛型配置更好读。
 *
 * <p>写操作一律「提交 → 重拉五张表」：字典一共几十行，本地一份、服务端一份的两套状态
 * 迟早会不一致（如停用一级分类时后端连带停了子级），重拉的代价可以忽略。
 *
 * <p>为什么是「停用」而不是「删除」：字典被商品 / 用户引用着，真删会留下孤儿数据。
 * 后端因此不提供删除接口，本页也没有删除按钮。
 */

import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { DICT_LABELS, DICT_TYPES, PERM_CODES } from '@campus/shared'
import type { DictType } from '@campus/shared'
import {
  dictApi,
  type DictCampusVO,
  type DictCategoryVO,
  type DictCollegeVO,
  type DictGradeVO,
  type DictMeetPointVO,
  type GradeCode
} from '@campus/api'

type TabKey = DictType

const TABS = DICT_TYPES.map((key) => ({ key, label: DICT_LABELS[key] }))

/** 各 Tab 那个「必填的文本字段」叫什么（同一个输入框在五个 Tab 里语义不同） */
const NAME_LABEL: Record<TabKey, string> = {
  category: '分类名称',
  grade: '成色文案',
  meetPoint: '面交点名称',
  college: '学院名称',
  campus: '校区名称'
}

const active = ref<TabKey>('category')

/* ── 数据源 ── */

const categories = ref<DictCategoryVO[]>([])
const grades = ref<DictGradeVO[]>([])
const meetPoints = ref<DictMeetPointVO[]>([])
const colleges = ref<DictCollegeVO[]>([])
const campuses = ref<DictCampusVO[]>([])

const loading = ref(false)

/**
 * 五张表一次拉齐：每张最多几十行，分 Tab 懒加载省不下什么，
 * 却要多维护一套「切到该 Tab 才加载」的状态。
 */
const load = async (): Promise<void> => {
  loading.value = true
  try {
    const [ca, gr, mp, co, cp] = await Promise.all([
      dictApi.categories(),
      dictApi.grades(),
      dictApi.meetPoints(),
      dictApi.colleges(),
      dictApi.campuses()
    ])
    categories.value = ca ?? []
    grades.value = gr ?? []
    meetPoints.value = mp ?? []
    colleges.value = co ?? []
    campuses.value = cp ?? []
  } catch {
    // 请求层已提示（含 traceId）。这里保留上一次的数据不清空：
    // 拉失败时看到旧数据，比看到一张空表更容易判断是"没拉到"而不是"真没有"
  } finally {
    loading.value = false
  }
}

/**
 * 分类是树：把二级挂到一级的 children 上，a-table 会自动渲染成可展开的树。
 *
 * <p>判「一级」用 `!c.parentId` 而不是 `=== null`：后端 Jackson 配了 `non_null`，
 * 一级分类的 `parentId` 在 JSON 里**整个字段都不出现**，前端拿到的是 `undefined`。
 * 写成 `=== null` 会一级都筛不出来 → 整张表空白（2026-10-02 联调踩过）。
 */
const categoryTree = computed(() => {
  const roots = categories.value
    .filter((c) => !c.parentId)
    .map((c) => ({ ...c, children: [] as (DictCategoryVO & { children?: unknown[] })[] }))
  for (const child of categories.value) {
    if (!child.parentId) continue
    roots.find((r) => r.id === child.parentId)?.children.push({ ...child })
  }
  return roots
})

/** 面交点与学院只能挂到启用中的校区（停用校区下不该再新增） */
const enabledCampusOptions = computed(() => campuses.value.filter((c) => c.enabled))

/* ── 列定义：五种字段不同，各写一份 ── */

const categoryColumns: TableColumnsType = [
  { title: '分类名称', dataIndex: 'name', width: 240 },
  { title: '排序', dataIndex: 'sort', width: 80 },
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
  { title: '开放时段', key: 'openHours', width: 160 },
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

/**
 * 级联提示：停用不是只改自己那一条，后端的连带范围要让操作者当场知道。
 * 启用不连带（连带恢复会把不想启用的也一起打开），所以只有 false 这一侧有文案。
 */
const CASCADE_HINT: Partial<Record<TabKey, string>> = {
  category: '，其下二级分类已一并停用',
  campus: '，该校区下的面交点与学院已一并停用'
}

/** 五张表的启停接口形状一致，只有成色按 code 定位——这里抹平差异，模板里不必再分情况 */
const STATUS_API: Record<TabKey, (key: string, enabled: boolean) => Promise<void>> = {
  category: dictApi.updateCategoryStatus,
  grade: (code, enabled) => dictApi.updateGradeStatus(code as GradeCode, enabled),
  meetPoint: dictApi.updateMeetPointStatus,
  college: dictApi.updateCollegeStatus,
  campus: dictApi.updateCampusStatus
}

/** 正在请求中的开关：key 为 `字典类型:主键`，避免同名的 id 互相串台 */
const toggling = ref<Record<string, boolean>>({})

const toggleStatus = async (
  kind: TabKey,
  key: string,
  next: boolean,
  label: string
): Promise<void> => {
  const ticket = `${kind}:${key}`
  toggling.value[ticket] = true
  try {
    await STATUS_API[kind](key, next)
    message.success(`${label} 已${next ? '启用' : '停用'}${next ? '' : (CASCADE_HINT[kind] ?? '')}`)
    await load()
  } catch {
    // 失败不改本地数据：开关是单向绑定（:checked），重拉或不动都会回到服务端的值
  } finally {
    delete toggling.value[ticket]
  }
}

/* ── 新增 / 编辑 ── */

const editing = ref(false)
const saving = ref(false)

const form = reactive<{
  id: string | null
  name: string
  code: GradeCode
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
  color: '',
  sort: 99,
  parentId: null,
  campusId: '',
  openHours: ''
})

/** 成色是固定四档，只有「编辑某一档」；其余四张表按路径 id 分新增/编辑 */
const modalTitle = computed(() => {
  if (active.value === 'grade') return '编辑'
  return form.id ? '编辑' : '新增'
})

/**
 * 「上级分类」候选项：树只有两级，所以只能选一级；同时排除自己，
 * 否则把自己选成自己的父级会生成 `parent_id = id`，前端按 parentId 组树时会死循环。
 *
 * <p>同样用 `!c.parentId` 判一级（null 字段在 JSON 里缺省，见 categoryTree 注释）。
 */
const parentOptions = computed(() =>
  categories.value.filter((c) => !c.parentId && c.id !== form.id)
)

const resetForm = (): void => {
  form.id = null
  form.name = ''
  form.code = 'A'
  form.label = ''
  form.desc = ''
  form.color = ''
  form.sort = 99
  form.parentId = null
  form.campusId = enabledCampusOptions.value[0]?.id ?? ''
  form.openHours = ''
}

const openCreate = (parentId: string | null = null): void => {
  resetForm()
  form.parentId = parentId
  editing.value = true
}

const openEdit = (row: Record<string, unknown>): void => {
  resetForm()
  // 成色没有雪花 id（code 即主键），所以不能拿 id 是否存在判断"这次是编辑"
  form.id = row.id == null ? null : String(row.id)
  form.name = String(row.name ?? '')
  form.code = (row.code as GradeCode) ?? 'A'
  form.label = String(row.label ?? '')
  form.desc = String(row.desc ?? '')
  form.color = String(row.color ?? '')
  form.sort = Number(row.sort ?? 99)
  form.parentId = row.parentId == null ? null : String(row.parentId)
  form.campusId = row.campusId == null ? '' : String(row.campusId)
  form.openHours = String(row.openHours ?? '')
  editing.value = true
}

/* ── 保存 ── */

/**
 * 校验后按 Tab 分派到对应接口。
 *
 * <p>空串一律转 null 再发：后端的 `@Pattern` 只跳过 null，不跳过空串——
 * 色值传 `""` 会被判成格式错误，传 null 才是"没填，走默认值"。
 */
const save = async (): Promise<void> => {
  const id = form.id
  const name = form.name.trim()

  switch (active.value) {
    case 'category': {
      const req = { name, parentId: form.parentId, sort: form.sort }
      if (id) await dictApi.updateCategory(id, req)
      else await dictApi.createCategory(req)
      return
    }
    case 'grade':
      // 按 code upsert，新增与编辑是同一个接口
      await dictApi.saveGrade(form.code, {
        name: form.label.trim(),
        description: form.desc.trim() || null,
        color: form.color.trim() || null
      })
      return
    case 'meetPoint': {
      const req = { name, campusId: form.campusId, openHours: form.openHours.trim() || null }
      if (id) await dictApi.updateMeetPoint(id, req)
      else await dictApi.createMeetPoint(req)
      return
    }
    case 'college': {
      const req = { name, campusId: form.campusId || null }
      if (id) await dictApi.updateCollege(id, req)
      else await dictApi.createCollege(req)
      return
    }
    default: {
      const req = { name }
      if (id) await dictApi.updateCampus(id, req)
      else await dictApi.createCampus(req)
    }
  }
}

const submit = async (): Promise<void> => {
  const typed = active.value === 'grade' ? form.label : form.name
  if (!typed.trim()) {
    message.warning(`请填写${NAME_LABEL[active.value]}`)
    return
  }
  if (active.value === 'meetPoint' && !form.campusId) {
    message.warning('请选择所属校区')
    return
  }
  // 一级分类下已有二级时不能再挂到别人下面，否则会拼出三级——树只允许两层
  if (
    active.value === 'category' &&
    form.id &&
    form.parentId &&
    categories.value.some((c) => c.parentId === form.id)
  ) {
    message.warning('该分类下已有二级分类，不能改为二级')
    return
  }

  const what = modalTitle.value
  saving.value = true
  try {
    await save()
    editing.value = false
    message.success(`${what}成功`)
    await load()
  } catch {
    // 请求层已提示；弹窗保持打开，改完可以直接重试
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
      <a-space>
        <a-button :loading="loading" shape="round" @click="load">刷新</a-button>
        <!-- 成色是固定 A–D 四档，没有"新增一档"这回事 -->
        <a-button
          v-if="active !== 'grade'"
          v-perm="PERM_CODES.CONTENT_EDIT"
          type="primary"
          shape="round"
          @click="openCreate(null)"
        >
          新增
        </a-button>
      </a-space>
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
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #emptyText>
          <a-empty description="暂无分类" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              :loading="toggling[`category:${record.id}`]"
              size="small"
              @change="toggleStatus('category', record.id, !record.enabled, record.name)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button
                v-perm="PERM_CODES.CONTENT_EDIT"
                type="link"
                size="small"
                @click="openEdit(record)"
              >
                编辑
              </a-button>
              <a-button
                v-if="!record.parentId"
                v-perm="PERM_CODES.CONTENT_EDIT"
                type="link"
                size="small"
                @click="openCreate(record.id)"
              >
                新增子分类
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <!-- 成色等级 -->
      <a-table
        v-else-if="active === 'grade'"
        :columns="gradeColumns"
        :data-source="grades"
        :loading="loading"
        row-key="code"
        size="middle"
        :pagination="false"
      >
        <template #emptyText>
          <a-empty description="暂无成色档位" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'color'">
            <span class="content__swatch" :style="{ background: record.color ?? '#8E8E93' }" />
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              :loading="toggling[`grade:${record.code}`]"
              size="small"
              @change="toggleStatus('grade', record.code, !record.enabled, record.label)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-perm="PERM_CODES.CONTENT_EDIT"
              type="link"
              size="small"
              @click="openEdit(record)"
            >
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
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #emptyText>
          <a-empty description="暂无面交点" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'campus'">
            {{ record.campusName ?? '未知校区' }}
          </template>
          <template v-else-if="column.key === 'openHours'">
            {{ record.openHours ?? '未填写' }}
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              :loading="toggling[`meetPoint:${record.id}`]"
              size="small"
              @change="toggleStatus('meetPoint', record.id, !record.enabled, record.name)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-perm="PERM_CODES.CONTENT_EDIT"
              type="link"
              size="small"
              @click="openEdit(record)"
            >
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
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #emptyText>
          <a-empty description="暂无学院" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'campus'">
            {{ record.campusName ?? '未指定校区' }}
          </template>
          <template v-else-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              :loading="toggling[`college:${record.id}`]"
              size="small"
              @change="toggleStatus('college', record.id, !record.enabled, record.name)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-perm="PERM_CODES.CONTENT_EDIT"
              type="link"
              size="small"
              @click="openEdit(record)"
            >
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
        :loading="loading"
        row-key="id"
        size="middle"
        :pagination="false"
      >
        <template #emptyText>
          <a-empty description="暂无校区" />
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'enabled'">
            <a-switch
              v-perm="PERM_CODES.CONTENT_EDIT"
              :checked="record.enabled"
              :loading="toggling[`campus:${record.id}`]"
              size="small"
              @change="toggleStatus('campus', record.id, !record.enabled, record.name)"
            />
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button
              v-perm="PERM_CODES.CONTENT_EDIT"
              type="link"
              size="small"
              @click="openEdit(record)"
            >
              编辑
            </a-button>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal
      v-model:open="editing"
      :title="`${modalTitle}${TABS.find((t) => t.key === active)?.label}`"
      :confirm-loading="saving"
      ok-text="保存"
      cancel-text="取消"
      @ok="submit"
    >
      <a-form layout="vertical" class="content__form">
        <template v-if="active === 'category'">
          <a-form-item label="上级分类">
            <a-select v-model:value="form.parentId" allow-clear placeholder="留空为一级分类">
              <a-select-option :value="null">（一级分类）</a-select-option>
              <a-select-option v-for="c in parentOptions" :key="c.id" :value="c.id">
                {{ c.name }}
              </a-select-option>
            </a-select>
            <p class="content__hint">分类只分两级：留空是一级，选中一项即在其下新增二级分类。</p>
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
            <a-select v-model:value="form.code" disabled>
              <a-select-option v-for="g in ['A', 'B', 'C', 'D']" :key="g" :value="g">{{ g }}</a-select-option>
            </a-select>
            <p class="content__hint">成色固定 A–D 四档，保存即覆盖该档的文案与色值。</p>
          </a-form-item>
          <a-form-item label="文案">
            <a-input v-model:value="form.label" placeholder="如：几乎全新" />
          </a-form-item>
          <a-form-item label="说明">
            <a-input v-model:value="form.desc" placeholder="如：使用极少，无明显痕迹" />
          </a-form-item>
          <a-form-item label="颜色">
            <a-input v-model:value="form.color" placeholder="#34C759" />
            <p class="content__hint">需为 #RRGGBB 格式；留空取 #8E8E93。</p>
          </a-form-item>
        </template>

        <template v-else-if="active === 'meetPoint'">
          <a-form-item label="名称">
            <a-input v-model:value="form.name" placeholder="如：中心校区·图书馆东门" />
          </a-form-item>
          <a-form-item label="所属校区">
            <a-select v-model:value="form.campusId" placeholder="请选择校区">
              <a-select-option v-for="c in enabledCampusOptions" :key="c.id" :value="c.id">
                {{ c.name }}
              </a-select-option>
            </a-select>
            <p class="content__hint">只能挂在启用中的校区下。</p>
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
            <a-select v-model:value="form.campusId" allow-clear placeholder="留空为未指定">
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

/* 表单项下的补充说明：用最小字号 + 三级文字色，不抢表单控件的视觉重量 */
.content__hint {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.content__form {
  margin-top: var(--cm-space-5);
}
</style>
