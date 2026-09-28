/**
 * 管理端路由（design/03 第五章页面清单）
 *
 * <p>权限分级（tech/02 §6.2）：
 *   ① 路由守卫拦**菜单级**——meta.perm 缺失权限时进 /403；
 *   ② `v-perm` 指令拦**按钮级**——无权限直接不渲染。
 * 前端只是体验层，接口鉴权在后端 Sa-Token，论文里要写明这一点。
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import type { PermCode } from '@campus/shared'
import { PERM_CODES } from '@campus/shared'
import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 菜单/页面所需权限点；不填表示登录即可访问 */
    perm?: PermCode
    /** 免登录（登录页） */
    public?: boolean
    /** 在侧边菜单里隐藏（403 页等） */
    hideInMenu?: boolean
  }
}

const BasicLayout = () => import('@/layouts/BasicLayout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true, hideInMenu: true }
  },
  {
    // A0 注册：注册后为「待分配角色」，由超管分配后才能登录（business/03 F-A0）
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { title: '注册', public: true, hideInMenu: true }
  },
  {
    // A10 找回密码：邮箱验证码两步（business/03 F-A10）
    path: '/forgot',
    name: 'forgot',
    component: () => import('@/views/ForgotView.vue'),
    meta: { title: '找回密码', public: true, hideInMenu: true }
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '数据看板', perm: PERM_CODES.DASHBOARD_VIEW }
      },
      {
        // A9 管理员账号：给注册产生的待分配账号分配角色（business/03 F-A0 下半段）
        // ⚠️ 路径不能写成 'admins'：vite 代理前缀 /admin 是**前缀匹配**，
        //    /admins 会被当成后端接口转发出去（实测返回 1003「资源不存在」）。
        path: 'settings/admins',
        name: 'admins',
        component: () => import('@/views/AdminUsersView.vue'),
        meta: { title: '管理员账号', perm: PERM_CODES.SETTINGS_EDIT }
      },
      {
        // A9 权限矩阵的骨架版：真实接口 /admin/auth/perm-codes，先验证隔离再扩展成矩阵
        path: 'perm-matrix',
        name: 'perm-matrix',
        component: () => import('@/views/PermMatrixView.vue'),
        meta: { title: '权限矩阵' }
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/ForbiddenView.vue'),
        meta: { title: '无权限', hideInMenu: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const user = useUserStore()
  if (to.meta.public) return true
  if (!user.isLogin) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.perm && !user.hasPerm(to.meta.perm)) return { path: '/403' }
  return true
})

export default router
