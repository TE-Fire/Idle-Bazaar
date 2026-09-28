/**
 * 管理后台入口
 *
 * <p>装配顺序有讲究：pinia 必须在 router 之前 use——路由守卫的第一件事就是
 * useUserStore()，若 pinia 未激活会直接报 "getActivePinia() was called"。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd, { message } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'

// 令牌由 @campus/tokens 生成：pnpm tokens
import '@campus/tokens/admin.css'
import './styles/index.scss'

import App from './App.vue'
import router from './router'
import { setupEcharts } from '@/charts/echarts'
import { perm } from '@/directives/perm'
import { configureApi, clearToken } from '@campus/api'
import { useUserStore } from '@/stores/user'

const app = createApp(App)
app.use(createPinia())

// 按需引入的 ECharts 模块必须先注册，视图里才能用 <VChart>
setupEcharts()

/* 请求层的副作用在这里注入：@campus/api 本身不认识任何 UI 库 */
configureApi({
  onError: (msg, traceId) => message.error(traceId ? `${msg}（trace: ${traceId}）` : msg),
  onForbidden: (msg) => message.warning(msg),
  onUnauthorized: () => {
    const user = useUserStore()
    user.clearSession()
    clearToken()
    void router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

app.use(router)
app.use(Antd)
app.directive('perm', perm)
app.mount('#app')
