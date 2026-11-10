# 校园二手交易平台 · campus-market

面向校园的同城二手交易系统，全栈自研，骨架与文档一体。后端 Spring Boot 单体多模块，前端 pnpm workspace 管三端（用户 Web / 管理后台 / 小程序），中间件由 Docker Compose 一次拉起。

仓库里 `campus-market/`（后端 + 文档 + 数据库）与 `campus-market-web/`（前端三端）是**同一个仓库的两个目录**，不是两个独立工程。

## 一、目录

```
BussinessWork/
├─ campus-market/          后端与项目资料
│  ├─ backend/             Maven 多模块 → 单 jar 部署
│  │  ├─ campus-common     统一响应 / 错误码 / 异常 / traceId / 权限点常量
│  │  ├─ campus-core       Entity · Mapper · Service · 鉴权 · 缓存
│  │  ├─ campus-support    可替换能力（对象存储 MinIO，接口隔离）
│  │  ├─ campus-user-api   学生端 Controller（/api/**）
│  │  ├─ campus-admin-api  管理端 Controller（/admin/**）+ 唯一启动类
│  │  └─ docker/           MySQL / Redis / RocketMQ / MinIO 编排
│  ├─ db/                  schema.sql + 增量 patch + 演示数据
│  ├─ tech/                技术选型与工程约定（01 后端 / 02 前端 / 03、04 工序）
│  ├─ business/            三端业务流程（Web / 小程序 / 管理端）
│  ├─ design/              三端 UI 设计规范（视觉基准）
│  └─ notes/               单点技术笔记（订单、缓存、审核、Lua、慢查询……）
├─ campus-market-web/      前端
│  ├─ apps/admin           管理后台（Ant Design Vue，蓝 #1677FF，5173）
│  ├─ apps/web             用户 Web 端（Element Plus，黄 #FFD100，5174）
│  ├─ apps/mini            小程序（uni-app，橙 #FF6A00，5175）★独立工程
│  └─ packages/            tokens / api / shared / mock
├─ dev.ps1                 本地开发环境的一键启停（后端 + 任意前端组合）
└─ ui-styles/              设计探索稿
```

模块规模：后端 324 个 Java 文件、33 个 Controller；数据库 31 张表 + 4 份增量 patch；权限点 18 个、角色 4 类、订单状态 8 种。

## 二、技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.7 · Sa-Token 1.38 · Knife4j |
| 存储 | MySQL 8（ngram 全文索引）· Redis 7（缓存 / 并发占位 Lua / GEO）· MinIO |
| 前端 | Vue 3.5 · Vite 8 · TypeScript · Element Plus / Ant Design Vue |
| 小程序 | uni-app（Vue 3.4 + Vite 5.2.8，受插件 peer 锁定，与另两端不同） |
| 环境 | Docker Compose · pnpm workspace |

选型理由与取舍写在 `campus-market/tech/01`、`tech/02`，这里不重复。

## 三、启动

### 1. 中间件

```bash
cd campus-market/backend/docker
docker compose up -d
docker compose ps          # 全部 healthy 再往下走
```

默认端口 MySQL 3306、Redis 6379、MinIO 9000/9001、RocketMQ 9876。本机已有 MySQL 占着 3306 时，把 compose 改成 `3307:3306`，并把 `application-dev.yml` 的 JDBC 端口一并改掉。RocketMQ 当前未在业务链路使用，内存紧张可以不起。

### 2. 建库

```bash
docker exec -i cm-mysql mysql -uroot -pCampus@2026 --default-character-set=utf8mb4 < campus-market/db/schema.sql
```

`schema.sql` 只对空库生效，已有库的改动要按 `db/patch-*.sql` 手工补。种子里不含管理员密码——演示账号由应用首次启动时自动生成。

### 3. 后端

