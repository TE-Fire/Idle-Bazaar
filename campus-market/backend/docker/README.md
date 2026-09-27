# 本地中间件操作手册（campus-market）

面向本机 Docker Desktop + Windows。所有命令都在本目录（`backend/docker/`）下执行，Compose 会读取同目录的 `.env`。

## 一、前置检查（只需做一次）

0. **先建 `.env`**。`.env` 已被 gitignore（本机端口与数据目录人人不同），仓库里只有模板：
   ```bash
   cp .env.example .env
   ```
   compose 里的 `${DATA_ROOT}` / `${MYSQL_PORT}` / `${REDIS_PORT}` 都取自它，缺了会报"变量未设置"、挂载路径错乱。
1. **Docker Desktop 允许挂载 D 盘**：Settings → Resources → File sharing，勾选 `D:\`。改完要 Apply & Restart。
2. **端口没被占**：本机已装 MySQL（3306）和 Redis（6379），所以宿主端口刻意偏移到 **3307 / 6380**。
3. **本机剩下的服务不动**：3306/6379 上原有的 MySQL、Redis 照常运行，互不影响。

## 二、数据落在 D 盘的哪里

统一根目录由 `.env` 里的 `DATA_ROOT` 决定。模板里默认是相对路径 `./data`（落在 yml 同级的 `data/`），**本机这份改成了 `D:/docker/campus-market`**，与既有的 `D:\docker\<中间件>` 习惯一致。

| 服务            | 宿主机路径（D 盘）                       | 容器内路径             | 存的是什么     |
| --------------- | ---------------------------------------- | ---------------------- | -------------- |
| mysql           | `D:\docker\campus-market\mysql\data`     | `/var/lib/mysql`       | 库、表数据     |
| redis           | `D:\docker\campus-market\redis\data`     | `/data`                | AOF 持久化文件 |
| rocketmq-broker | `D:\docker\campus-market\rocketmq\store` | `/home/rocketmq/store` | 消息 CommitLog |
| minio           | `D:\docker\campus-market\minio\data`     | `/data`                | 图片对象       |

**注意两点：**

- 用的是 bind mount，**不是命名卷**。所以 `docker compose down -v` 删不掉数据，只会删容器；要彻底重来必须手动删掉上表里的目录。
- 反过来也有好处：备份就是复制文件夹。

## 三、镜像清单与拉取

| 服务                     | image                                      | 本机已有 | 需要拉取                         |
| ------------------------ | ------------------------------------------ | -------- | -------------------------------- |
| mysql                    | `mysql:8.0`                                | ❌       | ✅ **约 590MB**                  |
| redis                    | `redis:7.2.3`                              | ✅       | —                                |
| rmq-namesrv / rmq-broker | `apache/rocketmq:5.3.1`                    | ✅       | —                                |
| minio                    | `minio/minio:RELEASE.2023-09-30T07-02-29Z` | ✅       | —                                |
| rmq-dashboard            | `apacherocketmq/rocketmq-dashboard:2.0.0`  | ✅       | 挂了 `profiles: debug`，默认不起 |

只缺一个 MySQL，单独拉它即可：

```bash
docker pull mysql:8.0
```

> 若 Docker Hub 拉不动或极慢，在 Docker Desktop → Docker Engine 配置里加 `"registry-mirrors"`，或考虑daemon 代理。

## 四、启动

```bash
docker compose up -d
```

按内存占用从大到小，默认只起这 5 个：`mysql`、`redis`、`rmq-namesrv`、`rmq-broker`、`minio`。

可选的两件事：

```bash
# 需要看 topic / 排查延迟消息时，临时起控制台（用完 stop 掉，内存立刻还回来）
docker compose --profile debug up -d rmq-dashboard
docker compose --profile debug stop rmq-dashboard
```

**应用侧的连接地址**（`application-dev.yml` 已改好）：MySQL `3307`、Redis `6380`、NameServer `9876`、MinIO `9000`（控制台 9001）。

## 五、建表脚本如何自动加载

项目根的 `db/` 目录整个挂进了 MySQL 容器的初始化目录：

```
campus-market/db/  →  /docker-entrypoint-initdb.d/
```

MySQL 官方镜像的 entrypoint 逻辑：**当且仅当数据目录为空（首次启动）时**，按文件名顺序扫描该目录，只处理 `*.sql` / `*.sh` / `*.sql.gz`，其余文件（比如 `01-数据库设计.md`）打印一行 `ignoring` 后跳过，无害。副作用是往 `db/` 新增 `.sql` 也会被自动执行。

因为挂的是源目录而非副本，**改完 `db/schema.sql` 不需要任何同步动作**。

### 路径必须算准，写错了不会有明显报错

本文件在 `backend/docker/` 下，要上**两级**才是项目根，所以是 `../../db`。

> **踩过的坑**：曾写成 `../db`（实际指向 `backend/db`，不存在）。Docker Desktop 遇到不存在的挂载源**不报错**，而是悄悄创建一个空目录顶替。症状是「库建了、表一张没有、容器还 healthy」——查 `docker compose config` 看不出问题，只有进容器 `ls` 才会发现挂载点变成了目录：
>
> ```bash
> docker compose exec mysql ls -la /docker-entrypoint-initdb.d/
> # 正常：-rwxrwxrwx schema.sql（首位是 -）
> # 异常：drwxrwxrwx schema.sql（首位是 d）
> ```
>
> 换机器或调整路径后，建议先跑一次上面的核对。

**要重新初始化时**（改了表结构、或像上面那样脚本没跑成功要重来）：

```bash
docker compose down
rm -rf /d/docker/campus-market/mysql/data   # 关键：目录非空就不会再执行脚本
docker compose up -d mysql
```

## 六、如何验证运行状态

```bash
docker compose ps                  # 看 STATUS，(healthy) 才算就绪
docker stats --no-stream           # 看每个容器实际吃了多少内存
docker compose logs -f mysql       # 首次启动跟踪初始化进度
```

首次 MySQL 在 NTFS 上初始化通常要 1~3 分钟，期间 STATUS 显示 `(health: starting)` 属正常，健康检查失败不会杀容器。

各服务连通性验证：

```bash
# MySQL：应查到 schema.sql 建出来的表
docker compose exec mysql mysql -uroot -pCampus@2026 -e "USE campus_market; SHOW TABLES;"

