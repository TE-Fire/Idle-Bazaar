/**
 * 用户 Web 端路由（design/01 §五 页面清单）
 *
 * <p>骨架期只落地 P0 三页 + P4 对比页（对比是桌面独占卖点，且数据已在 store 里）。
 * 发布与我的商品已接真实接口（学生端登录就绪后补上路由守卫：未登录访问写页跳登录）。
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
  }
}

const MainLayout = () => import('@/layouts/MainLayout.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: MainLayout,
    children: [
      {
        // P1 首页 / 列表：分类走 query，`/list?cat=数码电子` 与首页同一组件
        path: '',
        name: 'home',
        component: () => import('@/views/HomeView.vue'),
        meta: { title: '闲置市场' }
      },
      { path: 'list', name: 'list', component: () => import('@/views/HomeView.vue'), meta: { title: '商品列表' } },
      { path: 'item/:id', name: 'item', component: () => import('@/views/ItemDetailView.vue'), meta: { title: '商品详情' } },
      // P3 发布：桌面独占重头戏，拖拽多图 + 实时预览 + Ctrl+Enter；?edit={id} 编辑模式
      { path: 'publish', name: 'publish', component: () => import('@/views/PublishView.vue'), meta: { title: '发布闲置' } },
      // 我的商品：卖家视角全状态（含草稿），操作按状态给出
      { path: 'my-items', name: 'my-items', component: () => import('@/views/MyItemsView.vue'), meta: { title: '我的商品' } },
      // 我的订单：买 / 卖两个视角共用一页，状态筛服务端（notes/13 第七节）
      { path: 'orders', name: 'orders', component: () => import('@/views/OrdersView.vue'), meta: { title: '我的订单' } },
      // 关注 / 粉丝：/follow 看自己，/user/:id/follow 看他人；?tab=fans 切到粉丝
      { path: 'follow', name: 'follow', component: () => import('@/views/FollowListView.vue'), meta: { title: '关注与粉丝' } },
      {
        path: 'user/:id/follow',
        name: 'user-follow',
        component: () => import('@/views/FollowListView.vue'),
        meta: { title: '关注与粉丝' }
      },
      // 个人主页：资料卡 + 关注按钮 + TA 的在售闲置（动态 Tab 是 S4 占位）
      {
        path: 'user/:id',
        name: 'user-profile',
        component: () => import('@/views/UserProfileView.vue'),
        meta: { title: '个人主页' }
      },
      // 订单详情：状态动作都在这页（付款 / 退款 / 核销 / 收货 / 纠纷），?role=seller 切卖家视角
      {
        path: 'order/:id',
        name: 'order-detail',
        component: () => import('@/views/OrderDetailView.vue'),
        meta: { title: '订单详情' }
      },
      // P4 对比：桌面独占，2–4 件参数横评
      { path: 'compare', name: 'compare', component: () => import('@/views/CompareView.vue'), meta: { title: '对比' } },
      // P6 消息：询单会话（同步留言，无实时推送）；?itemId=&peerId= 从详情页带入选中会话
      { path: 'chat', name: 'chat', component: () => import('@/views/ChatView.vue'), meta: { title: '消息' } }
    ]
  },
  // 登录：独立全屏页（闲鱼口径），不走 MainLayout，因此没有顶栏与悬浮工具栏
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 换页回到顶部：列表→详情→返回时不该停在中间
  scrollBehavior: () => ({ top: 0 })
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 校园二手交易平台` : '校园二手交易平台'
})

export default router
