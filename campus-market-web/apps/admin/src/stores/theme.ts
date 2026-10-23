/**
 * 主题（一键换肤）
 *
 * <p>为什么两套机制并行：antd 组件（按钮/表格/表单）的颜色走 App.vue 里 ConfigProvider
 * 的 theme.token，那是 JS 侧的；我们自己写的样式走 CSS 变量（--cm-color-brand 等）。
 * 换肤必须同时改两边，只改一处就会出现「按钮是新的、链接是旧的」。
 *
 * <p>持久化用 localStorage（键 cm-admin-theme）：刷新不丢，且与用户会话无关——
 * 换肤是设备级偏好，不是账号级。
 */

import { acceptHMRUpdate, defineStore } from 'pinia'

export type ThemeKey = 'apple' | 'violet' | 'forest' | 'sunset'

export interface ThemeMeta {
  key: ThemeKey
  label: string
  /** 色板展示用的主色（与 antd token / CSS 变量三处同源） */
  swatch: string
}

/** 四套预设：顺序即顶栏展示顺序，apple 是默认（与 @campus/tokens 生成值一致） */
export const THEMES: ThemeMeta[] = [
  { key: 'apple', label: '经典蓝', swatch: '#0071E3' },
  { key: 'violet', label: '暗夜紫', swatch: '#7C4DFF' },
  { key: 'forest', label: '森林绿', swatch: '#0FA968' },
  { key: 'sunset', label: '暖阳橙', swatch: '#FF6A00' }
]

const STORAGE_KEY = 'cm-admin-theme'

function normalize(key: string | null): ThemeKey {
  return THEMES.some((t) => t.key === key) ? (key as ThemeKey) : 'apple'
}

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: normalize(localStorage.getItem(STORAGE_KEY)) as ThemeKey
  }),

  actions: {
    setTheme(key: ThemeKey): void {
      this.current = key
      localStorage.setItem(STORAGE_KEY, key)
      // CSS 变量侧的切换靠根元素 data-theme（styles/themes.scss 的选择器）
      document.documentElement.dataset.theme = key
    }
  },

  getters: {
    meta: (state) => THEMES.find((t) => t.key === state.current) ?? THEMES[0]
  }
})

/** antd token 主色：与 THEMES 的 swatch 同源，供 App.vue 的 ConfigProvider 使用 */
export function themePrimary(key: ThemeKey): string {
  return THEMES.find((t) => t.key === key)?.swatch ?? THEMES[0].swatch
}

/* 模块级兜底：store 尚未实例化（如登录页）时，CSS 变量也要与 localStorage 一致 */
document.documentElement.dataset.theme = normalize(localStorage.getItem(STORAGE_KEY))

if (import.meta.hot) {
  import.meta.hot.accept(acceptHMRUpdate(useThemeStore, import.meta.hot))
}
