<script setup lang="ts">
/**
 * 管理后台主框架（design/03 第三章：Sider 208px + Header 48px + 内容区 24px）
 *
 * <p>菜单来自路由表里 meta.hideInMenu !== true 的项，并**按权限点过滤**——
 * 没有 dashboard.view 的角色看不到「数据看板」，与后端 Sa-Token 注解同名。
 * <1024 时提示换桌面浏览器：管理端不为移动端做适配（已写进论文取舍）。
 */

import { computed, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DashboardOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SafetyCertificateOutlined
} from '@ant-design/icons-vue'
import { Modal } from 'ant-design-vue'
import { ROLE_META, type PermCode, type RoleCode } from '@campus/shared'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const collapsed = ref(false)

/** 菜单图标：与路由 name 对应，新增页面时在这里补一个 */
const ICONS: Record<string, Component> = {
  dashboard: DashboardOutlined,
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
const breadcrumb = computed(() => (route.meta.title ? [route.meta.title] : []))

const roleMeta = computed(
  () => ROLE_META[user.roleCode as RoleCode] ?? { label: user.roleCode || '未知角色', color: 'default' }
)

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
    <a-layout-sider v-model:collapsed="collapsed" :width="208" class="layout__sider" theme="light">
      <div class="layout__logo">
        <span v-if="!collapsed">校园二手 · 管理后台</span>
        <span v-else>校</span>
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
        <a-space :size="8">
          <component
            :is="collapsed ? MenuUnfoldOutlined : MenuFoldOutlined"
            class="layout__toggle"
            @click="collapsed = !collapsed"
          />
          <a-breadcrumb>
            <a-breadcrumb-item v-for="b in breadcrumb" :key="b">{{ b }}</a-breadcrumb-item>
          </a-breadcrumb>
        </a-space>

        <a-space :size="12">
          <a-tag :color="roleMeta.color">{{ roleMeta.label }}</a-tag>
          <span class="layout__user">{{ user.realName || user.adminId }}</span>
          <a-button type="text" @click="onLogout">
            <template #icon><LogoutOutlined /></template>
            退出
          </a-button>
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
  border-right: 1px solid var(--cm-color-border);
}

.layout__logo {
  height: var(--cm-layout-header-h);
  display: flex;
  align-items: center;
  padding: 0 var(--cm-space-4);
  font-weight: 600;
  color: var(--cm-color-text-1);
  border-bottom: 1px solid var(--cm-color-border);
  white-space: nowrap;
  overflow: hidden;
}

.layout__header {
  height: var(--cm-layout-header-h);
  line-height: var(--cm-layout-header-h);
  padding: 0 var(--cm-space-4);
  background: var(--cm-color-surface);
  border-bottom: 1px solid var(--cm-color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.layout__toggle {
  cursor: pointer;
  color: var(--cm-color-text-2);
}

.layout__user {
  color: var(--cm-color-text-2);
}

.layout__content {
  padding: var(--cm-layout-content-pad);
  overflow: auto;
}
</style>
