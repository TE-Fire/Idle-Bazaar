<script setup lang="ts">
/**
 * 地址新增 / 编辑（收货 SHIP / 自提 PICKUP 同一页，靠 kind 区分）
 *
 * <p>没有「查单条」接口：地址簿很小，编辑时拉一次同类列表按 id 找行即可，
 * 比把六七个字段塞进 URL 更可靠（转义、长度、刷新丢失都是坑）。
 *
 * <p>kind 不可改——服务端也会拦（跨类型会让收货地址变成自提地址、破坏隔离），
 * 这里连入口都不给，避免白填一遍再报错。
 */

import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ADDRESS_KIND, addressApi, type AddressReq, type AddressVO } from '@/api/address'
import CmNav from '@/components/CmNav.vue'

const kind = ref<number>(ADDRESS_KIND.ship)
const editingId = ref('')
const saving = ref(false)

const form = ref({
  contactName: '',
  contactPhone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false
})

onLoad(async (query) => {
  kind.value = Number(query?.kind) === ADDRESS_KIND.pickup ? ADDRESS_KIND.pickup : ADDRESS_KIND.ship
  uni.setNavigationBarTitle({ title: kind.value === ADDRESS_KIND.pickup ? '提货地址' : '收货地址' })
  const id = typeof query?.id === 'string' ? query.id : ''
  if (!id) return
  editingId.value = id
  try {
    const list = await addressApi.list(kind.value)
    const hit = list.find((a) => a.id === id)
    if (hit) fill(hit)
  } catch {
    // request 层已提示
  }
})

function fill(a: AddressVO): void {
  form.value.contactName = a.contactName
  form.value.contactPhone = a.contactPhone
  form.value.province = a.province ?? ''
  form.value.city = a.city ?? ''
  form.value.district = a.district ?? ''
  form.value.detail = a.detail
  form.value.isDefault = a.isDefault === 1
}

async function save(): Promise<void> {
  const contactName = form.value.contactName.trim()
  const contactPhone = form.value.contactPhone.trim()
  const detail = form.value.detail.trim()
  if (!contactName) {
    uni.showToast({ title: '请填写联系人', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(contactPhone)) {
    uni.showToast({ title: '请填写 11 位手机号', icon: 'none' })
    return
  }
  if (!detail) {
    uni.showToast({ title: '请填写详细地址', icon: 'none' })
    return
  }
  const req: AddressReq = {
    kind: kind.value,
    contactName,
    contactPhone,
    province: form.value.province.trim() || undefined,
    city: form.value.city.trim() || undefined,
    district: form.value.district.trim() || undefined,
    detail,
    isDefault: form.value.isDefault ? 1 : 0
  }
  saving.value = true
  try {
    if (editingId.value) {
      await addressApi.update(editingId.value, req)
    } else {
      await addressApi.create(req)
    }
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } catch {
    // request 层已提示（1000 参数 / 1001 未登录跳登录）
  } finally {
    saving.value = false
  }
}

function toggleDefault(): void {
  form.value.isDefault = !form.value.isDefault
}
</script>

<template>
  <view class="page">
    <CmNav :title="editingId ? '编辑地址' : '新增地址'" />

    <view class="sec">
      <view class="field">
        <text class="field__k">联系人</text>
        <input v-model="form.contactName" class="field__v" placeholder="收件人 / 提货联系人" maxlength="20" />
      </view>
      <view class="field">
        <text class="field__k">手机号</text>
        <input v-model="form.contactPhone" class="field__v" type="number" placeholder="11 位手机号" maxlength="11" />
      </view>
    </view>

    <view class="sec">
      <text class="sec__t">所在地区</text>
      <view class="region">
        <input v-model="form.province" class="region__i" placeholder="省" maxlength="10" />
        <input v-model="form.city" class="region__i" placeholder="市" maxlength="10" />
        <input v-model="form.district" class="region__i" placeholder="区 / 县" maxlength="10" />
      </view>
      <textarea
        v-model="form.detail"
        class="textarea"
        placeholder="详细地址：楼栋、门牌号；自提填提货点（如：3 号宿舍楼 101）"
        maxlength="100"
      />
    </view>

    <view class="sec">
      <view class="switch-row">
        <view class="switch-row__l">
          <text>设为默认地址</text>
          <text class="switch-row__h">同类地址只保留一个默认，下单时优先选中</text>
        </view>
        <view class="sw" :class="{ 'sw--on': form.isDefault }" @click="toggleDefault">
          <view class="sw__knob" />
        </view>
      </view>
    </view>

    <view class="submit" hover-class="submit--on" :class="{ 'submit--off': saving }" @click="save">
      <text class="submit__t">{{ saving ? '保存中' : '保存' }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-4 $cm-space-4 200rpx;
}

.sec {
  margin-bottom: $cm-space-4;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
}

.sec__t {
  display: block;
  margin-bottom: $cm-space-4;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

/* 行式字段：左标签定宽，右输入自适应，竖线分隔感靠底色 */
.field {
  display: flex;
  align-items: center;
  height: 88rpx;
  border-bottom: 1rpx solid $cm-color-gray-100;
}

.field:last-child {
  border-bottom: none;
}

.field__k {
  width: 168rpx;
  flex: none;
  font-size: $cm-font-size-base;
  color: $cm-color-text-2;
}

.field__v {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.region {
  display: flex;
  gap: $cm-space-2;
  margin-bottom: $cm-space-3;
}

.region__i {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.textarea {
  width: 100%;
  height: 160rpx;
  padding: $cm-space-3;
  box-sizing: border-box;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $cm-space-4;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.switch-row__l {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.switch-row__h {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

/* ── 开关（与发布页同一套自绘件，原生 switch 在 H5 尺寸不可控）── */
.sw {
  flex: none;
  width: 88rpx;
  height: 50rpx;
  padding: 5rpx;
  box-sizing: border-box;
  border-radius: $cm-radius-pill;
  background: $cm-color-gray-300;
  transition: background-color 220ms ease-out;
}

.sw--on {
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
}

.sw__knob {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2rpx 6rpx rgba(17, 24, 39, 0.22);
  transition: transform 240ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.sw--on .sw__knob {
  transform: translateX(38rpx);
}

.submit {
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out;
}

.submit--on {
  transform: scale(0.98);
}

.submit--off {
  opacity: 0.6;
}

.submit__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: #fff;
}
</style>
