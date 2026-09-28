/**
 * 用户 Web 端路由（design/01 §五 页面清单）
 *
 * <p>骨架期只落地 P0 三页 + P4 对比页（对比是桌面独占卖点，且数据已在 store 里）。
 * 其余 P5–P9 等接口与页面设计确认后再加。
 */

import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 隐藏顶栏的分类条（对比页不需要） */
    hideCat?: boolean
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
      // P3 发布：桌面独占重头戏，拖拽多图 + 实时预览 + Ctrl+Enter
      { path: 'publish', name: 'publish', component: () => import('@/views/PublishView.vue'), meta: { title: '发布闲置' } },
      // P4 对比：桌面独占，2–4 件参数横评
      { path: 'compare', name: 'compare', component: () => import('@/views/CompareView.vue'), meta: { title: '对比', hideCat: true } }
    ]
  },
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
