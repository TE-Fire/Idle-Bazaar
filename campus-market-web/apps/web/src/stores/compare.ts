/**
 * 对比篮（桌面独占能力，design/01 §4.5）
 *
 * <p>为什么存**快照**而不是只存 id：对比条与对比页在列表之外也要显示缩略信息，
 * 只存 id 就得回查列表接口；而对比最多 4 件，存快照的代价可以忽略。
 *
 * <p>两条约束都在 add 里拦：① 上限 4 件；② **仅限同类**——第一件入篮后按其
 * categoryId 锁定，跨类返回 'mismatch'，由调用方提示并把用户引导到同类列表
 * （对比条的 `+` 会带 `?cat=` 跳首页已筛好的同类列表）。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { GoodsItem } from '@campus/shared'

/** 上限 4 件：对比条 4 个槽位（design/01 §4.5） */
export const COMPARE_MAX = 4

export type CompareAddResult = 'ok' | 'full' | 'mismatch'

export const useCompareStore = defineStore('compare', () => {
  const items = ref<GoodsItem[]>([])

  const count = computed(() => items.value.length)
  const ids = computed(() => items.value.map((it) => it.id))
  const visible = computed(() => items.value.length > 0)

  /** 第一件入篮的商品锁定的分类；空篮为 '' */
  const lockedCategoryId = computed(() => items.value[0]?.categoryId ?? '')
  const lockedCategoryName = computed(() => items.value[0]?.category ?? '')

  const has = (id: string): boolean => items.value.some((it) => it.id === id)

  function add(item: GoodsItem): CompareAddResult {
    if (has(item.id)) return 'ok'
    if (items.value.length >= COMPARE_MAX) return 'full'
    if (items.value.length > 0 && item.categoryId !== lockedCategoryId.value) return 'mismatch'
    items.value.push(item)
    return 'ok'
  }

  function remove(id: string): void {
    items.value = items.value.filter((it) => it.id !== id)
  }

  /** 卡片上的「＋对比」：已选则取消，符合「点一下切换」的直觉 */
  function toggle(item: GoodsItem): CompareAddResult {
    if (has(item.id)) {
      remove(item.id)
      return 'ok'
    }
    return add(item)
  }

  function clear(): void {
    items.value = []
  }

  return {
    items,
    count,
    ids,
    visible,
    lockedCategoryId,
    lockedCategoryName,
    has,
    add,
    remove,
    toggle,
    clear
  }
})
