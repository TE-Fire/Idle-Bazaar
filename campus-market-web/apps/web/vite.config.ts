import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 后端地址：默认 8080（单 jar）。改端口：WEB_API_TARGET=http://localhost:8081 pnpm dev
 *
 * <p>只代理 `/api`：学生端接口还没落地（campus-user-api 目前只有 /api/health），
 * 等商品/订单接口就位后，代理前缀与 admin 保持一致即可，不用改这里。
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
