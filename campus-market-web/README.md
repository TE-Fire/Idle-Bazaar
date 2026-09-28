# campus-market-web · 前端工程（pnpm workspace）

对应文档：`campus-market/tech/02-前端技术选型与工程约定.md`（选型与工程约定）、
`tech/04-前端开发工序与编码规范.md`（工序与 CR 清单）、
`campus-market/design/0{1,2,3}-*-UI设计规范.md`（视觉基准）。

## 结构

```
apps/
├─ admin/        管理后台（蓝 #1677FF，Ant Design Vue）design/03   ← 当前已建骨架
└─ web/          用户 Web 端（黄 #FFD100，Element Plus）design/01   ← 待建
packages/
├─ tokens/       设计令牌唯一源头 → css / scss / uni.scss / json
├─ api/          Axios 实例 + Result 解包 + 接口声明（UI 无关）
├─ shared/       权限点常量、角色码、状态机枚举（与后端同名）
└─ eslint-config/ 统一规范（待建）
```

小程序（uni-app，橙 #FF6A00）是**独立工程**，放在 `apps/mini` 但不纳入 workspace
——uni-app 有自己的构建链（HBuilderX / `@dcloudio/vite-plugin-uni`），
塞进 pnpm workspace 会与 Vite 版本打架。它共享的是 `packages/tokens` 的产物与类型，不是源码工程。

## 快速开始

```bash
pnpm install          # 首次
pnpm tokens           # 生成设计令牌产物（dev 脚本已自动串好）
pnpm dev:admin        # 管理后台 http://localhost:5173
```

后端需先起来（`campus-admin-api`，默认 8080）。Vite 已配代理：
`/admin/**` 与 `/api/**` 同源转发到 `http://localhost:8080`，浏览器侧无跨域。
改后端端口：`ADMIN_API_TARGET=http://localhost:8081 pnpm dev:admin`。

## 登录账号（后端演示种子，四个角色同密码）

| 账号 | 角色 | 权限点数 |
|---|---|---|
| `super` | SUPER 超级管理员 | 16（全量） |
| `reviewer` | REVIEWER 审核员 | 6（无 user.ban / settings.edit） |
| `operator` | OPERATOR 运营 | 10（无 review.judge 系列） |
| `readonly` | READONLY 只读 | 4 |

统一密码 `Campus@2026`（后端 `AdminAccountInitializer.DEMO_PASSWORD`）。
dev 下 `campus.captcha.enabled=false`，登录页暂不显示验证码。

## 三条硬约定

1. **业务代码禁止写死色值**，一律 `var(--cm-*)`；令牌改在 `packages/tokens/src/*.json`。
2. **ID 一律 `string`**：后端雪花 ID 已全局转字符串，定义成 `number` 会精度丢失。
3. **权限点三方同名**：`@campus/shared` 的 `PERM_CODES` == 后端 `PermCodes.java` ==
   数据库 `admin_role_perm.perm_code` == 原型 `data-perm`。改一处必须同步四处。

## 当前进度（tech/02 第十章落地顺序）

- [x] ① `packages/tokens`：三份 JSON 源 + 生成脚本（57/59/59 个令牌）
- [ ] ② `apps/admin` 骨架：登录 → 布局 → 权限指令 → 一个 Table 页（**进行中**）
- [ ] ③ A2 看板四张图（ECharts）
- [ ] ④ `apps/web` 骨架：首页 → 详情 → 发布页
- [ ] ⑤ `apps/mini` 复用 api 与 Token，按原型逐页搬

## 已知与文档的偏差

- **Ant Design Vue 目前是全量引入**（`app.use(Antd)`）。文档要求按需引入
  （`unplugin-vue-components`），但 Vite 8 下插件兼容性需先验证；文档给出的降级方案
  就是「手动按需 import」，全量引入是更保守的中间态。等首个页面稳定后收敛。
- **会话持久化手写 localStorage**，未引 `pinia-plugin-persistedstate`：
  只有三个字段要存，插件是 1 个依赖换 6 行代码，等第二个 store 需要持久化时再引。
