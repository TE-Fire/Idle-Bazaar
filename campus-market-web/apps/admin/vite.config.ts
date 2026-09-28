import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 后端地址：默认 8080（单 jar，/api 与 /admin 同一端口）。
 * 改端口用环境变量：ADMIN_API_TARGET=http://localhost:8081 pnpm dev
 */
const API_TARGET = process.env.ADMIN_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 同源转发，浏览器侧只有 /admin/** 与 /api/**，不存在跨域。
      // ⚠️ vite 的代理键是**前缀匹配**：任何以 /admin 或 /api 开头的**前端页面路由**
      // 都会被当成接口转发出去——实测页面路径 /admins 被代理到后端，返回 1003「资源不存在」。
      // 所以前端页面路由一律不要以 /admin、/api 开头（管理端页面走 /dashboard、/settings/** 这类）。
      '/admin': { target: API_TARGET, changeOrigin: true },
      '/api': { target: API_TARGET, changeOrigin: true }
    }
  }
})
