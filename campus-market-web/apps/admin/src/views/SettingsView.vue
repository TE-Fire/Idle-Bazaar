<script setup lang="ts">
/**
 * A9 系统设置（business/03 A-B9）——只剩两块**需要写权限**的配置项
 *
 * <p>历史：本页原先还挂着「操作日志」Tab，2026-10-01 拆成独立页 `/settings/logs`（F-A11）。
 * 拆的原因有两条：
 *   ① 操作日志是高频查询（巡盘 / 追责），塞在低频配置项里不好找；
 *   ② 页面门禁只能用 log.view，而**运营只有 log.view、没有 settings.edit**，
 *      等于让运营进了放着 SLA 阈值与敏感词表单的页面。拆开后本页门禁改为 settings.edit，
 *      页面内容与「谁能改配置」才对得上权限矩阵。
 *
 * <p>「管理员账号」与「角色权限矩阵」另有两个页面（/settings/admins、/perm-matrix），
 * 这里不重复实现——A9 拆成多个入口比塞进一个大 Tab 更好找。
 *
 * <p>SLA 的两级指标**不可混用**（F-A9 判据）：
 * 响应时效是对用户的承诺（首审 ≤2h、P95 ≤6h、达标率 ≥95%、超 24h 记积压红牌），
 * 操作效率是对审核员的要求（单条 ≤30s）。这里改的阈值会立刻影响 A5 审核队列的临期/超时判定。
 */

import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES } from '@campus/shared'
import { adminSensitiveApi, settingsApi, type SlaThresholds, type SensitiveWordVO } from '@campus/api'

const active = ref<'sla' | 'word'>('sla')

/* ── SLA 配置：落库（`sys_config`），改完看板 / 报表 / 审核队列三处同时生效 ── */

/** 业务文档默认值：重置按钮用它，不是前端自己编的数 */
const SLA_DEFAULTS: SlaThresholds = {
  firstResponseHours: 2,
  p95Hours: 6,
  targetRate: 95,
  backlogHours: 24,
  avgHandleSeconds: 30,
  appealHours: 48
}

const slaForm = reactive<SlaThresholds>({ ...SLA_DEFAULTS })
const slaLoading = ref(false)
const slaSaving = ref(false)

async function loadSla(): Promise<void> {
  slaLoading.value = true
  try {
    Object.assign(slaForm, await settingsApi.sla())
  } catch {
    // 请求层已提示
  } finally {
    slaLoading.value = false
  }
}

void loadSla()

async function saveSla(): Promise<void> {
  if (slaSaving.value) return
  slaSaving.value = true
  try {
    await settingsApi.updateSla({ ...slaForm })
    message.success('SLA 阈值已保存，看板与报表的达标率即时按新阈值判定')
    await loadSla()
  } catch {
    // 请求层已提示（1000 参数越界等）
  } finally {
    slaSaving.value = false
  }
}

const resetSla = (): void => {
  Object.assign(slaForm, SLA_DEFAULTS)
  message.info('已填回业务文档默认值，点「保存」才生效')
}

/* ── 敏感词 ── */

/** 级别展示名：1 高危直接拒发布、2 低危替换成等长 * 后放行 */
const LEVEL_LABELS: Record<number, string> = { 1: '拦截', 2: '疑似' }

const words = ref<SensitiveWordVO[]>([])
const wordTotal = ref(0)
const wordLoading = ref(false)
const newWord = ref('')
const newLevel = ref(2)

const wordColumns: TableColumnsType = [
  { title: '敏感词', dataIndex: 'word', width: 200 },
  { title: '级别', key: 'level', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: '操作', key: 'action', width: 100 }
]

/**
 * 一次拉 100 条：词库量级在几十条，翻页反而碍事。
 * 切到这个 Tab 才拉——SLA 那半边不需要它。
 */
async function loadWords(): Promise<void> {
  wordLoading.value = true
  try {
    const p = await adminSensitiveApi.list({ pageSize: 100 })
    words.value = p.list
    wordTotal.value = p.total
  } catch {
    // 请求层已统一提示，这里只需把 loading 收掉
  } finally {
    wordLoading.value = false
  }
}

watch(active, (key) => {
  if (key === 'word') {
    void loadWords()
  }
})

const addWord = async (): Promise<void> => {
  const w = newWord.value.trim()
  if (!w) {
    message.warning('请输入敏感词')
    return
  }
  try {
    await adminSensitiveApi.create({ word: w, level: newLevel.value })
    newWord.value = ''
    message.success('已新增，3 秒内全端生效')
    await loadWords()
  } catch {
    // 4026 重复词由请求层提示
  }
}

/** 开关不回滚本地状态：改动以服务端为准，成功后重拉列表 */
const toggleWord = async (row: SensitiveWordVO): Promise<void> => {
  try {
    await adminSensitiveApi.setEnabled(row.id, row.status !== 1)
    message.success(row.status === 1 ? '已停用' : '已启用')
    await loadWords()
  } catch {
    void loadWords()
  }
}

