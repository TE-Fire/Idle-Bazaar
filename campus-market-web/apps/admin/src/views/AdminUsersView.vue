<script setup lang="ts">
/**
 * A9 系统设置 · 管理员账号（business/03 F-A0 的下半段）
 *
 * <p>注册只建档、**角色在这里分配**——这条分界是权限模型的安全前提：
 * 注册接口不接受 roleId，超管也不在这里看到「超级管理员」选项
 * （后端 `/admin/admins/roles` 已剔除 SUPER，错误码 4013 是第二道）。
 *
 * <p>这里列出的是 `status=2` 待分配账号：注册完登录会被 4006 拦下，
 * 所以「分配角色」这个动作等价于「放行该账号」，页面上要把这句话说清楚，
 * 否则操作者不知道自己点下去意味着什么。
 *
 * <p>按钮再加一层 `v-perm`：路由守卫挡的是菜单级（无权限进 /403），
 * 指令挡的是按钮级，两层都用上才对得上 design/03 的权限分级口径。
 */

import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES } from '@campus/shared'
import { adminApi, type AdminRole, type AdminUserVO } from '@campus/api'

const list = ref<AdminUserVO[]>([])
const roles = ref<AdminRole[]>([])
const loading = ref(false)
/** 每行一个待选角色：用 map 而不是单值，避免给 A 选完再给 B 选时互相覆盖 */
const picked = reactive<Record<string, string>>({})
const submitting = ref<Record<string, boolean>>({})

const columns: TableColumnsType<AdminUserVO> = [
  { title: '邮箱', dataIndex: 'email', width: 200 },
  { title: '登录账号', dataIndex: 'username', width: 140 },
  { title: '姓名', dataIndex: 'realName', width: 100 },
  { title: '注册时间', dataIndex: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 240 }
]

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const [pending, allRoles] = await Promise.all([adminApi.pending(), adminApi.roles()])
    list.value = pending ?? []
    roles.value = allRoles ?? []
  } finally {
    loading.value = false
  }
}

const onAssign = async (row: AdminUserVO): Promise<void> => {
  const roleId = picked[row.id]
  if (!roleId) {
    message.warning('请先选择要分配的角色')
    return
  }
  submitting.value[row.id] = true
  try {
    await adminApi.assignRole(row.id, roleId)
    message.success(`已为 ${row.email} 分配角色，该账号现在可以登录`)
    await load()
  } finally {
    submitting.value[row.id] = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="admins__head">
      <div>
        <h3 class="admins__title">待分配角色的账号</h3>
        <p class="admins__desc">
          注册产生的账号处于「待分配」状态，登录会被拦下；分配角色后自动启用。
        </p>
      </div>
      <a-button :loading="loading" @click="load">刷新</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="list"
      :loading="loading"
      row-key="id"
      size="middle"
      :pagination="false"
    >
      <template #emptyText>
        <a-empty description="暂无待分配账号" />
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-select
              v-model:value="picked[record.id]"
              placeholder="选择角色"
              style="width: 130px"
            >
              <a-select-option v-for="r in roles" :key="r.id" :value="r.id">
                {{ r.roleName }}
              </a-select-option>
            </a-select>
            <a-button
              v-perm="PERM_CODES.SETTINGS_EDIT"
              type="primary"
              size="small"
              :loading="submitting[record.id]"
              @click="onAssign(record)"
            >
              分配并启用
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
.admins__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
  margin-bottom: var(--cm-space-4);
}

.admins__title {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.admins__desc {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}
</style>
