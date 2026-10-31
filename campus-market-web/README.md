# campus-market-web · 前端工程（pnpm workspace）

对应文档：`campus-market/tech/02-前端技术选型与工程约定.md`（选型与工程约定）、
`tech/04-前端开发工序与编码规范.md`（工序与 CR 清单）、
`campus-market/design/0{1,2,3}-*-UI设计规范.md`（视觉基准）。

## 结构

```
apps/
├─ admin/        管理后台（蓝 #1677FF，Ant Design Vue）design/03   ← 已建骨架 + A2 看板
├─ web/          用户 Web 端（黄 #FFD100，Element Plus）design/01   ← 已建骨架（P1–P4）
└─ mini/         小程序（橙 #FF6A00，uni-app）design/02            ← 已建骨架（M1–M6）★独立工程
packages/
├─ tokens/       设计令牌唯一源头 → css / scss / uni.scss / json
├─ api/          Axios 实例 + Result 解包 + 接口声明（UI 无关）
├─ shared/       权限点常量、角色码、商品枚举与模型、格式化工具（与后端同名）
│                 perm-codes / role / item / format
├─ mock/         骨架期共享 mock 数据（Web 与小程序同一批商品，截图才对得上）
└─ eslint-config/ 统一规范（待建）
```

小程序（uni-app，橙 #FF6A00）是**独立工程**：放在 `apps/mini`，但在
`pnpm-workspace.yaml` 里显式排除（`- '!apps/mini'`）。原因是
`@dcloudio/vite-plugin-uni` 的 peer 锁 **vite 5.2.8**，而 admin / web 用 Vite 8，
同一棵依赖树里装两个 vite 会让构建链打架。它共享的是 `packages/*` 的产物与类型，
不是源码工程——依赖用 `file:` 协议引入，`pnpm install --ignore-workspace` 独立安装。

## 快速开始

```bash
pnpm install          # 首次（web + admin）
pnpm tokens           # 生成设计令牌产物（dev 脚本已自动串好）
pnpm dev:admin        # 管理后台 http://localhost:5173
pnpm dev:web          # 用户 Web 端 http://localhost:5174

# 小程序是独立工程，要在 apps/mini 下单独装一次
cd apps/mini && pnpm install --ignore-workspace
pnpm dev:h5           # http://localhost:5175 用浏览器看
pnpm dev:mp-weixin    # 产出 dist/dev/mp-weixin，用微信开发者工具打开
```

两端端口错开（5173 / 5174），可以同时起。

> 小程序的 `--ignore-workspace` 不能省：pnpm 会向上找到根 workspace，
> 不带这个参数它会把根 workspace 装一遍而**不装 mini 自己的依赖**。

后端需先起来（`campus-admin-api`，默认 8080）。Vite 已配代理：
`/admin/**` 与 `/api/**` 同源转发到 `http://localhost:8080`，浏览器侧无跨域。
改后端端口：`ADMIN_API_TARGET=http://localhost:8081 pnpm dev:admin`、
`WEB_API_TARGET=http://localhost:8081 pnpm dev:web`。
用户 Web 端只代理 `/api`（学生端接口全在这一前缀下）。

## 登录账号（后端演示种子，四个角色同密码）

| 账号 | 角色 | 权限点数 |
|---|---|---|
| `super` | SUPER 超级管理员 | 16（全量） |
| `reviewer` | REVIEWER 审核员 | 6（无 user.ban / settings.edit） |
| `operator` | OPERATOR 运营 | 10（无 review.judge 系列） |
| `readonly` | READONLY 只读 | 4 |

统一密码 `Campus@2026`（后端 `AdminAccountInitializer.DEMO_PASSWORD`）。
四个演示账号的邮箱分别是 `3037749727~3037749730@qq.com`（`db/schema.sql` 升级脚本里同步写入）。

**图形验证码**：登录 / 注册 / 找回密码三页共用 `components/CaptchaField.vue`。
后端 `campus.captcha.enabled=false` 时，下发接口返回 `disabled=true`，组件收到后
**自己隐藏**（本地联调不想每次都输四位码）；答辩前把它改成 `true` 即可。
`campus.mail.enabled=false` 同理：验证码不真发邮件，只打进后端日志，联调时从控制台取。

## 用户端联调：测试账号面板

dev 构建下，用户 Web 端与小程序 H5 的角落各有一个「测试账号」浮层
（`components/DevLoginSwitcher.vue`，生产不挂载）。点条目即 `send-code → 取 devCode → login`
自动登录，免手填验证码。

面板分两组：**刚注册 · 本机** 与 **演示账号**。前者由注册页在成功后写入本机存储
（key `campus-test-accounts`，Web 用 localStorage、小程序用 uni.storage，最多 8 条），
条目右侧的 `×` 只删本机记录、不动数据库。所以注册完一个新号，立刻能在面板里切过去。

## 三条硬约定