const removeWord = async (row: SensitiveWordVO): Promise<void> => {
  try {
    await adminSensitiveApi.remove(row.id)
    message.success('已删除')
    await loadWords()
  } catch {
    // 请求层已提示
  }
}
</script>

<template>
  <div class="cm-page">
    <header class="cm-page__head">
      <h1 class="cm-page__title">系统设置</h1>
      <p class="cm-page__sub">
        审核 SLA 阈值与敏感词，均需 settings.edit；操作日志在独立页面。
      </p>
    </header>

    <div class="cm-card settings__card">
      <a-tabs v-model:active-key="active">
        <a-tab-pane key="sla" tab="SLA 配置" />
        <a-tab-pane key="word" tab="敏感词" />
      </a-tabs>

      <!-- SLA 配置 -->
      <template v-if="active === 'sla'">
        <p class="settings__note">
          SLA 分两级，不可混用：<b>响应时效</b>是平台对用户的承诺，<b>操作效率</b>是对审核员的要求。
          改这里的阈值会立刻改变审核队列的临期 / 超时判定。
        </p>
        <a-form layout="vertical" class="settings__form">
          <div class="settings__grid">
            <a-form-item label="首审响应达标（小时）">
              <a-input-number v-model:value="slaForm.firstResponseHours" :min="1" :max="72" />
            </a-form-item>
            <a-form-item label="P95 首审时长上限（小时）">
              <a-input-number v-model:value="slaForm.p95Hours" :min="1" :max="168" />
            </a-form-item>
            <a-form-item label="整体达标率下限（%）">
              <a-input-number v-model:value="slaForm.targetRate" :min="50" :max="100" />
            </a-form-item>
            <a-form-item label="积压红牌阈值（小时）">
              <a-input-number v-model:value="slaForm.backlogHours" :min="1" :max="168" />
            </a-form-item>
            <a-form-item label="单条平均处置（秒）">
              <a-input-number v-model:value="slaForm.avgHandleSeconds" :min="5" :max="300" />
            </a-form-item>
            <a-form-item label="申诉复审承诺（小时）">
              <a-input-number v-model:value="slaForm.appealHours" :min="1" :max="168" />
            </a-form-item>
          </div>
        </a-form>
        <div class="settings__actions">
          <a-space>
            <a-button
              v-perm="PERM_CODES.SETTINGS_EDIT"
              type="primary"
              shape="round"
              :loading="slaSaving"
              :disabled="slaLoading"
              @click="saveSla"
            >
              保存
            </a-button>
            <a-button shape="round" @click="resetSla">恢复默认</a-button>
          </a-space>
        </div>
      </template>

      <!-- 敏感词 -->
      <template v-else>
        <p class="settings__note">
          词库同时作用于<b>商品描述</b>、<b>动态</b>与两处<b>评论</b>：高危词直接拒绝发布，低危词替换成等长 `*`。
          改动 3 秒内全端生效，不用重启后端。
        </p>
        <div class="settings__filter">
          <a-space wrap :size="12">
            <a-input v-model:value="newWord" placeholder="新增敏感词" style="width: 200px" />
            <a-select v-model:value="newLevel" style="width: 110px">
              <a-select-option :value="1">拦截</a-select-option>
              <a-select-option :value="2">疑似</a-select-option>
            </a-select>
            <a-button v-perm="PERM_CODES.SETTINGS_EDIT" type="primary" shape="round" @click="addWord">
              新增
            </a-button>
          </a-space>
          <span class="settings__count">共 {{ wordTotal }} 个</span>
        </div>

        <a-table
          :columns="wordColumns"
          :data-source="words"
          :loading="wordLoading"
          row-key="id"
          size="middle"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'level'">
              <a-tag :color="record.level === 1 ? 'red' : 'orange'">
                {{ LEVEL_LABELS[record.level] ?? record.level }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-switch
                v-perm="PERM_CODES.SETTINGS_EDIT"
                :checked="record.status === 1"
                size="small"
                @change="toggleWord(record)"
              />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button
                v-perm="PERM_CODES.SETTINGS_EDIT"
                type="link"
                size="small"
                danger
                @click="removeWord(record)"
              >
                删除
              </a-button>
            </template>
          </template>
        </a-table>
      </template>
    </div>
  </div>
</template>

<style scoped>
.settings__card {
  padding: 0 var(--cm-space-5) var(--cm-space-5);
}

.settings__filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  flex-wrap: wrap;
  margin-bottom: var(--cm-space-4);
}

.settings__count {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.settings__note {
  margin: 0 0 var(--cm-space-5);
  padding: var(--cm-space-3);
  border-radius: var(--cm-radius-md);
  background: var(--cm-color-surface-muted);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  line-height: 1.7;
}

.settings__form {
  max-width: 720px;
}

.settings__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--cm-space-6);
}

.settings__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--cm-space-4);
}

@media (max-width: 1279px) {
  .settings__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
