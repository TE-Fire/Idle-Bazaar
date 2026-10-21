/**
 * 用户 Web 端入口
 *
 * <p>与 admin 同构：pinia 先于 router 装配；请求层的 UI 副作用在这里注入
 * （`@campus/api` 本身不认识任何 UI 库），两端因此能共用同一份请求层。
 *
 * <p>登录失效（1001）跳登录页并带 redirect：写操作（发布 / 编辑 / 我的商品）
 * 全部要求登录，回跳让用户登录后回到原来的页面。
 */

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'

// 令牌由 @campus/tokens 生成：pnpm tokens
import '@campus/tokens/web.css'
import './styles/index.scss'

import App from './App.vue'
import router from './router'
import { clearToken, configureApi } from '@campus/api'

const app = createApp(App)
app.use(createPinia())

configureApi({
  // design/01 §六：成功用 Toast（顶部居中）。duration 给 2s，与文档一致
  onError: (msg, traceId) =>
    ElMessage.error({ message: traceId ? `${msg}（trace: ${traceId}）` : msg, duration: 2000 }),
  onForbidden: (msg) => ElMessage.warning({ message: msg, duration: 2000 }),
  onUnauthorized: () => {
    clearToken()
    ElMessage.warning({ message: '请先登录', duration: 2000 })
    void router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
  }
})

app.use(router)
app.use(ElementPlus, { locale: zhCn })
app.mount('#app')
