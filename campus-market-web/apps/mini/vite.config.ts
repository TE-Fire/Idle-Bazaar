/**
 * 小程序端 Vite 配置
 *
 * <p>三个「为什么」：
 * ① **vite 锁 5.2.8**——`@dcloudio/vite-plugin-uni` 的 peer 就是这个精确版本，
 *    而 admin / web 用 Vite 8。这就是小程序必须做独立工程（不进 pnpm workspace）的原因，
 *    否则 pnpm 会在同一棵依赖树里装两个 vite，构建链直接打架。
 * ② **optimizeDeps 显式包含两个 workspace 包**——它们是 TS 源码包（不参与构建），
 *    不预构建的话小程序端编译时可能拿到未转译的 .ts。
 * ③ **h5 代理 /api 到 8080**——学生端与管理端同 jar，靠 /api 与 /admin 前缀分流；
 *    H5 预览走同源代理避免 CORS。微信小程序端不经过 vite devServer，无此问题。
 *
 * <p>**warmup 不是优化项而是正确性项**：uni-app h5 的页面是 `defineAsyncComponent`
 * 懒加载，`__uniConfig.async.timeout` 为 60 秒；本机单页按需编译（SFC + Sass）常常
 * 几十秒，点开一个没编译过的页面若超过 60 秒就渲染「连接服务器超时，点击屏幕重试」，
 * 页面组件根本没 mount，一条 API 请求都不会发。启动时把页面全部预热掉，就不会再出现。
 */

import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/**
 * 源码别名：@campus/shared / @campus/mock 是 TS 源码包，pnpm 在 install 时已把它们
 * 以硬链接固化进 .pnpm 虚拟仓库，之后在 packages 里新增文件（如 demo-users.ts）不会
 * 自动同步，导致小程序端 esbuild 预构建解析失败、整页白屏。这里直接别名到源码，
 * 与 web 端（node_modules/@campus/shared 直链 packages/shared）等效，规避硬链接过期。
 */
const alias = {
  '@campus/shared': fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url)),
  '@campus/mock': fileURLToPath(new URL('../../packages/mock/src/index.ts', import.meta.url))
}

export default defineConfig({
  plugins: [uni()],
  resolve: {
    alias
  },
  server: {
    // uni-app h5 默认也是 5173，会和 admin 撞车，显式挪到 5175
    port: 5175,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    },
    // 启动即编译全部页面：页面组件有 60 秒加载上限，点开才编译很容易超时
    warmup: {
      clientFiles: ['./src/main.ts', './src/App.vue', './src/pages/**/*.vue', './src/components/*.vue']
    }
  },
  optimizeDeps: {
    // 只预打包 shared：mini 工程当前无文件 import @campus/mock（骨架期遗留、已无人使用），
    // 而 mock 的 items.ts 仍从 @campus/shared 导入已移除的 CAMPUSES，强制预打包会编译失败。
    include: ['@campus/shared']
  }
})
