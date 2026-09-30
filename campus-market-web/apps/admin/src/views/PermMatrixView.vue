<script setup lang="ts">
/**
 * 权限矩阵（骨架版）—— 对应 design/03 A9 的第一张表
 *
 * <p>展示形态：**分组卡片墙**而不是 16 行平铺表格。行=权限组（与侧边菜单分组一致），
 * 组内每个权限点是一枚 chip：持有 = 品牌蓝实底，未持有 = 灰底带「—」。
 * 一眼能看出「这个角色能干什么、不能干什么」，比逐行扫表格直观得多。
 *
 * <p>数据两路：全量权限点走真实接口 `/admin/auth/perm-codes`（与后端 PermCodes 常量同源），
 * 「当前角色是否持有」来自登录时下发的 permCodes（`/admin/auth/info`）。
 * 前端只是展示，真正的隔离由后端 Sa-Token 注解兜底。
 *
 * <p>底部保留**端到端验证**按钮卡：用审核员登录时「调用 user.ban」按钮会
 * 因为 v-perm 指令直接不渲染，即便绕过前端也会被后端拦下返回 1002。
 * 这一页跑通，后面的 Table 页都是同一套模式的复制。
 */
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { demoApi, authApi } from '@campus/api'
import { PERM_CODES, PERM_GROUPS, PERM_LABELS, ROLE_META, type PermCode, type RoleCode } from '@campus/shared'
import { useUserStore } from '@/stores/user'

const user = useUserStore()

/** 后端下发的全量权限点；为空表示还没拉到，走 loading */
const allCodes = ref<PermCode[]>([])
const loading = ref(false)

const groups = computed(() =>
  PERM_GROUPS.map((g) => ({
    name: g.group,
    items: g.codes
      .filter((c) => allCodes.value.length === 0 || allCodes.value.includes(c))
      .map((code) => ({
        code,
        label: PERM_LABELS[code] ?? '—',
        owned: user.hasPerm(code)
      }))
  }))
)

const roleMeta = computed(
  () => ROLE_META[user.roleCode as RoleCode] ?? { label: user.roleCode || '未知角色', color: 'default' }
)

const ownedCount = computed(() => user.permCodes.length)

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
  <div class="cm-page">
    <header class="cm-page__head matrix__head">
      <div>
        <h1 class="cm-page__title">权限矩阵</h1>
        <p class="cm-page__sub">当前角色持有的权限点，与侧边菜单分组一一对应</p>
      </div>
      <a-button :loading="loading" shape="round" @click="load">刷新清单</a-button>
    </header>

    <!-- 会话摘要：一行 chips 代替原来的 descriptions 大卡 -->
    <div class="matrix__session">
      <span class="matrix__who">
        <span class="matrix__avatar">{{ (user.realName || '管').slice(0, 1) }}</span>
        {{ user.realName || user.adminId }}
      </span>
      <span class="matrix__role" :data-role="roleMeta.label">{{ roleMeta.label }}</span>
      <span class="matrix__count">持有 <b>{{ ownedCount }}</b> / 16 个权限点</span>
      <a-button type="link" size="small" @click="refreshPerms">刷新权限点</a-button>
    </div>

    <!-- 分组卡片墙：2 列，组名 + 权限 chips -->
    <a-spin :spinning="loading">
      <div class="matrix__grid">
        <section v-for="g in groups" :key="g.name" class="cm-card matrix__card">
          <h2 class="matrix__group">{{ g.name }}</h2>
          <div class="matrix__chips">
            <span
              v-for="item in g.items"
              :key="item.code"
              class="chip"
              :class="{ 'is-owned': item.owned }"
              :title="item.owned ? '当前角色持有' : '当前角色未持有'"
            >
              <code class="chip__code">{{ item.code }}</code>
              <span class="chip__label">{{ item.label }}</span>
              <span v-if="!item.owned" class="chip__mark">—</span>
            </span>
          </div>
        </section>
      </div>
    </a-spin>

    <div class="cm-card matrix__demo">
      <h2 class="matrix__group">端到端验证（按钮级 v-perm + 后端 Sa-Token）</h2>
      <a-space>
        <a-button v-perm="PERM_CODES.REVIEW_JUDGE" type="primary" shape="round" @click="callDemo('review')">
          调用 review.judge（审核判定）
        </a-button>
        <a-button v-perm="PERM_CODES.USER_BAN" danger shape="round" @click="callDemo('ban')">
          调用 user.ban（封禁用户）
        </a-button>
      </a-space>
      <p class="hint">
        用 <b>reviewer</b> 登录：只看到第一个按钮；用 <b>super</b> 登录：两个都在。
        两侧都会真实请求后端，后端拦截时返回业务码 1002。
      </p>
    </div>
  </div>
</template>

<style scoped>
.matrix__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

/* 会话摘要：白卡上一行排开，不再单独占一张大卡 */
.matrix__session {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--cm-space-4);
  padding: var(--cm-space-3) var(--cm-space-5);
  margin-bottom: var(--cm-space-5);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-2);
}

.matrix__who {
  display: inline-flex;
  align-items: center;
  gap: var(--cm-space-2);
  color: var(--cm-color-text-1);
  font-weight: 500;
}

.matrix__avatar {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--cm-color-brand-bg);
  color: var(--cm-color-brand);
  font-size: var(--cm-font-size-xs);
  font-weight: 600;
}

.matrix__role {
  padding: 2px 10px;
  border-radius: var(--cm-radius-pill);
  background: var(--cm-color-brand-bg);
  color: var(--cm-color-brand);
  font-size: var(--cm-font-size-xs);
  font-weight: 500;
}

.matrix__count b {
  color: var(--cm-color-brand);
  font-variant-numeric: tabular-nums;
}

.matrix__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--cm-space-4);
}

.matrix__card {
  padding: var(--cm-space-5);
}

.matrix__group {
  margin: 0 0 var(--cm-space-4);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--cm-color-text-1);
}

.matrix__chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
}

/*
 * 权限 chip：持有 = 品牌蓝浅底；未持有 = 灰底 + 尾部「—」。
 * 状态不只靠颜色区分（无障碍）：未持有的 chip 一定带「—」记号。
 */
.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--cm-space-2);
  padding: 6px 12px;
  border-radius: var(--cm-radius-pill);
  background: var(--cm-color-gray-100);
  color: var(--cm-color-text-3);
  border: 1px solid transparent;
}

.chip.is-owned {
  background: var(--cm-color-brand-bg);
  border-color: var(--cm-color-brand-border);
  color: var(--cm-color-text-1);
}

/* tokens 里没有 mono 字体族，这里显式写死（SF Mono → 系统等宽兜底） */
.chip__code {
  font-family: 'SF Mono', ui-monospace, 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: var(--cm-font-size-xs);
}

.chip.is-owned .chip__code {
  color: var(--cm-color-brand);
  font-weight: 500;
}

.chip__label {
  font-size: var(--cm-font-size-xs);
}

.chip__mark {
  color: var(--cm-color-text-3);
}

.matrix__demo {
  margin-top: var(--cm-space-4);
  padding: var(--cm-space-5);
}

.hint {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}
</style>