```bash
cd campus-market/backend
"D:/program files/apache-maven-3.9.11/bin/mvn.cmd" -q clean package -DskipTests
java -jar campus-admin-api/target/campus-admin-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

启动成功看三行：`Tomcat started on port 8080` → `[init] 已创建 4 个演示管理员账号` → `Started CampusAdminApplication`。

- 接口文档 <http://127.0.0.1:8080/doc.html>
- 健康检查 <http://127.0.0.1:8080/api/health>

配置四份：`application.yml`（公共）、`application-demo.yml`（演示，入库）、`application-dev.yml`（本地，已忽略）、`application-prod.yml`（环境变量，已忽略）。默认 profile 是 `dev`；clone 下来没有 dev 文件时，从 demo 复制一份再改。

### 4. 前端

```bash
cd campus-market-web
pnpm install
pnpm dev:admin             # http://localhost:5173
pnpm dev:web               # http://localhost:5174

cd apps/mini && pnpm install --ignore-workspace
pnpm dev:h5                # http://localhost:5175
```

小程序是独立工程，`--ignore-workspace` 不能省：不带它 pnpm 会向上找到根 workspace，装一遍根依赖却不装 mini 自己的。`dev:mp-weixin` 产出 `dist/dev/mp-weixin`，用微信开发者工具打开。

### 5. 一把起

```powershell
.\dev.ps1              # 后端 + 用户 Web 端
.\dev.ps1 -App all     # 后端 + 三端
.\dev.ps1 status       # 端口与 PID
.\dev.ps1 stop
```

PID 落 `.run\`，控制台输出落 `logs\dev\`，后端业务日志仍在 `campus-market\backend\logs\`。

## 四、账号

管理端（后端种子，仅当 `admin_user` 表为空时生成）：

| 账号 | 角色 | 权限点 |
|---|---|---|
| `super` | SUPER 超级管理员 | 全量 |
| `reviewer` | REVIEWER 审核员 | 6（无封禁 / 改配置） |
| `operator` | OPERATOR 运营 | 10（无裁决类） |
| `readonly` | READONLY 只读 | 4 |

统一密码 `Campus@2026`。学生端走手机号 + 短信验证码，`campus.user.sms.echo=true` 时验证码随响应回显，不真发短信。图形验证码由 `campus.captcha.enabled` 控制，答辩前记得打开。

## 五、文档

改代码前先看对应文档，文档没写的行为不要自行发明。

| 目录 | 内容 |
|---|---|
| `campus-market/tech/` | 技术选型、工程约定、开发工序与 CR 清单 |
| `campus-market/business/` | 三端业务流程与状态机 |
| `campus-market/design/` | 三端 UI 规范（颜色、间距、组件），前端视觉以此为准 |
| `campus-market/db/01-数据库设计.md` | 表结构与表间关系 |
| `campus-market/notes/` | 单点实现笔记：订单闭环、缓存结构、审核队列、Lua、慢查询等 |
| `campus-market-web/README.md` | 前端目录约定、令牌、三端联调与已知偏差 |
| `campus-market/backend/README.md` | 后端模块、profile、权限验证示例 |

## 六、硬约定

1. **ID 一律 `string`**：雪花 ID 已全局转字符串，前端定义成 `number` 会精度丢失；手写 JSON 时 Long 要 `String.valueOf`。
2. **业务代码不写死色值**：一律 `var(--cm-*)`，令牌源在 `packages/tokens/src/*.json`。
3. **权限点四方同名**：`packages/shared` 的 `PERM_CODES` == 后端 `PermCodes.java` == `admin_role_perm.perm_code` == 原型 `data-perm`，改一处同步四处。
4. **页面路由不得以 `/admin`、`/api` 开头**：Vite 代理是前缀匹配，`/admins` 会被转发到后端。
5. **金额对外是字符串**，前端声明 `string`、运算显式 `Number()`。
6. **`application-dev.yml` / `application-prod.yml` 不入库**，凭据不进仓库。
