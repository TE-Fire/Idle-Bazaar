<script setup lang="ts">
/**
 * 管理后台主框架（design/03 第三章：Sider 240px + Header 56px + 内容区 32px）
 *
 * <p>菜单来自路由表里 meta.hideInMenu !== true 的项，并**按权限点过滤**——
 * 没有 dashboard.view 的角色看不到「数据看板」，与后端 Sa-Token 注解同名。
 * <1024 时提示换桌面浏览器：管理端不为移动端做适配（已写进论文取舍）。
 */

import { computed, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BgColorsOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  FlagOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  TransactionOutlined,
  UserOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { ROLE_META, type PermCode, type RoleCode } from '@campus/shared'
import { useUserStore } from '@/stores/user'
import { useThemeStore, THEMES } from '@/stores/theme'

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const themeStore = useThemeStore()

const collapsed = ref(false)

/** 与 --cm-layout-sider-w 同值：antd 的 Sider 宽度只吃数字 prop，读不到 CSS 变量 */
const SIDER_W = 240

/** 菜单图标：与路由 name 对应，新增页面时在这里补一个 */
const ICONS: Record<string, Component> = {
  dashboard: DashboardOutlined,
  items: AppstoreOutlined,
  users: UserOutlined,
  review: AuditOutlined,
  'social-reports': FlagOutlined,
  orders: TransactionOutlined,
  content: DatabaseOutlined,
  report: BarChartOutlined,
  settings: SettingOutlined,
  logs: FileTextOutlined,
  admins: TeamOutlined,
  'perm-matrix': SafetyCertificateOutlined
}

interface MenuItem {
  key: string
  label: string
  icon?: Component
  perm?: PermCode
}

/** 从路由表派生菜单，避免路由与菜单两处维护 */
const menuItems = computed<MenuItem[]>(() =>
  router.options.routes
    .flatMap((r) => r.children ?? [])
    .filter((r) => !r.meta?.hideInMenu)
    .map((r) => ({
      key: String(r.name),
      label: r.meta?.title ?? String(r.name),
      icon: ICONS[String(r.name)],
      perm: r.meta?.perm
    }))
    .filter((item) => !item.perm || user.hasPerm(item.perm))
)

const selectedKeys = computed(() => [String(route.name)])

const roleMeta = computed(
  () => ROLE_META[user.roleCode as RoleCode] ?? { label: user.roleCode || '未知角色', color: 'default' }
)

/** 头像里放姓名首字；没有姓名就用 ID 兜底 */
const avatarChar = computed(() => (user.realName || String(user.adminId || '管')).slice(0, 1))

const onMenuClick = ({ key }: { key: string | number }): void => {
  void router.push({ name: String(key) })
}

const onLogout = (): void => {
  Modal.confirm({
    title: '确认退出登录？',
    content: '退出后需要重新输入账号密码。',
    okText: '退出',
    cancelText: '取消',
    onOk: async () => {
      await user.logout()
      void router.push({ path: '/login' })
    }
  })
}
</script>

<template>
  <a-layout class="layout">
    <a-layout-sider
      v-model:collapsed="collapsed"
      :width="SIDER_W"
      class="layout__sider"
      theme="light"
    >
      <div class="layout__logo">
        <span class="layout__logo-mark">校</span>
        <span v-if="!collapsed" class="layout__logo-name">校园二手 · 管理后台</span>
      </div>
      <!-- 不用 :items 数据源写法：那样图标要传 VNode，类型上要给 VueNode 让路；
           直接写 <a-menu-item> 更贴合 Vue 模板，也让权限过滤的结果一目了然 -->
      <a-menu :selected-keys="selectedKeys" mode="inline" theme="light" @click="onMenuClick">
        <a-menu-item v-for="item in menuItems" :key="item.key">
          <template #icon>
            <component :is="item.icon" />
          </template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="layout__header">
        <a-space :size="12">
          <component
            :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined"
            class="layout__toggle"
            @click="collapsed = !collapsed"
          />
          <!-- 菜单只有一层，面包屑没有信息量，直接展示当前页大标题 -->
          <span class="layout__page-title">{{ route.meta.title }}</span>
        </a-space>

        <a-space :size="12">
          <!-- 一键换肤：色板 Popover。图标用 BgColorsOutlined（调色盘语义），
               当前主题的色板加勾选态；选中即切 antd token 与 CSS 变量两侧（stores/theme.ts） -->
          <a-popover placement="bottomRight" trigger="click" overlay-class-name="theme-pop">
            <template #content>
              <div class="theme-panel">
                <p class="theme-panel__t">界面主题</p>
                <button
                  v-for="t in THEMES"
                  :key="t.key"
                  type="button"
                  class="theme-swatch"
                  :class="{ 'theme-swatch--on': themeStore.current === t.key }"
                  @click="themeStore.setTheme(t.key)"
                >
                  <span class="theme-swatch__dot" :style="{ background: t.swatch }" />
                  <span class="theme-swatch__label">{{ t.label }}</span>
                </button>
              </div>
            </template>
            <a-tooltip title="一键换肤">
              <button type="button" class="layout__iconbtn">
                <BgColorsOutlined />
              </button>
            </a-tooltip>
          </a-popover>
          <!-- 角色不再单独展示标签：演示账号的 realName 就是角色名，两个「超级管理员」纯冗余。
               角色信息收进头像的 tooltip，实名非角色名的账号也能看到 -->
          <span class="layout__user">{{ user.realName || user.adminId }}</span>
          <a-tooltip :title="`${roleMeta.label} · 点击退出登录`">
            <button type="button" class="layout__avatar" @click="onLogout">
              {{ avatarChar }}
            </button>
          </a-tooltip>
        </a-space>
      </a-layout-header>

      <a-layout-content class="layout__content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout__sider {
  background: var(--cm-color-surface);
  border-right: 1px solid var(--cm-color-border);
}

