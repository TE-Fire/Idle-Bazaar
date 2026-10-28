<script setup lang="ts">
/**
 * 举报弹窗（notes/22 批次 3）：动态、商品评论、动态评论复用同一个组件。
 *
 * <p>原因只给预置选项、不给自由文本——管理端要按原因聚类判断，一段随手写的话没法聚合。
 * 对象类型与 id 由调用方传，组件不判断「这能不能举报」，那是后端的事（重复举报返回 3005）。
 */

import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { REPORT_REASONS, userReportApi } from '@campus/api'
import { useUserStore } from '@/stores/user'

const props = defineProps<{
  modelValue: boolean
  /** 1 商品 / 2 用户 / 3 动态 / 4 商品评论 / 5 动态评论 */
  targetType: number
  targetId: string
}>()

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const open = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

const reason = ref<string>(REPORT_REASONS[0])
const submitting = ref(false)

async function submit(): Promise<void> {
  if (!props.targetId || submitting.value) return
  if (!userStore.userId) {
    open.value = false
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  submitting.value = true
  try {
    await userReportApi.submit({
      targetType: props.targetType,
      targetId: props.targetId,
      reason: reason.value
    })
    ElMessage.success('举报已提交，平台会尽快核实')
    open.value = false
  } catch {
    // http 层已提示（3005 重复举报等）
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="open" title="举报" width="360px" append-to-body>
    <p class="rp__tip">请选择举报原因，平台会核实后处理，恶意举报会影响信用分。</p>
    <el-radio-group v-model="reason" class="rp__group">
      <el-radio v-for="r in REPORT_REASONS" :key="r" :value="r">{{ r }}</el-radio>
    </el-radio-group>
    <template #footer>
      <el-button @click="open = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交举报</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.rp__tip {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  line-height: 1.6;
  color: var(--cm-color-text-3);
}

.rp__group {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}
</style>
