<script setup lang="ts">
/**
 * 根组件：只挂 router-view。
 *
 * <p>antd 的视觉走 ConfigProvider 的 theme.token，值与 `@campus/tokens` 的 admin 语义层
 * 保持一致（主色随主题、圆角、控件高度）。必须两边同源：CSS 变量管我们自己写的样式，
 * theme.token 管 antd 组件，任一处漏改就会出现「按钮是蓝的、链接是另一种蓝」。
 *
 * <p>换肤（stores/theme.ts）：theme.token 的 colorPrimary/colorInfo 与 Menu 的
 * 选中色从这里随主题切换；CSS 变量侧由 styles/themes.scss 的 data-theme 选择器接管。
 */
import { computed } from 'vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'
// 中文包走默认导出路径：'ant-design-vue/es/locale' 只有类型，没有具名导出
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useThemeStore, themePrimary } from '@/stores/theme'

const themeStore = useThemeStore()

const theme = computed<ThemeConfig>(() => {
  const primary = themePrimary(themeStore.current)
  return {
    token: {
      colorPrimary: primary,
      colorInfo: primary,
      colorSuccess: '#34C759',
      colorWarning: '#FF9F0A',
      colorError: '#FF3B30',
      colorText: '#1D1D1F',
      colorTextSecondary: '#6E6E73',
      colorTextTertiary: '#8E8E93',
      colorBorder: '#D2D2D7',
      colorBorderSecondary: '#D2D2D7',
      colorBgLayout: '#F5F5F7',
      colorBgContainer: '#FFFFFF',
      borderRadius: 10,
      fontSize: 15,
      controlHeight: 40,
      fontFamily:
        "'SF Pro Text', 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif"
    },
    /*
     * 只想改 Card / Menu 两项：antd 4.2.6 的 Table ComponentToken 是空接口，
     * tableHeaderBg 这类「完整名」写进 components 会被类型拦下（不是 AliasToken 成员）。
     * 表格的表头底色改走全局 CSS（styles/index.scss）。
     */
    components: {
      Card: { borderRadiusLG: 14 },
      /* Menu 令牌用 colorXxx 完整名：antd 4.2.6 的 ComponentToken 里没有 itemHeight / itemSelectedBg 这种短名 */
      Menu: {
        radiusItem: 10,
        itemMarginInline: 8,
        colorItemText: '#6E6E73',
        colorItemTextHover: '#1D1D1F',
        colorItemTextSelected: primary,
        colorItemBgSelected: themeStore.current === 'apple' ? '#E8F2FE' : 'var(--cm-color-brand-bg)'
      }
    }
  }
})
</script>

<template>
  <a-config-provider :locale="zhCN" :theme="theme">
    <router-view />
  </a-config-provider>
</template>
