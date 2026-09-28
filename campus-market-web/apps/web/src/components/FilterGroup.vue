<script setup lang="ts">
/**
 * 侧栏筛选组（design/01 §4.6）
 *
 * <p>结构固定：`h4 标题（右对齐「清空」）+ 内容 + 1px 分隔线`，组间距 16px。
 * 用 `fieldset + legend` 而不是 div+h4：文档 §七 明确要求筛选组语义化，
 * 否则屏幕阅读器读不出「这几个复选框属于同一组条件」。
 */

withDefaults(defineProps<{ title: string; clearable?: boolean }>(), { clearable: false })

const emit = defineEmits<{ clear: [] }>()
</script>

<template>
  <fieldset class="fgrp">
    <legend class="fgrp__hd">
      <span>{{ title }}</span>
      <button v-if="clearable" type="button" class="fgrp__clear" @click="emit('clear')">清空</button>
    </legend>
    <div class="fgrp__bd">
      <slot />
    </div>
  </fieldset>
</template>

<style scoped lang="scss">
.fgrp {
  margin: 0;
  padding: 0 0 var(--cm-space-4);
  border: none;
  border-bottom: 1px solid var(--cm-color-border);
}

.fgrp__hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.fgrp__clear {
  font-size: var(--cm-font-size-xs);
  font-weight: 400;
  color: var(--cm-color-text-3);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}

.fgrp__clear:hover {
  color: var(--cm-color-text-1);
}

.fgrp__bd {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

/* 组内复选框行：原生 accent-color 转品牌黄（design/01 §4.3） */
:deep(label) {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  cursor: pointer;
}

:deep(input[type='checkbox']) {
  accent-color: var(--cm-color-brand);
}
</style>
