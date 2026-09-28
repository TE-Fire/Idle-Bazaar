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
      // 同源转发，浏览器侧只有 /admin/** 与 /api/**，不存在跨域
      '/admin': { target: API_TARGET, changeOrigin: true },
      '/api': { target: API_TARGET, changeOrigin: true }
    }
  }
})
