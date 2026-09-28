<script setup lang="ts">
/**
 * 权限矩阵（骨架版）—— 对应 design/03 A9 的第一张表
 *
 * <p>为什么先做这一页：它是唯一能**端到端验证权限隔离**的页面——
 * 左侧表格走真实接口 `/admin/auth/perm-codes`（后端与 PermCodes 常量同源），
 * 右侧两个按钮走 `/admin/demo/*`，用审核员登录时「调用 user.ban」按钮会
 * 因为 v-perm 指令直接不渲染，即便绕过前端也会被后端 Sa-Token 拦下返回 1002。
 * 这一页跑通，后面的 Table 页都是同一套模式的复制。
 */

import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { demoApi, authApi } from '@campus/api'
import { PERM_CODES, PERM_GROUPS, PERM_LABELS, type PermCode } from '@campus/shared'
import { useUserStore } from '@/stores/user'

const user = useUserStore()

/** 后端下发的全量权限点；为空表示还没拉到，表格走 loading */
const allCodes = ref<PermCode[]>([])
const loading = ref(false)

const columns = [
  { title: '权限点', dataIndex: 'code', key: 'code', width: 180 },
  { title: '说明', dataIndex: 'label', key: 'label' },
  { title: '所属分组', dataIndex: 'group', key: 'group', width: 120 },
  { title: '当前角色', dataIndex: 'owned', key: 'owned', width: 100 }
]

/** 权限点 → 分组，反查一次避免表格里循环嵌套 */
const groupOf = computed(() => {
  const map = new Map<PermCode, string>()
  for (const g of PERM_GROUPS) for (const c of g.codes) map.set(c, g.group)
  return map
})

const rows = computed(() =>
  allCodes.value.map((code) => ({
    key: code,
    code,
    label: PERM_LABELS[code] ?? '—',
    group: groupOf.value.get(code) ?? '—',
    owned: user.hasPerm(code)
  }))
)

const load = async (): Promise<void> => {
  loading.value = true
  try {
    const codes = await authApi.permCodes()
    allCodes.value = (codes ?? []) as PermCode[]
  } finally {
    loading.value = false
  }
}

/** 演示：调后端受保护接口，验证「前端隐藏 + 后端拦截」两层都生效 */
const callDemo = async (kind: 'review' | 'ban'): Promise<void> => {
  try {
    const res = kind === 'review' ? await demoApi.review() : await demoApi.ban()
    message.success(`后端放行：${res.passed}`)
  } catch {
    // 无权限时请求层已弹 warning（code 1002），这里不重复提示
  }
}

const refreshPerms = async (): Promise<void> => {
  await user.refreshPerms()
  message.success('权限点已刷新')
}

onMounted(load)
</script>

<template>
  <a-space direction="vertical" size="16" style="width: 100%">
    <div class="cm-card">
      <div class="cm-card__title">当前会话</div>
      <div class="cm-card__body">
        <a-descriptions :column="4" size="small">
          <a-descriptions-item label="登录人">{{ user.realName || '—' }}</a-descriptions-item>
          <a-descriptions-item label="角色">{{ user.roleCode || '—' }}</a-descriptions-item>
          <a-descriptions-item label="管理员 ID">{{ user.adminId || '—' }}</a-descriptions-item>
          <a-descriptions-item label="持有权限点">{{ user.permCodes.length }} 个</a-descriptions-item>
        </a-descriptions>
        <a-button size="small" style="margin-top: 12px" @click="refreshPerms">刷新权限点</a-button>
      </div>
    </div>

    <div class="cm-card">
      <div class="cm-card__title">权限点清单（后端 /admin/auth/perm-codes）</div>
      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="{ pageSize: 20, size: 'small' }"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'code'">
            <a-typography-text code>{{ record.code }}</a-typography-text>
          </template>
          <template v-else-if="column.key === 'owned'">
            <a-tag v-if="record.owned" color="green">持有</a-tag>
            <a-tag v-else>无</a-tag>
          </template>
        </template>
      </a-table>
    </div>

    <div class="cm-card">
      <div class="cm-card__title">权限隔离验证（按钮级 v-perm + 后端 Sa-Token）</div>
      <div class="cm-card__body">
        <!-- 无权限的按钮会被指令直接移除，不占位置 -->
        <a-space>
          <a-button v-perm="PERM_CODES.REVIEW_JUDGE" type="primary" @click="callDemo('review')">
            调用 review.judge（审核判定）
          </a-button>
          <a-button v-perm="PERM_CODES.USER_BAN" danger @click="callDemo('ban')">
            调用 user.ban（封禁用户）
          </a-button>
        </a-space>
        <p class="hint">
          用 <b>reviewer</b> 登录：只看到第一个按钮；用 <b>super</b> 登录：两个都在。
          两侧都会真实请求后端，后端拦截时返回业务码 1002。
        </p>
      </div>
    </div>
  </a-space>
</template>

<style scoped>
.hint {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}
</style>
