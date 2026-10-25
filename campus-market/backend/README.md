# 校园二手交易平台 · 后端

单体应用 + Maven 多模块，单 jar 部署。技术依据见 `campus-market/tech/01`，开发工序见 `tech/03`。

## 一、模块结构

```
backend/
├─ campus-common      统一响应 / 错误码 / 异常 / traceId / Jackson 四条规则 / 权限点常量
├─ campus-core        Entity · Mapper · Service · Sa-Token 权限加载 · 演示账号初始化
├─ campus-support     可替换能力：对象存储（MinIO，接口隔离）
├─ campus-user-api    学生端 Controller（/api/**）
└─ campus-admin-api   管理端 Controller（/admin/**）+ 唯一启动类
```

两个 api 模块最终打进同一个 jar，靠路径前缀分流（`tech/01` 3.2 已定：单 jar + 前缀）。
将来若学院要求接口分离，给 `campus-user-api` 加个启动类即可，业务代码不动。

## 二、启动中间件

```bash
cd docker
docker compose up -d
docker compose ps        # 全部 healthy 再往下走
```

| 服务 | 地址 | 账号 |
|---|---|---|
| MySQL 8 | 127.0.0.1:3306 | root / Campus@2026 |
| Redis 7 | 127.0.0.1:6379 | 密码 Campus@2026 |
| RocketMQ | 127.0.0.1:9876（控制台 8089） | — |
| MinIO | 127.0.0.1:9000（控制台 9001） | campus / Campus@2026 |

> MySQL 的 `ngram_token_size=2` 是通过容器启动参数传入的，**不需要改本机 my.ini，也不需要 root 密码**。
> 如果本机 MySQL 服务已占用 3306，先停掉它，或把 compose 里的端口改成 `3307:3306`。

## 三、建库

```bash
# 方式一：容器里执行
docker exec -i cm-mysql mysql -uroot -pCampus@2026 < ../db/schema.sql

# 方式二：本机 mysql 客户端
mysql -h127.0.0.1 -uroot -p campus_market < ../db/schema.sql
```

`schema.sql` 内含 24 张表 + 全部种子数据（12 学院 / 8 分类 / 成色 / 6 个面交点 / 4 角色 / 16 权限点 / 减碳系数 / 敏感词）。
**种子里没有管理员密码**——演示账号由应用首次启动时自动生成（见下）。

## 四、环境配置（profile）

配置分四份，位于 `campus-admin-api/src/main/resources/`：

| 文件 | 是否入库 | 用途 | 说明 |
|---|---|---|---|
| `application.yml` | ✅ 入库 | 基础配置 | 与环境无关的公共项，不含任何凭据 |
| `application-demo.yml` | ✅ 入库 | 演示 | localhost + 演示凭据，clone 下来就能跑 |
| `application-dev.yml` | ❌ 已忽略 | 本地开发 | 可随意改端口/日志/连本机中间件 |
| `application-prod.yml` | ❌ 已忽略 | 生产 | 取值全部来自环境变量，无明文凭据 |

默认激活 `demo`，所以**不指定 profile 也能直接启动**。切换方式：

```bash
java -jar campus-admin-api/target/campus-admin-api-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
# 或 IDE 里加 VM options：-Dspring.profiles.active=dev
```

> `dev` / `prod` 不入库（`.gitignore` 已列），首次用 dev 请从 `application-demo.yml` 复制后修改。
> 生产环境注入：`DB_URL` / `DB_USER` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PASSWORD` / `MQ_NAME_SERVER` / `MINIO_*`。

## 五、启动应用

```bash
cd backend
# 注意：你的 M2_HOME 目前指向了 bin 目录，用完整路径调用
"D:/program files/apache-maven-3.9.11/bin/mvn.cmd" -q clean package -DskipTests
java -jar campus-admin-api/target/campus-admin-api-1.0.0-SNAPSHOT.jar
```

首次启动会自动创建 4 个演示管理员（仅当 `admin_user` 表为空时执行，可重复运行）：

| 账号 | 角色 | 密码 |
|---|---|---|
| super | 超级管理员 | Campus@2026 |
| reviewer | 审核员 | Campus@2026 |
| operator | 运营 | Campus@2026 |
| readonly | 只读 | Campus@2026 |

- 接口文档：<http://127.0.0.1:8080/doc.html>
- 健康检查：<http://127.0.0.1:8080/api/health>

## 六、验证权限隔离（毕设演示要点）

```bash
# 1. 用审核员登录，拿到 token
curl -X POST http://127.0.0.1:8080/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"reviewer","password":"Campus@2026"}'

# 2. 分别访问两个演示接口（把 TOKEN 换成登录返回的 tokenValue）
curl -H "satoken-admin: TOKEN" http://127.0.0.1:8080/admin/demo/review   # 通过：持有 review.judge
curl -H "satoken-admin: TOKEN" http://127.0.0.1:8080/admin/demo/ban      # {"code":1002,"msg":"无权限执行该操作"}
```

这一条就是「审核员能审核商品、不能封禁用户」在后端真正生效的证据——
前端按钮置灰只是体验层，这里的 `@SaCheckPermission` 才是安全边界。

## 七、已知待办

基础设施：
- [x] 图形验证码下发接口（2026-09-29）：`GET /admin/auth/captcha`，登录/注册/找回密码共用
- [x] `@OpLog` 注解与 AOP 异步落库（2026-10-06）：管理端 22 个写接口挂上，线程池 `opLogExecutor`
- [x] A9 操作日志查询接口（2026-10-06）：`GET /admin/log/list`（`log.view`），只读、不挂 `@OpLog`

业务模块（后端，均指管理端 /admin/**）：
- [x] 字典 dict：学生端 `/api/dict/all` 走缓存；管理端全量字典走独立缓存 `cm:dict:admin:full`（含停用项）
- [x] 商品 item：管理端 CRUD、审核、强制下架、成色；列表「分页主表 + 批量回填」
- [x] 审核 review：管理端队列（商品审核 / 举报 / 申诉三来源并入）+ 六个处置接口
- [x] 用户 user：管理端账号 / 角色 / 权限
- [x] 订单 order：下单不锁货、付款占位两级校验、超时取消、退款与纠纷（业务说明见 `notes/13`）
- [ ] 消息 message（`msg_message` 表已建、无 Controller；`tech/01` §9.4 已设计 `zone` 分区）
- [ ] 统计 stats（A8 报表，未落地）

学生端（C 端 `/api/**`）已落地：登录（`/api/auth`，手机号 + 短信验证码，
演示期验证码随响应回显，开关 `campus.user.sms.echo`）、资料编辑（`PUT /api/auth/profile`，
昵称 / 头像局部更新，改昵称同步失效 `cm:user:pub` 缓存，业务规则见 `notes/20`）、
商品（`/api/item`，发布含草稿 / 浏览 / 编辑 / 我的商品 / 删除 / 下架）、订单、举报与申诉提交；
字典 `/api/dict/all` 免登录。鉴权走 `StpUserUtil`（token `satoken-user`，7 天）。
管理端队列的单据来源已从演示数据切到真实提交。

中间件（非业务，独立推进）：
- [ ] RocketMQ 生产者/消费者（5 个 topic 已定义在 `tech/01` 4.3；`OP_LOG` 已明确不启用）
- [x] MinIO 接入（2026-10-22：dev 已 `minio.enabled=true`；图片上传两步票据制 + 策略模式，PROXY/DIRECT 由 `campus.upload.mode` 切换；删除商品提交后清对象）
