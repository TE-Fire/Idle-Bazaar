<script setup lang="ts">
/**
 * 拒绝议价面板（notes/28），与 Web 端 OfferRejectDialog 同口径。
 *
 * <p>理由可选：卖家拒绝已是明确态度，强制填写只会逼出「不」这类无信息内容。
 * 预置四条高频理由——买家看到「已经卖掉」和看到「价格太低」，后续动作完全不同。
 */

import { computed, ref, watch } from 'vue'
import BottomSheet from './BottomSheet.vue'
import { formatPrice } from '@campus/shared'
import { userOfferApi } from '@/api/user'

const props = defineProps<{
  modelValue: boolean
  offerId: string
  offerPrice: string
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  done: []
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

/** 四字以内，两个一排能放下 */
const PRESETS = ['价格太低', '已是底价', '已经卖掉', '暂不议价']

const reason = ref('')
const submitting = ref(false)

watch(open, (v) => {
  if (v) reason.value = ''
})

function pick(text: string): void {
  reason.value = reason.value === text ? '' : text
}

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    await userOfferApi.reject(props.offerId, reason.value.trim() || undefined)
    uni.showToast({ title: '已拒绝', icon: 'none' })
    open.value = false
    emit('done')
  } catch {
    // request 层已提示（如状态已变）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet v-model="open" title="拒绝议价">
    <view class="rs">
      <text class="rs__head">买家出价 {{ formatPrice(Number(offerPrice)) }}，拒绝后该议价立即关闭。</text>

      <view class="rs__presets">
        <view
          v-for="p in PRESETS"
          :key="p"
          class="rs__preset"
          :class="{ 'rs__preset--on': reason === p }"
          @click="pick(p)"
        >{{ p }}</view>
      </view>

      <textarea
        v-model="reason"
        class="rs__input"
        :maxlength="200"
        placeholder="补充说明（可不填，买家能看到）"
      />
      <text class="rs__tip">拒绝后买家可以换个价格重新出价。</text>

      <button class="rs__submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '确认拒绝' }}
      </button>
    </view>
  </BottomSheet>
</template>

<style scoped lang="scss">
.rs {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.rs__head {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.rs__presets {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
}

.rs__preset {
  padding: 8rpx $cm-space-3;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border: 2rpx solid transparent;
  border-radius: $cm-radius-pill;

  &--on {
    color: $cm-color-brand-text;
    background: $cm-color-brand-bg;
    border-color: $cm-color-brand;
  }
}

.rs__input {
  width: 100%;
  height: 140rpx;
  padding: $cm-space-2 $cm-space-3;
  box-sizing: border-box;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.rs__tip {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.rs__submit {
  margin: 0;
  height: 92rpx;
  line-height: 92rpx;
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: #fff;
  background: $cm-color-brand;
  border-radius: $cm-radius-pill;
  box-shadow: $cm-shadow-brand;

  &[disabled] {
    opacity: 0.55;
    box-shadow: none;
  }
}
</style>