1. **业务代码禁止写死色值**，一律 `var(--cm-*)`；令牌改在 `packages/tokens/src/*.json`。
2. **ID 一律 `string`**：后端雪花 ID 已全局转字符串，定义成 `number` 会精度丢失。
3. **权限点三方同名**：`@campus/shared` 的 `PERM_CODES` == 后端 `PermCodes.java` ==
   数据库 `admin_role_perm.perm_code` == 原型 `data-perm`。改一处必须同步四处。
4. **页面路由不得以 `/admin`、`/api` 开头**：vite 代理键是前缀匹配，
   `/admins` 这种路径会被当成后端接口转发（实测返回 1003）。管理端页面走
   `/dashboard`、`/settings/**` 这类。

## 当前进度（tech/02 第十章落地顺序）

- [x] ① `packages/tokens`：三份 JSON 源 + 生成脚本（57/59/59 个令牌）
- [x] ② `apps/admin` 骨架：登录 → 布局 → 权限指令 → 权限矩阵 Table 页
- [x] ③ A2 看板：KPI×4 + 趋势折线 + 品类环图 + 学院×品类热力矩阵 + 循环率/减碳卡 + 待处置队列
      **（数据来自 `apps/admin/src/mock/dashboard.ts`，后端统计接口未实现）**
- [x] ④ `apps/web` 骨架：首页·列表（P1）+ 详情（P2）+ 发布（P3）+ 对比（P4）
- [x] ⑤ `apps/mini` 骨架（uni-app 独立工程）：M1 首页瀑布 / M2 详情 / M3 发布 / M4 分类 /
      M5 消息 / M6 我的。`pnpm build:h5` 已通过（产物 `apps/mini/dist/build/h5`）；
      小程序端跑 `pnpm dev:mp-weixin`，用微信开发者工具打开 `dist/dev/mp-weixin`。
- [x] ⑥ A0 注册页 `/register` + A10 找回密码页 `/forgot` + A1 登录页接图形验证码
      （`packages/api` 的 `authApi` 补齐 5 个接口）
- [x] ⑦ A9 管理员账号页 `/settings/admins`：给注册产生的待分配账号分配角色
      （`adminApi.pending/roles/assignRole`；角色下拉由后端剔除 SUPER，菜单按 `settings.edit` 过滤）

### 用户端与小程序的数据来源

同看板：`packages/mock` 是**确定性 mock**（LCG 生成 36 件，基准时间写死 2026-09-28），
Web 端与小程序**共用这一批**，否则同一件商品在两端价格不同，论文截图一对比就露馅。
后端 `/api/item/*` 就绪后删掉它，改由 `packages/api` 供数。
学生端的商品 / 订单 / 会话接口**目前一个都没实现**（`campus-user-api` 只有
`/api/health`），所以用户端还没有登录页与路由守卫——`main.ts` 的 `onUnauthorized`
只弹提示不跳转，等 `/api/auth/*` 就位后再补。

### 看板的数据来源

`apps/admin/src/mock/dashboard.ts` 是**写死的确定性数据**（用 LCG 生成，不用
`Math.random`），这样每次刷新截图一致——论文里贴的图不能每次都变。
后端 `/admin/dashboard/*` 就绪后：删掉该文件，改用 `packages/api` 里由 OpenAPI
生成的 `dashboardApi`，视图层只换数据源，图表配置与组件不动。

四张校园牌做了三张（学院×品类热力矩阵、二手循环率面积图、学期粒度趋势）。
**面交所热力需要校园平面底图**，单独一页做更合适，放到 A8 报表页。

---

## 下一步 · 待排期（前端页面 × 依赖接口）

骨架已全部跑通（三端 dev 均能渲染）。剩下的工作卡在同一个地方：**后端学生端接口
一个都没有**（`campus-user-api` 目前只有 `/api/health`）。所以排期的实质是选一条路，
而不是挑页面做。

### 剩余页面清单

| 端 | 页面 | 路由 / 路径 | 优先 | 依赖的后端接口（现状） |
|---|---|---|---|---|
| Web | P5 搜索结果 | `/search?q=` | P1 | `/api/item/search` ❌ |
| Web | P6 消息（双栏） | `/chat` | P1 | WebSocket + `/api/chat/*` ❌ |
| Web | P7 我的 | `/me` | P1 | `/api/user/*`、`/api/item/mine` ❌ |
| Web | P8 订单详情 | `/order/:id` | P1 | `/api/order/*` ❌ |
| Web | P9 个人主页 | `/user/:id` | P2 | `/api/user/:id` ❌ |
| 小程序 | M7 搜索 | `pages/search/index` | P1 | 同上 ❌ |
| 小程序 | M8 订单详情 | `pages/order/detail` | P1 | `/api/order/*` ❌ |
| 小程序 | M9 个人主页 | `pages/user/index` | P1 | `/api/user/:id` ❌ |
| 小程序 | M10 学号认证 | `pages/verify/index` | P1 | `/api/user/verify` ❌ |
| 管理端 | A3–A9（审核队列、商品、用户、订单、内容、报表、权限矩阵排版） | `/review` 等 | — | `/admin/*` 仅 auth + demo ✅，其余 ❌ |
| 三端 | 登录 / 认证守卫 | — | — | 管理端 ✅，学生端 ❌ |
| 工程 | `packages/eslint-config` | — | — | 无（tech/02 §八 要求，尚未建） |

