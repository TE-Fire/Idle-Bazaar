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

import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { PERM_CODES, type SensitiveWord } from '@campus/shared'
import { DEFAULT_SLA, SENSITIVE_WORDS } from '@/mock/settings'

const active = ref<'sla' | 'word'>('sla')

/* ── SLA 配置：改的是 mock 里的同一份对象，A5 队列立刻按新阈值判定 ── */

const slaForm = reactive({ ...DEFAULT_SLA })

const saveSla = (): void => {
  Object.assign(DEFAULT_SLA, slaForm)
  message.success('SLA 阈值已更新，审核队列的临期/超时判定即时生效')
}

const resetSla = (): void => {
  Object.assign(slaForm, {
    firstResponseHours: 2,
    p95Hours: 6,
    targetRate: 95,
    backlogHours: 24,
    avgHandleSeconds: 30,
    appealHours: 48
  })
  message.info('已恢复为业务文档默认值')
}

/* ── 敏感词 ── */

const words = ref<SensitiveWord[]>(SENSITIVE_WORDS.map((w) => ({ ...w })))
const newWord = ref('')
const newLevel = ref<SensitiveWord['level']>('疑似')

const wordColumns: TableColumnsType = [
  { title: '敏感词', dataIndex: 'word', width: 200 },
  { title: '级别', key: 'level', width: 110 },
  { title: '状态', key: 'enabled', width: 110 },
  { title: '操作', key: 'action', width: 100 }
]

const addWord = (): void => {
  const w = newWord.value.trim()
  if (!w) {
    message.warning('请输入敏感词')
    return
  }
  if (words.value.some((x) => x.word === w)) {
    message.warning('该词已存在')
    return
  }
  words.value.unshift({ id: `w-${Date.now()}`, word: w, level: newLevel.value, enabled: true })
  newWord.value = ''
  message.success('已新增')
}

const toggleWord = (row: SensitiveWord): void => {
  row.enabled = !row.enabled
}

const removeWord = (row: SensitiveWord): void => {
  words.value = words.value.filter((w) => w.id !== row.id)
  message.success('已删除')
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
            <a-button v-perm="PERM_CODES.SETTINGS_EDIT" type="primary" shape="round" @click="saveSla">
              保存
            </a-button>
            <a-button shape="round" @click="resetSla">恢复默认</a-button>
          </a-space>
        </div>
      </template>

      <!-- 敏感词 -->
      <template v-else>
        <div class="settings__filter">
          <a-space wrap :size="12">
            <a-input v-model:value="newWord" placeholder="新增敏感词" style="width: 200px" />
            <a-select v-model:value="newLevel" style="width: 110px">
              <a-select-option value="拦截">拦截</a-select-option>
              <a-select-option value="疑似">疑似</a-select-option>
            </a-select>
            <a-button v-perm="PERM_CODES.SETTINGS_EDIT" type="primary" shape="round" @click="addWord">
              新增
            </a-button>
          </a-space>
          <span class="settings__count">共 {{ words.length }} 个</span>
        </div>

        <a-table :columns="wordColumns" :data-source="words" row-key="id" size="middle" :pagination="false">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'level'">
              <a-tag :color="record.level === '拦截' ? 'red' : 'orange'">{{ record.level }}</a-tag>
            </template>
            <template v-else-if="column.key === 'enabled'">
              <a-switch
                v-perm="PERM_CODES.SETTINGS_EDIT"
                :checked="record.enabled"
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
