<script setup lang="ts">
/**
 * 成色标签（design/02 §5.4，本平台核心组件）
 *
 * <p>四端（Web / 小程序 / 后台 / API）必须使用**同一套 A–D 编码**，文案与颜色一一对应：
 * A/B 绿、C 橙、D 灰。所以文案与评级都取自 `@campus/shared`，不在本端另写一份。
 */

import { computed } from 'vue'
import { CONDITION_LABELS, type ConditionGrade } from '@campus/shared'

const props = defineProps<{ grade: ConditionGrade }>()
const text = computed(() => `${props.grade} ${CONDITION_LABELS[props.grade]}`)
</script>

<template>
  <text class="cond" :class="grade.toLowerCase()">{{ text }}</text>
</template>

<style scoped lang="scss">
.cond {
  font-size: $cm-font-size-xs;
  line-height: 1.6;
  padding: 0 8rpx;
  border: 1rpx solid;
  border-radius: $cm-radius-sm;
}

.a,
.b {
  color: $cm-color-ok;
  border-color: rgba(15, 169, 104, 0.35);
}

.c {
  color: $cm-color-warn;
  border-color: rgba(255, 143, 31, 0.35);
}

.d {
  color: $cm-color-text-2;
  border-color: $cm-color-border-strong;
}
</style>
