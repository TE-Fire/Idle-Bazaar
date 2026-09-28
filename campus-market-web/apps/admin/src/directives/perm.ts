/**
 * 按钮级权限指令 `v-perm`（tech/02 §6.2，与原型 data-perm 同义）
 *
 * 用法：
 *   <a-button v-perm="PERM_CODES.USER_BAN">封禁</a-button>
 *   <a-button v-perm="[PERM_CODES.ITEM_OFFLINE, PERM_CODES.ITEM_DELETE]">批量处置</a-button>
 *
 * <p>无权限时把元素从 DOM 移除（而不是 disabled）——原型口径是「不渲染」，
 * 灰按钮会暴露权限结构，也没必要。
 *
 * <p>已知边界：判断发生在 mounted，权限点中途变化（刷新权限）不会重算。
 * 本项目权限点只在登录/刷新权限时变，且这两个动作都会重挂载页面，够用。
 */

import type { Directive } from 'vue'
import type { PermCode } from '@campus/shared'
import { useUserStore } from '@/stores/user'

export const perm: Directive<HTMLElement, PermCode | PermCode[]> = {
  mounted(el, binding) {
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (!codes.length) return
    const user = useUserStore()
    if (!codes.some((code) => user.hasPerm(code))) {
      el.remove()
    }
  }
}
