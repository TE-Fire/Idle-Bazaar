<script setup lang="ts">
/**
 * 出价议价弹窗（notes/28）。
 *
 * <p>金额只能落在「标价 10% ~ 标价」之间，与后端 `OfferErrorCode.PRICE_INVALID` 同口径；
 * 前端拦一道是为了不让用户白提交一次请求，真正的裁决仍在服务端。
 *
 * <p>不给自由输入框而给折扣快捷键：议价发生在「便宜一点」的心理价位上，
 * 直接点「9 折」比让人算差额快得多，输入框只是允许微调。
 */

import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userOfferApi } from '@campus/api'
import { formatPrice } from '@campus/shared'

const props = defineProps<{
  modelValue: boolean
  itemId: string
  /** 商品当前挂牌价（字符串金额，与接口一致） */
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
/** 出价下限：标价的 10%，最低 0.01，与后端一致 */
const floor = computed(() => Math.max(0.01, Number((origin.value * 0.1).toFixed(2))))
/** 出价上限：必须低于标价一分，等于标价就没有议价的意义 */
const ceiling = computed(() => Number((origin.value - 0.01).toFixed(2)))

const amount = ref<number | null>(null)
const submitting = ref(false)

/** 快捷折扣：按标价直接折算，低于下限的档位不出现 */
const presets = computed(() =>
  [0.9, 0.85, 0.8, 0.7]
    .map((r) => ({ rate: r, value: Number((origin.value * r).toFixed(2)) }))
    .filter((p) => p.value >= floor.value && p.value <= ceiling.value)
)

/** 折数：留 1 位小数，8.3 折这种比「省 ¥123」更有议价感；整数折扣不显示 .0 */
const discount = computed(() => {
  if (!amount.value || !origin.value) return ''
  return `${(amount.value / origin.value * 10).toFixed(1).replace('.0', '')} 折`
})

const canSubmit = computed(() => {
  const v = amount.value
  return v != null && v >= floor.value && v <= ceiling.value
})

// 每次打开重置成当前标价的 9 折；immediate 覆盖「打开即 true」的首帧（组件复用时不触发 watch）
watch(
  open,
  (v) => {
    if (!v) return
    amount.value = Number((origin.value * 0.9).toFixed(2))
  },
  { immediate: true }
)

async function submit(): Promise<void> {
  if (!canSubmit.value || submitting.value || amount.value == null) return
  submitting.value = true
  try {
    await userOfferApi.create(props.itemId, Number(amount.value.toFixed(2)))
    ElMessage.success('议价已发出，等待卖家答复')
    open.value = false
    emit('done')
  } catch {
    // http 层已提示（7004 已有进行中 / 7003 价格不合规）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="出个价" width="420px" append-to-body>
    <div class="od">
      <div class="od__head">
        <span class="od__label">商品标价</span>
        <span class="od__origin">{{ formatPrice(origin) }}</span>
      </div>

      <div class="od__presets">
        <button
          v-for="p in presets"
          :key="p.rate"
          type="button"
          class="od__preset"
          :class="{ on: amount != null && Math.abs(amount - p.value) < 0.005 }"
          @click="amount = p.value"
        >
          {{ (p.rate * 10).toFixed(1).replace('.0', '') }} 折
          <span class="od__preset-price">{{ formatPrice(p.value) }}</span>
        </button>
      </div>

      <div class="od__field">
        <span class="od__unit">¥</span>
        <el-input-number
          v-model="amount"
          :min="floor"
          :max="ceiling"
          :precision="2"
          :step="10"
          :controls="false"
          class="od__input"
          placeholder="输入你的出价"
        />
        <span v-if="discount" class="od__discount">{{ discount }}</span>
      </div>

      <p class="od__range">
        可出价 {{ formatPrice(floor) }} ~ {{ formatPrice(ceiling) }}
      </p>
      <p class="od__tip">
        议价只在你和卖家之间进行，<strong>不会改动商品挂牌价</strong>，其他买家看到的仍是原价。
        卖家同意后需在 10 分钟内付款，超时失效。
      </p>
    </div>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
        发出议价
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.od {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.od__head {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
}

.od__label {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* 不加删除线：挂牌价从未被改动，划线会误导成「已作废的原价」 */
.od__origin {
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
  color: var(--cm-color-text);
}

.od__presets {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
}

.od__preset {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-md);
  cursor: pointer;

  &.on {
    color: var(--cm-color-text);
    border-color: var(--cm-color-brand);
    background: var(--cm-color-brand-bg);
  }
}

.od__preset-price {
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
}

.od__field {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  padding: 4px 10px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-md);

  &:focus-within {
    border-color: var(--cm-color-brand);
  }
}

.od__unit {
  font-size: var(--cm-font-size-md);
  color: var(--cm-color-text-2);
}

.od__input {
  flex: 1;
  width: auto;

  :deep(.el-input__wrapper) {
    box-shadow: none;
    background: transparent;
    padding: 0;
  }

  :deep(.el-input__inner) {
    font-size: var(--cm-font-size-lg);
    font-weight: 600;
    text-align: left;
  }
}

.od__discount {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.od__range {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.od__tip {
  margin: 0;
  padding: var(--cm-space-2) var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  line-height: 1.7;
  color: var(--cm-color-text-3);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-md);
}
</style>
