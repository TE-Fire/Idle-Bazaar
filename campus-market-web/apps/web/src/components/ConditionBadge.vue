<script setup lang="ts">
/**
 * 成色标签
 *
 * <p>配色取自 `prototypes/web/web.css` 的 `.cond.a~d`：A/B 绿、C 橙、D 灰。
 * 注意不是 `grade-a~d` 令牌（那里 B 是蓝色）——原型 CSS 是视觉依据，令牌只是数值层。
 */

import { computed } from 'vue'
import { CONDITION_LABELS, type ConditionGrade } from '@campus/shared'

const props = defineProps<{ grade: ConditionGrade; showLabel?: boolean }>()

const text = computed(() => (props.showLabel ? `${props.grade} 级 · ${CONDITION_LABELS[props.grade]}` : `${props.grade} 级`))
</script>

<template>
  <span class="cond" :class="grade.toLowerCase()">{{ text }}</span>
</template>

<style scoped lang="scss">
.cond {
  display: inline-block;
  font-size: var(--cm-font-size-xs);
  line-height: 18px;
  padding: 0 5px;
  border: 1px solid;
  border-radius: var(--cm-radius-sm);
  white-space: nowrap;
}

/* A/B 绿（成色良好）、C 橙（一般）、D 灰（较旧）——与原型 .cond 完全一致 */
.a,
.b {
  color: var(--cm-color-ok);
  border-color: rgba(15, 169, 104, 0.35);
}

.c {
  color: var(--cm-color-warn);
  border-color: rgba(255, 143, 31, 0.35);
}

.d {
  color: var(--cm-color-text-2);
  border-color: var(--cm-color-border-strong);
}
</style>
