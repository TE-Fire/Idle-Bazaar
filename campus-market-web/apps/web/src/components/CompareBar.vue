<script setup lang="ts">
/**
 * 对比条（桌面独占，design/01 §4.5）
 *
 * <p>fixed bottom，高 56px，已选 ≥1 件时出现，选中第 1 件时从下方滑入 200ms。
 * 4 个槽位：已填显示缩略图 + 右上角 ×，空槽是虚线框 `+`。
 */

import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { COMPARE_MAX, useCompareStore } from '@/stores/compare'

const compare = useCompareStore()
const router = useRouter()

/** 空槽位：4 − 已选 */
const slots = computed(() => COMPARE_MAX - compare.count)

const bg = (tints: [string, string]): string => `linear-gradient(135deg, ${tints[0]}, ${tints[1]})`

function startCompare(): void {
  // 至少 2 件才叫对比——1 件横评没有意义
  if (compare.count < 2) return
  void router.push({ name: 'compare', query: { ids: compare.ids.join(',') } })
}
</script>

<template>
  <Transition name="cmpbar">
    <div v-if="compare.visible" class="cmpbar">
      <div class="cmpbar__in">
        <div class="slots">
          <div v-for="it in compare.items" :key="it.id" class="slot slot--fill">
            <span class="thumb" :style="{ background: bg(it.tints) }" />
            <button type="button" class="slot__x" aria-label="移出对比" @click="compare.remove(it.id)">×</button>
          </div>
          <div v-for="i in slots" :key="`empty-${i}`" class="slot slot--empty">+</div>
        </div>

        <span class="txt">已选 {{ compare.count }} / {{ COMPARE_MAX }} 件</span>

        <div class="acts">
          <button type="button" class="btn" @click="compare.clear()">清空</button>
          <button
            type="button"
            class="btn btn--primary"
            :disabled="compare.count < 2"
            :title="compare.count < 2 ? '至少选 2 件才能对比' : ''"
            @click="startCompare"
          >
            开始对比
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped lang="scss">
.cmpbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 56px;
  background: var(--cm-color-surface);
  border-top: 1px solid var(--cm-color-border);
  box-shadow: 0 -2px 10px rgba(20, 20, 30, 0.06);
  z-index: 20;
}

.cmpbar__in {
  max-width: var(--cm-layout-content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--cm-space-6);
  display: flex;
  align-items: center;
  gap: var(--cm-space-5);
}

.slots {
  display: flex;
  gap: var(--cm-space-2);
}

/* 槽位 66×50（design/01 §4.5） */
.slot {
  position: relative;
  width: 66px;
  height: 50px;
  border-radius: var(--cm-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.slot--fill {
  border: 1px solid var(--cm-color-border-strong);
  overflow: hidden;
}

.thumb {
  width: 100%;
  height: 100%;
}

.slot__x {
  position: absolute;
  right: 2px;
  top: 2px;
  width: 14px;
  height: 14px;
  line-height: 1;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-1);
  background: var(--cm-color-surface);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.slot--empty {
  border: 1px dashed var(--cm-color-border-strong);
  color: var(--cm-color-text-3);
  font-size: var(--cm-font-size-md);
}

.txt {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
}

.acts {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.btn {
  height: 28px;
  padding: 0 var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border-strong);
  border-radius: var(--cm-radius-md);
  cursor: pointer;
}

/* primary 黄底深字，禁用态降饱和但不改色相 */
.btn--primary {
  height: 36px;
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-color: var(--cm-color-brand);
}

.btn--primary:disabled {
  cursor: not-allowed;
  background: var(--cm-color-brand-bg);
  border-color: var(--cm-color-brand-border);
  color: var(--cm-color-text-3);
}

/* 首次出现从下方滑入 200ms（design/01 §六） */
.cmpbar-enter-active,
.cmpbar-leave-active {
  transition: transform 200ms ease-out;
}

.cmpbar-enter-from,
.cmpbar-leave-to {
  transform: translateY(100%);
}
</style>
