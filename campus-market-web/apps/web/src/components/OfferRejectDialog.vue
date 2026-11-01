<script setup lang="ts">
/**
 * 拒绝议价弹窗（notes/28）。
 *
 * <p>理由是**可选**的：卖家拒绝已是明确态度，强制填理由只会逼出「不」这类无信息内容。
 * 但给了四个高频理由的快捷标签——买家看到「已卖掉」和看到「价格太低」,
 * 后续行为完全不同（前者去别处找，后者可能加价再谈）。
 */

import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { userOfferApi } from '@campus/api'
import { formatPrice } from '@campus/shared'

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

/** 高频理由，点一下填进输入框，仍可改。控制在四字以内，四个能排一行 */
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
    ElMessage.success('已拒绝该议价')
    open.value = false
    emit('done')
  } catch {
    // http 层已提示（7005 状态已变等）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="拒绝议价" width="420px" append-to-body>
    <div class="rd">
      <p class="rd__head">
        买家出价 <strong>{{ formatPrice(Number(offerPrice)) }}</strong>，拒绝后该议价立即关闭。
      </p>

      <div class="rd__presets">
        <button
          v-for="p in PRESETS"
          :key="p"
          type="button"
          class="rd__preset"
          :class="{ on: reason === p }"
          @click="pick(p)"
        >{{ p }}</button>
      </div>

      <el-input
        v-model="reason"
        type="textarea"
        :rows="2"
        maxlength="200"
        show-word-limit
        placeholder="补充说明（可不填，买家能看到）"
      />
      <p class="rd__tip">拒绝后买家可以换个价格重新出价。</p>
    </div>

    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认拒绝</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.rd {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.rd__head {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.rd__presets {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
}

.rd__preset {
  padding: 4px 10px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  background: var(--cm-color-bg);
  border: 1px solid var(--cm-color-border);
  border-radius: 999px;
  cursor: pointer;

  &.on {
    color: var(--cm-color-text);
    border-color: var(--cm-color-warn);
    background: var(--cm-color-warn-bg);
  }
}

.rd__tip {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>
