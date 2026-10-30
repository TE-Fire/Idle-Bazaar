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
    /*
     * 认证三页共用一条路由：/login、/login/register、/login/forgot。
     * 同一记录内切参数组件实例复用，卡片原地过场，不再整页跳转；
     * 旧的 /register、/forgot 由下面的 redirect 兼容。
     */
    path: '/login/:mode(register|forgot)?',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', public: true, hideInMenu: true }
  },
  {
    // A0 注册：注册后为「待分配角色」，由超管分配后才能登录（business/03 F-A0）
    path: '/register',
    redirect: '/login/register'
  },
  {
    // A10 找回密码：邮箱验证码两步（business/03 F-A10）
    path: '/forgot',
    redirect: '/login/forgot'
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
        /*
         * A3 商品管理（business/03 A-B4）：只能下架不能代编辑。
         * 菜单级权限用 item.view——四种角色都有，所以人人可见；
         * 但「下架」「删除」按钮另由 item.offline / item.delete 控制（页面内 v-perm）。
         */
        path: 'items',
        name: 'items',
        component: () => import('@/views/ItemsView.vue'),
        meta: { title: '商品管理', perm: PERM_CODES.ITEM_VIEW }
      },
      {
        // A4 用户管理（A-B5 / F-A5）：认证审核逐条做，不做批量通过
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { title: '用户管理', perm: PERM_CODES.USER_VIEW }
      },
      {
        /*
         * A5 审核队列（F-A2 / F-A3 / F-A4）：管理端每日最大工作量，权重排序 + 连续审核。
         * 菜单门禁用 review.view（17 个点里补的只读点，四种角色都有）——
         * 此前借 review.judge 做门禁，只读角色看不到这一页，与 F-A1「只读角色能看全部数据」冲突。
         * 页面内的「通过 / 驳回 / 下架 / 申诉裁决」按钮仍各挂各的写权限（v-perm）。
         */
        path: 'review',
        name: 'review',
        component: () => import('@/views/ReviewView.vue'),
        meta: { title: '审核队列', perm: PERM_CODES.REVIEW_VIEW }
      },
      {
        /*
         * 社交举报（notes/22 批次 3）：动态与评论不进审核队列——队列按商品举报次数加权，
         * 这两类解析不出商品会污染排序。单开一页，处置动作只有「下架内容」。
         * 权限复用 review.view（看）与 review.judge（裁决），不新增权限点。
         */
        path: 'social-reports',
        name: 'social-reports',
        component: () => import('@/views/SocialReportsView.vue'),
        meta: { title: '社交举报', perm: PERM_CODES.REVIEW_VIEW }
      },
      {
        /*
         * A6 订单管理（A-B7 / F-A6）：纠纷裁决看商品快照。
         * 17 个权限点里没有 order.view，菜单级只能借 order.refund 做门禁——
         * 代价是只读角色看不到订单页。要打通需后端补一个 order.view，记进待办。
         */
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/OrdersView.vue'),
        meta: { title: '订单管理', perm: PERM_CODES.ORDER_VIEW }
      },
      {
        // A7 内容管理（A-B6 / F-A7）：五张字典是全端一致性的源头
        path: 'content',
        name: 'content',
        component: () => import('@/views/ContentView.vue'),
        meta: { title: '内容管理', perm: PERM_CODES.CONTENT_EDIT }
      },
      {
        /*
         * A11 平台公告（A-B13 / F-A12，技术口径 notes/22 S12）：
         * 管理端唯一「主动对外说话」的入口。权限复用 content.edit（与改字典同属运营内容），
         * 不新增权限点。
         */
        path: 'notices',
        name: 'notices',
        component: () => import('@/views/NoticesView.vue'),
        meta: { title: '平台公告', perm: PERM_CODES.CONTENT_EDIT }
      },
      {
        // A8 数据报表：四张校园独有可视化
        path: 'report',
        name: 'report',
        component: () => import('@/views/ReportView.vue'),
        meta: { title: '数据报表', perm: PERM_CODES.REPORT_VIEW }
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
        /*
         * A9 系统设置：只剩 SLA 阈值与敏感词两块，都是**写操作**，
         * 所以门禁用 settings.edit 而不是 log.view——
         * 用 log.view 的话，运营（有 log.view 无 settings.edit）能进本页，
         * 而页里的表单正是它改不了的东西。
         */
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/SettingsView.vue'),
        meta: { title: '系统设置', perm: PERM_CODES.SETTINGS_EDIT }
      },
      {
        /*
         * 操作日志（business/03 F-A11）：2026-10-01 从系统设置的 Tab 拆为独立页。
         * 只读场景（巡盘 / 追责 / 答辩演示审计留痕）频率远高于改配置，
         * 门禁用 log.view：超级管理员与运营可见，恰好区分于 settings.edit。
         */
        path: 'settings/logs',
        name: 'logs',
        component: () => import('@/views/OperationLogView.vue'),
        meta: { title: '操作日志', perm: PERM_CODES.LOG_VIEW }
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
