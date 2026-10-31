<script setup lang="ts">
/**
 * 地址管理（收货 SHIP / 自提 PICKUP）
 *
 * <p>两类地址同页切换（`?kind=1|2`）：收货地址给「送货上门」下单时选，自提地址给「发布自提商品」时选。
 * 后端按 `userId + kind` 隔离，这里只是把两侧的地址簿分别列出来管理。
 *
 * <p>地址被订单引用后会在下单那一刻快照进订单，所以这里改 / 删都不影响历史订单——文案不承诺「已下单也能改」。
 */

import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  userAddressApi,
  ADDRESS_KIND,
  fullAddress,
  type AddressKind,
  type UserAddressReq,
  type UserAddressVO
} from '@campus/api'

const route = useRoute()
const router = useRouter()

/** 当前地址类型：接收 ?kind=2 切到自提，其余一律按收货地址 */
const kind = computed<AddressKind>(() => (route.query.kind === '2' ? ADDRESS_KIND.PICKUP : ADDRESS_KIND.SHIP))

const list = ref<UserAddressVO[]>([])
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    list.value = await userAddressApi.list(kind.value)
  } catch {
    // http 层已提示（未登录会跳转）
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(kind, load)

function switchKind(k: AddressKind): void {
  router.replace({ name: 'address', query: { kind: String(k) } })
}

/* ── 新增 / 编辑弹层 ─────────────────────────────────────── */

const dialogVisible = ref(false)
const editingId = ref('')
const submitting = ref(false)

function blank(): UserAddressReq {
  return {
    kind: kind.value,
    contactName: '',
    contactPhone: '',
    province: '',
    city: '',
    district: '',
    detail: '',
    isDefault: 0
  }
}

const form = ref<UserAddressReq>(blank())

function openCreate(): void {
  editingId.value = ''
  form.value = blank()
  dialogVisible.value = true
}

function openEdit(a: UserAddressVO): void {
  editingId.value = a.id
  form.value = {
    kind: a.kind,
    contactName: a.contactName,
    contactPhone: a.contactPhone,
    province: a.province ?? '',
    city: a.city ?? '',
    district: a.district ?? '',
    detail: a.detail,
    isDefault: a.isDefault
  }
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!form.value.contactName.trim() || !form.value.contactPhone.trim() || !form.value.detail.trim()) {
    ElMessage.warning({ message: '联系人、电话、详细地址都要填', duration: 2000 })
    return
  }
  submitting.value = true
  try {
    const payload: UserAddressReq = { ...form.value, kind: kind.value }
    if (editingId.value) {
      await userAddressApi.update(editingId.value, payload)
      ElMessage.success({ message: '已保存', duration: 2000 })
    } else {
      await userAddressApi.create(payload)
      ElMessage.success({ message: '已添加', duration: 2000 })
    }
    dialogVisible.value = false
    await load()
  } catch {
    // http 层已提示具体原因
  } finally {
    submitting.value = false
  }
}

async function setDefault(a: UserAddressVO): Promise<void> {
  if (a.isDefault === 1) return
  try {
    await userAddressApi.setDefault(a.id)
    await load()
  } catch {
    // http 层已提示
  }
}

async function remove(a: UserAddressVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后不可恢复，确认删除该地址？', '删除地址', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await userAddressApi.remove(a.id)
    ElMessage.success({ message: '已删除', duration: 2000 })
    await load()
  } catch {
    // http 层已提示
  }
}
</script>

<template>
  <div class="cm-shell address">
    <section class="pane">
      <header class="head">
        <div class="tabs">
          <button
            type="button"
            class="tab"
            :class="{ 'tab--on': kind === ADDRESS_KIND.SHIP }"
            @click="switchKind(ADDRESS_KIND.SHIP)"
          >
            收货地址
          </button>
          <button
            type="button"
            class="tab"
            :class="{ 'tab--on': kind === ADDRESS_KIND.PICKUP }"
            @click="switchKind(ADDRESS_KIND.PICKUP)"
          >
            自提地址
          </button>
        </div>
        <el-button type="primary" @click="openCreate">新增地址</el-button>
      </header>

      <p class="hint">
        {{
          kind === ADDRESS_KIND.PICKUP
            ? '发布「自提」商品时从这里选一个作为提货点，买家下单后到该地址自取。'
            : '购买「送货上门」商品时从这里选一个作为收货地址。'
        }}
      </p>

      <div v-loading="loading" class="list">
        <div v-if="!loading && !list.length" class="empty">
          还没有{{ kind === ADDRESS_KIND.PICKUP ? '自提' : '收货' }}地址，点右上角「新增地址」添加
        </div>

        <div v-for="a in list" :key="a.id" class="card" :class="{ 'card--default': a.isDefault === 1 }">
          <div class="card__main">
            <div class="card__line">
              <span class="card__name">{{ a.contactName }}</span>
              <span class="card__phone">{{ a.contactPhone }}</span>
              <span v-if="a.isDefault === 1" class="card__badge">默认</span>
            </div>
            <p class="card__addr">{{ fullAddress(a) }}</p>
          </div>
          <div class="card__ops">
            <el-button v-if="a.isDefault !== 1" link type="primary" @click="setDefault(a)">设为默认</el-button>
            <el-button link @click="openEdit(a)">编辑</el-button>
            <el-button link type="danger" @click="remove(a)">删除</el-button>
          </div>
        </div>
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑地址' : '新增地址'"
      width="440px"
      append-to-body
    >
      <el-form label-width="72px">
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" maxlength="20" placeholder="收/提货人姓名" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.contactPhone" maxlength="20" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="省市区">
          <div class="region">
            <el-input v-model="form.province" placeholder="省" />
            <el-input v-model="form.city" placeholder="市" />
            <el-input v-model="form.district" placeholder="区/县" />
          </div>
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.detail" type="textarea" :rows="2" maxlength="120" placeholder="门牌号 / 楼栋 / 摊位号" />
        </el-form-item>
        <el-form-item label="默认">
          <el-switch :model-value="form.isDefault === 1" @change="form.isDefault = $event ? 1 : 0" />
          <span class="dialog__tip">设为{{ kind === ADDRESS_KIND.PICKUP ? '自提' : '收货' }}默认地址</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.address {
  padding-top: var(--cm-space-4);
}

.pane {
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
}

.tabs {
  display: flex;
  gap: var(--cm-space-2);
}

.tab {
  padding: var(--cm-space-2) var(--cm-space-4);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  color: var(--cm-color-text-3);
  background: transparent;
  border: none;
  border-radius: var(--cm-radius-lg);
  cursor: pointer;
}

.tab--on {
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand-bg);
}

.hint {
  margin: var(--cm-space-3) 0 var(--cm-space-4);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
  min-height: 80px;
}

.empty {
  padding: var(--cm-space-8) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--cm-space-4);
  padding: var(--cm-space-4);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-lg);
  transition: border-color 160ms ease-out;
}

.card:hover {
  border-color: var(--cm-color-border-strong);
}

.card--default {
  border-color: var(--cm-color-brand);
  background: var(--cm-color-brand-bg);
}

.card__main {
  min-width: 0;
}

.card__line {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.card__name {
  font-weight: 600;
  font-size: var(--cm-font-size-md);
}

.card__phone {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.card__badge {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-sm);
  padding: 0 6px;
}

.card__addr {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  word-break: break-all;
}

.card__ops {
  display: flex;
  flex: none;
  align-items: center;
  gap: var(--cm-space-1);
}

.region {
  display: flex;
  gap: var(--cm-space-2);
  width: 100%;
}

.dialog__tip {
  margin-left: var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

@media (max-width: 720px) {
  .card {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
