/**
 * 对比篮（桌面独占能力，design/01 §4.5）
 *
 * <p>为什么存**快照**而不是只存 id：对比条与对比页在列表之外也要显示缩略信息，
 * 只存 id 就得回查列表接口；而对比最多 4 件，存快照的代价可以忽略。
 * 后端商品接口就绪后，改为进入对比页时按 ids 拉一次最新数据（价格可能已变）。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { GoodsItem } from '@campus/shared'

/** 上限 4 件：对比条 4 个槽位（design/01 §4.5） */
export const COMPARE_MAX = 4

export const useCompareStore = defineStore('compare', () => {
  const items = ref<GoodsItem[]>([])

  const count = computed(() => items.value.length)
  const ids = computed(() => items.value.map((it) => it.id))
  const visible = computed(() => items.value.length > 0)

  const has = (id: string): boolean => items.value.some((it) => it.id === id)

  /** 返回是否加入成功——满了要提示，不能静默失败 */
  function add(item: GoodsItem): boolean {
    if (has(item.id)) return true
    if (items.value.length >= COMPARE_MAX) return false
    items.value.push(item)
    return true
  }

  function remove(id: string): void {
    items.value = items.value.filter((it) => it.id !== id)
  }

  /** 卡片上的「＋对比」：已选则取消，符合「点一下切换」的直觉 */
  function toggle(item: GoodsItem): boolean {
    if (has(item.id)) {
      remove(item.id)
      return true
    }
    return add(item)
  }

  function clear(): void {
    items.value = []
  }

  return { items, count, ids, visible, has, add, remove, toggle, clear }
})
