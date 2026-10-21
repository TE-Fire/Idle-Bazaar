import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 后端地址：默认 8080（单 jar）。改端口：WEB_API_TARGET=http://localhost:8081 pnpm dev
 *
 * <p>代理 `/api`：学生端与管理端同 jar，靠 /api 与 /admin 前缀分流。
 * 学生端登录（/api/auth）、商品（/api/item）、字典（/api/dict）均已就绪。
 */
const API_TARGET = process.env.WEB_API_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    // 与 admin（5173）错开，两端可同时起
    port: 5174,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: true }
    }
  }
})