.layout__logo {
  height: var(--cm-layout-header-h);
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  padding: 0 var(--cm-space-5);
  white-space: nowrap;
  overflow: hidden;
}

/* 与登录页的 .auth__logo 同一形状：渐变圆角方块 + 单字 */
.layout__logo-mark {
  flex: none;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--cm-radius-md);
  background: linear-gradient(135deg, var(--cm-banner-blue-from), var(--cm-banner-blue-to));
  color: #fff;
  font-size: var(--cm-font-size-base);
  font-weight: 600;
}

.layout__logo-name {
  font-size: var(--cm-font-size-base);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--cm-color-text-1);
}

.layout__sider :deep(.ant-menu) {
  padding: var(--cm-space-2) var(--cm-space-3);
  border-inline-end: none;
  background: transparent;
}

/* Apple 官网那根半透明导航条：滚内容时底下的东西会透上来 */
.layout__header {
  position: sticky;
  top: 0;
  z-index: 10;
  height: var(--cm-layout-header-h);
  line-height: var(--cm-layout-header-h);
  padding: 0 var(--cm-space-5);
  background: var(--cm-layout-nav-bg);
  backdrop-filter: var(--cm-layout-nav-blur);
  -webkit-backdrop-filter: var(--cm-layout-nav-blur);
  border-bottom: 1px solid var(--cm-color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.layout__page-title {
  font-size: var(--cm-font-size-md);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--cm-color-text-1);
}

.layout__toggle {
  cursor: pointer;
  color: var(--cm-color-text-2);
}

.layout__user {
  color: var(--cm-color-text-2);
}

/* 顶栏图标按钮（换肤入口）：与头像同一高度手感，无边框 */
.layout__iconbtn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--cm-radius-md);
  background: transparent;
  color: var(--cm-color-text-2);
  cursor: pointer;
  transition:
    background 160ms ease-out,
    color 160ms ease-out;
}

.layout__iconbtn:hover {
  background: var(--cm-color-surface-muted);
  color: var(--cm-color-text-1);
}

/* 换肤面板：色板列表。Popover 内容在 teleport 里，scoped 选不中，用全局类名 */
.theme-panel {
  width: 168px;
}

.theme-panel__t {
  margin: 0 0 var(--cm-space-2);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.theme-swatch {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  width: 100%;
  padding: var(--cm-space-2);
  border: none;
  border-radius: var(--cm-radius-md);
  background: transparent;
  cursor: pointer;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
  transition: background 160ms ease-out;
}

.theme-swatch:hover {
  background: var(--cm-color-surface-muted);
}

.theme-swatch__dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  flex: none;
  box-shadow: 0 0 0 1px rgb(0 0 0 / 6%) inset;
}

.theme-swatch--on {
  background: var(--cm-color-brand-bg);
  color: var(--cm-color-brand-text);
  font-weight: 600;
}

/* 头像即退出入口：品牌浅底圆 + 首字，hover 加柔光圈提示可点 */
.layout__avatar {
  width: 34px;
  height: 34px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--cm-color-brand-bg);
  color: var(--cm-color-brand);
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: box-shadow var(--cm-motion-duration-fast) var(--cm-motion-ease);
}

.layout__avatar:hover {
  box-shadow: 0 0 0 3px var(--cm-color-brand-bg);
}

.layout__content {
  padding: var(--cm-layout-content-pad);
  overflow: auto;
}
</style>
