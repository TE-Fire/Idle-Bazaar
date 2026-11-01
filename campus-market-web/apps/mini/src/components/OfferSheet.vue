<script setup lang="ts">
/**
 * 出价议价面板（notes/28），与 Web 端 OfferDialog 同口径。
 *
 * <p>金额区间「标价 10% ~ 标价减一分」与后端 `PRICE_INVALID` 一致，前端先拦一道
 * 只是不让用户白提交；真正的裁决在服务端。
 */

import { computed, ref, watch } from 'vue'
import BottomSheet from './BottomSheet.vue'
import { formatPrice } from '@campus/shared'
import { userOfferApi } from '@/api/user'

const props = defineProps<{
  modelValue: boolean
  itemId: string
  /** 商品挂牌价（字符串金额） */
  price: string
}>()

const emit = defineEmits<{
  'update:modelValue': [boolean]
  done: []
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const origin = computed(() => Number(props.price) || 0)
const floor = computed(() => Math.max(0.01, Number((origin.value * 0.1).toFixed(2))))
const ceiling = computed(() => Number((origin.value - 0.01).toFixed(2)))

/** 输入框用字符串：数字键盘敲到「1160.」这种中间态时 number 会把它抹掉 */
const input = ref('')
const submitting = ref(false)

const amount = computed(() => Number(input.value))
const valid = computed(
  () => Number.isFinite(amount.value) && amount.value >= floor.value && amount.value <= ceiling.value
)

/** 快捷折扣：按标价折算，低于下限的档位不出现 */
const presets = computed(() =>
  [0.9, 0.85, 0.8, 0.7]
    .map((r) => ({ rate: r, value: Number((origin.value * r).toFixed(2)) }))
    .filter((p) => p.value >= floor.value && p.value <= ceiling.value)
)

const discount = computed(() => {
  if (!valid.value || !origin.value) return ''
  return `${(amount.value / origin.value * 10).toFixed(1).replace('.0', '')} 折`
})

/** 打开即预填 9 折；immediate 覆盖「打开即 true」的首帧 */
watch(
  open,
  (v) => {
    if (!v) return
    input.value = (origin.value * 0.9).toFixed(2)
  },
  { immediate: true }
)

function pick(v: number): void {
  input.value = v.toFixed(2)
}

async function submit(): Promise<void> {
  if (submitting.value) return
  if (!valid.value) {
    uni.showToast({ title: `出价需在 ${formatPrice(floor.value)} ~ ${formatPrice(ceiling.value)}`, icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await userOfferApi.create(props.itemId, Number(amount.value.toFixed(2)))
    uni.showToast({ title: '已发出，等卖家答复', icon: 'none' })
    open.value = false
    emit('done')
  } catch {
    // request 层已提示（如「已有进行中的议价」）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet v-model="open" title="出个价">
    <view class="os">
      <view class="os__head">
        <text class="os__label">商品标价</text>
        <text class="os__origin">{{ formatPrice(origin) }}</text>
      </view>

      <view class="os__presets">
        <view
          v-for="p in presets"
          :key="p.rate"
          class="os__preset"
          :class="{ 'os__preset--on': Math.abs(amount - p.value) < 0.005 }"
          @click="pick(p.value)"
        >
          <text class="os__preset-rate">{{ (p.rate * 10).toFixed(1).replace('.0', '') }} 折</text>
          <text class="os__preset-price">{{ formatPrice(p.value) }}</text>
        </view>
      </view>

      <view class="os__field">
        <text class="os__unit">¥</text>
        <input v-model="input" class="os__input" type="digit" :placeholder="`${floor} ~ ${ceiling}`" />
        <text v-if="discount" class="os__discount">{{ discount }}</text>
      </view>

      <text class="os__range">可出价 {{ formatPrice(floor) }} ~ {{ formatPrice(ceiling) }}</text>
      <text class="os__tip">
        议价只在你和卖家之间进行，不会改动商品挂牌价，其他买家看到的仍是原价。卖家同意后需在 10 分钟内付款。
      </text>

      <button class="os__submit" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '发出议价' }}
      </button>
    </view>
  </BottomSheet>
</template>

<style scoped lang="scss">
.os {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
}

.os__head {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
}

.os__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.os__origin {
  font-size: $cm-font-size-lg;
  font-weight: 600;
  color: $cm-color-text-1;
}

.os__presets {
  display: flex;
  gap: $cm-space-2;
}

.os__preset {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: $cm-space-2 0;
  background: $cm-color-bg;
  border: 2rpx solid transparent;
  border-radius: $cm-radius-md;

  &--on {
    background: $cm-color-brand-bg;
    border-color: $cm-color-brand;
  }
}

.os__preset-rate {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

.os__preset-price {
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
}

.os__field {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
  padding: 0 $cm-space-3;
  height: 100rpx;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.os__unit {
  font-size: $cm-font-size-lg;
  color: $cm-color-text-2;
}

.os__input {
  flex: 1;
  height: 100%;
  font-size: $cm-font-size-lg;
  font-weight: 600;
  color: $cm-color-text-1;
}

.os__discount {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.os__range {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.os__tip {
  font-size: $cm-font-size-xs;
  line-height: 1.7;
  color: $cm-color-text-3;
  padding: $cm-space-2 $cm-space-3;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.os__submit {
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