# Redis
docker compose exec redis redis-cli -a Campus@2026 ping        # 返回 PONG

# RocketMQ：看 topic 是否自动建出
docker compose exec rmq-broker sh mqadmin topicList -n cm-rmq-namesrv:9876

# MinIO：浏览器打开 http://127.0.0.1:9001 ，用 campus / Campus@2026 登录
```

Web 入口：MinIO 控制台 `http://127.0.0.1:9001`，RocketMQ 控制台 `http://127.0.0.1:8089`（需先用 debug profile 启起来）。

## 七、常用操作

```bash
docker compose stop          # 停服，数据都在
docker compose start         # 再拉起来，秒起
docker compose restart rmq-broker    # 改了 broker.conf 后重启这一个即可
docker compose down          # 删容器，D 盘数据保留
```

## 八、已知事项

- **`rmq-dashboard` 版本**：用的是 `apacherocketmq/rocketmq-dashboard:2.0.0`（2023 年）。若打开 http://127.0.0.1:8089 后刷不出 topic 列表，是与 5.3.1 namesrv 的兼容问题，把 image 换成 `apache/rocketmq-dashboard:latest` 即可（需再拉约 1.1GB）。
- **排序规则已统一**：此前 server 是 `utf8mb4_general_ci`、而 `schema.sql` 建库用 `utf8mb4_0900_ai_ci`，两者不一致。已把 server 改成 `utf8mb4_0900_ai_ci`（这也是 MySQL 8 的默认值），server 与库现在一致。
  > 说明：原状态在单库、无存储过程的前提下不会报错，所以一度留着没动；趁数据目录尚未初始化时一并改掉，改动成本为零。