### 三条路线（待拍板）

| 路线 | 做法 | 好处 | 代价 |
|---|---|---|---|
| **A 后端先行** | 先把 `/api/item`、`/api/order`、`/api/auth` 做出来，前端接真数据 | 前端一次做对，不用写一遍 mock 再改一遍 | 前端短期停工；接口设计要先定稿 |
| **B 前端继续铺** | 按 mock 把 P5–P9 / M7–M10 全部做完，接口就位后统一换数据源 | 论文截图与演示可以尽早成型 | 换数据源时每个页面都要改一遍；接口字段若与 mock 不符要返工 |
| **C 只做论文要的** | 前端只补「核心流程闭环」（发布 → 审核 → 下单 → 面交），其余留 TODO | 最快拿到可演示的主线 | 页面完整度不足，答辩时可能被问 |

**推荐 A 与 B 的混合**：先定接口契约（字段与枚举写进 `packages/shared` 与后端枚举，
这一步不动页面），然后前端按契约铺页面（mock 字段与契约一致，将来只换 URL）。
这样避免"mock 与接口字段对不上"的返工——这也是 `packages/shared` 存在的理由。

### 接口契约落地顺序（若采纳推荐）

1. 商品：`/api/item/{list,detail,publish,edit,offline}` —— 字段以 `GoodsItem` 为准（ID string、价格元）
2. 字典：`/api/dict/all` —— 分类 / 成色 A–D / 校区 / 面交点，三端不得硬编码（tech/02 §七）
3. 认证：`/api/auth/{login,info}` —— 有它才能加路由守卫与登录页
4. 订单：`/api/order/*` —— 状态机见 business/01 §四
5. 会话：WebSocket（tech/02 §6.6，原生 WS + Sa-Token 握手）

## 已知与文档的偏差

- **Ant Design Vue 目前是全量引入**（`app.use(Antd)`）。文档要求按需引入
  （`unplugin-vue-components`），但 Vite 8 下插件兼容性需先验证；文档给出的降级方案
  就是「手动按需 import」，全量引入是更保守的中间态。代价是主 chunk 1.84MB（gzip 575KB），
  等页面稳定后收敛。
- **会话持久化手写 localStorage**，未引 `pinia-plugin-persistedstate`：
  只有三个字段要存，插件是 1 个依赖换 6 行代码，等第二个 store 需要持久化时再引。
- **ECharts 锁 5.6.0 + vue-echarts 7.0.3**：vue-echarts 8 起 peer 变成 `echarts ^6`，
  与文档锁定的 5.x 冲突，暂不升级（echarts 6 已发布，但默认主题与部分配置有变化，
  升之前要先过一遍 design/03 的图表规范）。
- **Element Plus 同样全量引入**（`app.use(ElementPlus)`），主 chunk 996KB（gzip 321KB）——
  比 admin 还大，因为 EP 的 CSS 也全量打进来了。按需引入（`unplugin-vue-components`）
  等页面稳定后再统一收敛，两端一起做。
- **商品图用渐变占位**（`GoodsItem.tints`），不是设计决定，只是后端未就绪时的权宜：
  MinIO 与上传接口就位后 `images` 存真实 URL，`tints` 字段直接删掉。
  发布页的图片只做本地预览（`URL.createObjectURL`），不上传。
- **成色配色跟原型 CSS 而不是 `grade-a~d` 令牌**：原型 `.cond` 是 A/B 绿、C 橙、D 灰，
  而令牌里 `grade-b` 是蓝色。原型是视觉依据，令牌只是数值层，所以以原型为准。
  若后续要统一到令牌，得先改 `design/01` §4.4。
- **`web.semantic.json` 补了 `brand-bg` / `brand-border`**（`#FFF8DB` / `#F2DFA0`）：
  design/01 §4.4 的「已选条件 chip」需要黄底 + 黄边，但令牌源里原本只有 `brand` 与
  `brand-dark`，配色取自 design/01 §2.1 的 `--yellow-100/200`。
- **小程序端 `pages.json` 里的色值只能写死**（tabBar 的 `#FF6A00` 等）：它是 JSON 配置，
  拿不到 CSS 变量/令牌。值与 `mini.semantic.json` 同源，**改主色时这处要手工同步**。
- **小程序端 Vue 锁 `^3.4.21`、vite 锁 `5.2.8`、TS 4.9 + vue-tsc 1.x**：全部跟着
  uni-app 官方模板 `uni-preset-vue#vite-ts`（`3.0.0-5020420260813003`）——
  `@dcloudio/vite-plugin-uni` 的 peer 就是 vite 5.2.8，往上抬会直接构建失败。
  与 Web / 管理端的 Vue 3.5 + Vite 8 不一致，这是 uni-app 的约束，不是疏漏。
- **TabBar 暂无图标**：规范要 81×81 png 两套（选中/未选），手里没图，
  先用纯文字 tabBar（`iconPath` 在小程序里可省略）。有图后补上即可。
