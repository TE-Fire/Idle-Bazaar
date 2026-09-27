# 笔记 · 从 `docker run` 到 `docker compose`

这份笔记对应 `campus-market/backend/docker/docker-compose.yml`、`broker.conf` 与 `.env`。起因是我原先只用最基础的 `docker run` 管理容器——每个中间件都自己写一行命令，参数散落在命令行的各个角落，改一个端口要翻历史记录，重装一次要重新敲一遍。换成 Compose 之后,"五件套"变成了一条 `docker compose up -d`。下面把这条路完整走一遍：先看裸命令长什么样、它到底累在哪，再对照到 `docker run` 的每个参数是怎么被搬进 YAML 的，最后把本项目的一个服务逐行拆开讲，并梳理基于这份配置能跑的命令。

---

## 一、裸命令的形态

### 1.1 先把 MySQL 这一条写出来

要启动一个符合项目要求的 MySQL，裸命令是这么长：

```bash
docker run -d \
  --name cm-mysql \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=Campus@2026 \
  -e MYSQL_DATABASE=campus_market \
  -e TZ=Asia/Shanghai \
  -v D:/docker/campus-market/mysql/data:/var/lib/mysql \
  -v D:/BussinessWork/campus-market/db:/docker-entrypoint-initdb.d \
  --health-cmd "mysqladmin ping -h 127.0.0.1 -uroot -pCampus@2026" \
  --health-interval 10s \
  --health-timeout 5s \
  --health-retries 30 \
  --health-start-period 60s \
  mysql:8.0 \
  --ngram_token_size=2 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_0900_ai_ci \
  --default-time-zone=+08:00 \
  --lower_case_table_names=1 \
  --performance-schema=OFF
```

这还只是 **5 个中间件中的一个**。Redis、MinIO、NameServer、Broker 各自再来一遍。

### 1.2 累在哪里

| 痛点 | 具体表现 |
|---|---|
| 不可读 | 20 行长命令，想改一个参数得先读懂整句，改错一个引号就启动失败 |
| 不可共享 | 参数只活在 `history` 里。换台机器、重装系统、交接给别人，全靠重新回忆 |
| 不可追溯 | 上周把 ngram 加上去过，现在这段一模一样的命令没有任何痕迹说明"为什么" |
| 无法整体操作 | 五个容器要停就得停五次；服务之间有先后关系，全靠人肉记顺序 |
| 难以迁移 | 想在另一台机器上用别的端口（比如对方 3307 也被占了），只能把整条命令重写一遍 |

前三条是**记录问题**，第四条是**编排问题**，第五条是**环境问题**。

### 1.3 这件事的本质

Compose 并没有改变 Docker 的任何行为。它做的事只有一件：**把运行时的可变参数从"命令历史"搬到"版本控制的文件"里**。

这个搬迁带来两个质变：

1. **参数可以写注释**。上面那条命令里 `--ngram_token_size=2` 为什么要有？没有地方解释。而在 `docker-compose.yml` 里，它头顶可以写明"缺了它 `schema.sql` 的全文索引建不出来"——后来的人改配置时能看到前因后果，才敢动。
2. **命令退化为动词**。所有"怎么做"的细节进了文件，命令行只剩"做什么"：`up` / `down` / `ps` / `logs`。这是抽象层级的上移。

---

## 二、先把 Docker 基础命令理一遍

不弄清楚这些，看 Compose 的配置就是在背模板。

### 2.1 三个核心对象

| 对象 | 是什么 | 类比 |
|---|---|---|
| **镜像 Image** | 只读的文件模板，含应用及其依赖 | 类 / 安装包 |
| **容器 Container** | 镜像的一次运行实例，可读写 | 对象 / 已安装的进程 |
| **数据 Volume/Mount** | 独立于容器生命周期的存储 | 硬盘上的数据分区 |

关键推论：**容器删了数据还在**（前提是数据放在挂载里）；容器是廉价的，可以随时销毁重建，承载价值的是挂载出来的数据。

### 2.2 镜像相关

```bash
docker pull mysql:8.0            # 从 registry 拉镜像到本地
docker images                    # 看本地有哪些镜像（含大小）
docker image inspect mysql:8.0   # 看镜像元数据：默认 CMD、ENTRYPOINT、环境变量、暴露端口
docker rmi mysql:8.0             # 删镜像（有容器引用时删不掉）
```

`docker image inspect` 值得单说：**镜像约定的一切秘密都在里面**。比如 MySQL 为什么认 `MYSQL_ROOT_PASSWORD` 这个环境变量、默认启动的是什么命令，全是镜像作者写在 Dockerfile 里的。`inspect` 能直接读到 `Entrypoint` 和 `Cmd`。

### 2.3 容器相关

```bash
docker run 镜像 [命令]           # 新建并启动一个容器
docker ps                        # 正在运行的容器
docker ps -a                     # 含已停止的
docker stop  <容器名>            # 优雅停止
docker start <容器名>            # 重新启动已存在的容器
docker restart <容器名>          # stop + start
docker rm -f <容器名>            # 强制删除
docker logs -f <容器名>          # 跟踪日志
docker exec -it <容器名> <命令>  # 进到运行中的容器里执行命令
docker stats                     # 实时看每个容器的 CPU / 内存
```

### 2.4 `docker run` 的参数家族

这是本节的重点，因为 Compose 的每个字段基本都能在这里找到原型。

```bash
docker run [选项] 镜像 [命令] [命令参数]
```

| 选项 | 作用 | 举例 |
|---|---|---|
| `-d` | 后台运行（detached） | 不加就会占住当前终端 |
| `--name` | 给容器起固定名字 | 后续 `stop` / `exec` 用它，不用记容器 ID |
| `-p 宿主:容器` | 端口映射 | `-p 3307:3306`，**左边是宿主，右边是容器**，顺序记反是最常见错误 |
| `-e KEY=VALUE` | 注入环境变量 | `-e MYSQL_ROOT_PASSWORD=xxx` |
| `-v 源:目标` | 挂载数据 | 见 2.6 |
| `--network` | 指定网络 | 不指定用默认 bridge |
| `--rm` | 停止后自动删除 | 适合一次性任务 |
| `--restart` | 重启策略 | `always` 常用于生产 |

### 2.5 末尾 `[命令]` 为什么存在

`docker run mysql:8.0 mysqld --xxx` 里，镜像名后面的部分会**替换镜像的默认 `CMD`**。

MySQL 镜像的真实结构是：

```
ENTRYPOINT = docker-entrypoint.sh     ← 不会被同名参数覆盖
CMD        = mysqld                   ← 会被 docker run 后面的内容覆盖
```

于是 `docker run mysql:8.0 --performance-schema=OFF` 最终执行的是：

```
docker-entrypoint.sh --performance-schema=OFF
```

entrypoint 脚本收到参数后，发现第一个不是它认识的开关，就会把它原样转交给 `mysqld`。**这就是为什么启动 MySQL 能顺带传一堆 mysqld 参数**——它们本质是被 CMD 位置塞进去的。

同理：`docker run redis:7.2.3 redis-server --requirepass xxx` 覆盖了 Redis 的默认 `CMD`。

> 记住这条：**compose 里的 `command:` 字段，就是在干"覆盖 CMD"这件事。**

### 2.6 数据的三种去处

| 方式 | 写法 | 数据在哪 | 适合 |
|---|---|---|---|
| 容器内 | 不挂载 | 容器读写层，**删容器即消失** | 临时数据 |
| bind mount | `-v D:/xxx:/var/lib/mysql` | 宿主机指定路径 | 要能直接看到、备份要用资源管理器 |
| named volume | `-v mydata:/var/lib/mysql` | Docker 管理的区域（Windows 下在 WSL 虚拟磁盘） | 追求 IO 性能、让 Docker 管生命周期 |

本项目从**命名卷改成了 bind mount**，理由是沿用本机 `D:\docker\<中间件>` 的存放习惯，备份直接复制文件夹即可。代价有两个，写在这里以免日后自己忘了：

1. `docker compose down -v` 对 bind mount **无效**，清数据要手动 `rm -rf`；
2. Windows 下容器访问 NTFS 是跨文件系统的，MySQL 初始化明显比在虚拟磁盘里慢，所以健康检查才放宽到 `start_period: 60s`。

### 2.7 清理

```bash
docker system df                 # 看各类资源占了多少空间
docker system prune              # 删停止的容器、无用网络、悬空镜像
docker system prune -a           # 连未被引用的镜像也删（会丢掉已经拉好但没跑的镜像，慎用）
```

---

## 三、`docker compose`：把命令翻译成 YAML

### 3.1 它不是 Docker 的替代品

Compose 是一个**编排层**：读 YAML，把它翻译成一系列 `docker run`，再顺手补上网络创建、依赖顺序、日志聚合这些杂事。底层跑的还是普通的 Docker 容器，`docker ps` 里看到的、Compose 起的容器和手写命令起的容器没有任何区别。

### 3.2 参数对照表（核心）

| `docker run` 参数 | Compose 字段 | 备注 |
|---|---|---|
| `-d` | 由 `up -d` 决定 | yml 本身不写是否后台 |
| `--name xxx` | `container_name: xxx` | |
| `-p 3307:3306` | `ports: - "3307:3306"` | 方向一致：宿主在前 |
| `-e KEY=V` | `environment:` 或 `.env` | 值建议用 `${变量}` 插值 |
| `-v src:dst` | `volumes: - src:dst` | 相对路径按**yml 所在目录**推算 |
| `--health-cmd` 等 | `healthcheck:` 下的 `test` / `interval` / `timeout` / `retries` / `start_period` | |
| 镜像名 | `image:` | |
| 末尾 COMMAND | `command:` | 列表形式最稳，不会被 shell 二次拆词 |
| `--link`（旧写法） | `depends_on:` + 服务名 DNS | 见 3.5 |
| 无对应 | `profiles:` / `restart:` / `healthcheck` | Compose 额外的能力 |

### 3.3 幂等性：`up` 是"逼近期望状态"

这是理解 Compose 行为模式的钥匙。`docker run` 是命令式的："给我建一个"；`docker compose up` 是声明式的："让现实符合这份文件"。

所以重复执行 `docker compose up -d` 是安全的：已经符合配置的容器**会被跳过**，不会重建，数据不动。只有检测到配置变更时，Compose 才会 recreate 那个特定容器。

### 3.4 项目名、默认网络、服务名 DNS

- Compose 会按目录名生成一个项目名（本目录叫 `docker`），所有容器、网络名前都带上这个前缀，避免和别的项目撞名。
- 首次 `up` 会自动创建一个网络并把所有服务接进去。
- 在这个网络里，**服务名就是主机名**。所以 `rmq-broker` 里写的 `-n cm-rmq-namesrv:9876` 能被解析——它用的是 `container_name`，同样会被注册进内嵌 DNS；不过按惯例写**服务名**更规范。
- 反过来，宿主机上的应用访问容器要走端口映射，所以用 `127.0.0.1:9876`，而不是服务名。

---

## 四、逐行拆解：以 MySQL 服务为例

下面这段是 `docker-compose.yml` 里 MySQL 的完整配置，逐行说明它在干什么、为什么这么写。

```yaml
  mysql:
    image: mysql:8.0
```

**服务名** `mysql`：`services:` 下的键名。它同时是网络里的主机名，也是命令行里指代它的方式（`docker compose logs mysql`）。

`image: mysql:8.0`：**钉死主版本**而不用 `latest`。`latest` 的问题是不确定性——它在"拉取那一刻"才解析成一个具体版本，今天拉到的和上个月拉到的可能不同，导致同一个配置文件在两台机器上起出两个版本的数据库。

```yaml
    container_name: cm-mysql
```

固定容器名，方便命令行操作和看日志时识别。不加的话 Compose 会生成 `docker-mysql-1` 这类名字。

```yaml
    command:
      - --ngram_token_size=2
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_0900_ai_ci
      - --default-time-zone=+08:00
      - --lower_case_table_names=1
      - --performance-schema=OFF
```

对应第二部分的 2.5：这些是**覆盖镜像 CMD 的参数**，最终被 `docker-entrypoint.sh` 转交给 `mysqld`。

用列表而不是写成一整行字符串，是为了避免 shell 的二次拆词——字符串形式下，`--x="a b"` 这类带空格的值很容易被切成两段。列表形式每个元素就是一个 argv 项，不做任何解析。

每一项的来历：

| 参数 | 为什么需要 |
|---|---|
| `--ngram_token_size=2` | `schema.sql` 里有 `FULLTEXT(title, description) WITH PARSER ngram`。这个变量必须**在 mysqld 启动时**传入，事后 `SET` 无效，缺了建表会直接报错 |
| `--character-set-server=utf8mb4` | 完整的 4 字节 UTF-8，`utf8`（utf8mb3）存不了 emoji 和部分生僻字 |
| `--collation-server=utf8mb4_0900_ai_ci` | 与 `schema.sql` 建库时写的排序规则保持一致 |
| `--default-time-zone=+08:00` | 容器内默认 UTC；不设的话 `NOW()` 与约定好的 Asia/Shanghai 差 8 小时 |
| `--lower_case_table_names=1` | 表名不区分大小写，Windows/macOS 文件系统本身就是这个行为 |
| `--performance-schema=OFF` | 本机内存紧张，性能视图本地用不上，关掉省约 80~120MB |

```yaml
    environment:
      MYSQL_ROOT_PASSWORD: "Campus@2026"
      MYSQL_DATABASE: campus_market
      TZ: Asia/Shanghai
```

这三个变量**不是 Docker 规定的，是 MySQL 镜像的作者规定的**。官方 entrypoint 在初次初始化时读它们：

- `MYSQL_ROOT_PASSWORD` → 设置 root 密码（不设容器会拒绝启动）；
- `MYSQL_DATABASE` → 自动创建这个库；**
  这一点解释了当初那个诡异现象：即使建表脚本没执行成功，`campus_market` 库还是被建出来了，因为建库是 entrypoint 干的，与脚本无关**；
- `TZ` → 容器时区。

密码加了引号，是因为 YAML 里 `@` 之类符号虽然合法，但带上引号能避免 `Campus@2026` 这类值被误判为别名引用。

```yaml
    ports:
      - "${MYSQL_PORT}:3306"
```

`${MYSQL_PORT}` 的值来自同目录的 `.env`（那里写的是 `3307`）。宿主侧偏移一位是因为**本机已装 MySQL 占着 3306**；容器内仍是标准的 3306，应用看到的才算数的地方是冒号左边。

```yaml
    volumes:
      - ${DATA_ROOT}/mysql/data:/var/lib/mysql
      - ../../db:/docker-entrypoint-initdb.d
```

两条挂载，性质完全不同：

- 第一条是**数据持久化**。`${DATA_ROOT}` 同样来自 `.env`。
- 第二条是**初始化脚本**。MySQL 的 entrypoint 只在**数据目录为空**时扫描这个目录，按文件名顺序处理 `*.sql` / `*.sh` / `*.sql.gz`，其他文件打印一行 `ignoring` 后跳过。所以 `db/` 里的 `01-数据库设计.md` 会被忽略，只有 `schema.sql` 生效。

> **踩过的坑，值得单独记**：这条原来写成 `../db`。yml 在 `backend/docker/` 下，上一级是 `backend/`，而真正的脚本在项目根 `campus-market/db`，所以正确写法是往上两级 `../../db`。错的那一级让 Docker Desktop **不报任何错**——遇到不存在的挂载源，它会悄悄建一个空目录顶替。症状是"库建了、表一张没有、容器还 healthy"，查 `docker compose config` 完全看不出来。
>
> 排错方法只有一条：进容器看挂载点到底是文件还是目录。
> ```bash
> docker compose exec mysql ls -la /docker-entrypoint-initdb.d/
> # 正常：-rwxrwxrwx schema.sql（首位是 -）
> # 异常：drwxrwxrwx schema.sql（首位是 d）
> ```

```yaml
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-pCampus@2026"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 60s
```

四个时间参数的含义：

| 参数 | 含义 |
|---|---|
| `interval` | 两次检查之间的间隔 |
| `timeout` | 单次检查超时即判失败 |
| `retries` | 连续失败几次后标记 `unhealthy` |
| `start_period` | 这段时间内失败**不计入次数**，是给慢启动应用的宽限期 |

`start_period: 60s` 正是为 bind mount 到 NTFS 准备的——首次初始化加跑完 592 行脚本要 1~3 分钟，判死太快会误报 unhealthy。

要注意：**健康检查失败不会杀容器**，它只是给外部（比如编排器、`depends_on` 的 condition）判断用的状态标记。

### 4.1 顺带看另外两种"传配置"的方式

同样的"把配置塞进容器"，Redis 和 RocketMQ 用了不同手段，值得对比：

```yaml
  redis:
    command: redis-server --requirepass "Campus@2026" --appendonly yes
```

Redis 参数少，直接写在 `command:` 里。

```yaml
  rmq-broker:
    command: sh mqbroker -n cm-rmq-namesrv:9876 -c /opt/rocketmq/conf/broker.conf
    volumes:
      - ./broker.conf:/opt/rocketmq/conf/broker.conf
```

RocketMQ 的参数多且有结构，就单独放一个 `broker.conf`，再通过 bind mount 塞进容器，用 `-c` 指过去。

**`broker.conf` 单独放着不起任何作用**——它只能通过挂载进容器、被 `-c` 引用才生效。这解释了为什么这个文件必须和 compose 文件待在一起。

它的好处是：改完配置只需 `docker compose restart rmq-broker`，不用重建容器；而且 conf 文件本身能被 IDE 高亮、能被 Git 追踪。

### 4.2 `depends_on` 的陷阱

```yaml
  rmq-broker:
    depends_on:
      - rmq-namesrv
```

这里**只保证启动顺序**，不保证 namesrv 已经就绪。它会等容器进程起来，然后立刻启动 broker，此时 namesrv 可能还在初始化。

RocketMQ 客户端自带重试，所以这里没出问题；但这个坑很常见——换成"应用依赖数据库"的场景，应用可能在 MySQL 还没准备好时就建连失败。要真正等待就绪，得写成：

```yaml
    depends_on:
      mysql:
        condition: service_healthy     # 依赖 healthcheck 的结果
```

这正是上面给 MySQL 写 `healthcheck` 的另一个用处：**让健康检查可被依赖**，而不只是给人看的。

### 4.3 `profiles`：按需挂载服务

```yaml
  rmq-dashboard:
    profiles:
      - debug
```

挂了 `profiles` 的服务，`docker compose up -d` 会**完全忽略**它——不创建容器，也不拉 healthcheck。需要时用：

```bash
docker compose --profile debug up -d rmq-dashboard
```

这个机制适合"平时用不上、排查时才需要、还很吃资源"的服务。本机的 dashboard 是个近 GB 的 JVM 应用，内存紧张时正好归到这一类。

### 4.4 `.env` 与变量插值

Compose 会自动读取与 yml 同目录的 `.env` 文件，把 `${XXX}` 替换成里面的值。本项目用它收敛了三个易变的东西：

```properties
DATA_ROOT=D:/docker/campus-market   # 数据根目录
MYSQL_PORT=3307                     # 宿主端口
REDIS_PORT=6380
```

价值在于：**换机器时改 `.env` 就够了**。别人机器上 3307 可能也被占，他改自己那份 `.env` 即可，不用动主配置。本地排查时先跑：

```bash
docker compose config          # 打印插值后的完整生效配置
```

这条能一次性看出变量有没有被正确替换、路径推算对不对、哪些服务会被启动。

---

## 五、有了配置文件之后，能跑哪些命令

### 5.1 生命周期

```bash
docker compose up -d              # 按配置创建并启动（幂等）
docker compose up -d mysql        # 只操作指定服务
docker compose stop               # 停止，容器还在，数据都在
docker compose start              # 重新启动已停止的容器（秒起）
docker compose restart mysql      # stop + start（注意：见 5.2）
docker compose down               # 停止并删除容器与网络
docker compose down -v            # 额外删除「命名卷」
```

### 5.2 最容易踩的一个：改了配置要用 `up -d`

**`restart` 不会应用新的 yml。**

因为 `restart` 只是把已存在的容器进程关掉再拉起来，容器内部的挂载、环境变量、端口映射在创建时就已经固定了。改了 `docker-compose.yml` 之后，必须：

```bash
docker compose up -d
```

Compose 会比对配置指纹，发现变化的那个服务自动 recreate，没变的原样跳过。执行时的输出会直接写明 `Recreating cm-mysql ...` 还是 `cm-mysql is up-to-date`。

> 唯一的例外：挂载文件的内容变了（比如改了 `broker.conf`）不需要重建，restart 就行——因为它是运行时读取的。

### 5.3 观察与排错

```bash
docker compose ps                 # 所有服务的状态与端口；看 STATUS 是否 (healthy)
docker compose logs -f mysql      # 跟踪某个服务日志（-f = follow）
docker compose logs --tail 50 mysql
docker compose exec mysql mysql -uroot -pCampus@2026 -e "SHOW DATABASES;"
docker compose top                # 容器内跑的进程
docker stats --no-stream          # 每个容器实际吃了多少 CPU / 内存
```

`exec` 的格式值得留意：`docker compose exec <服务名> <命令>`。第二个参数往后就是在**容器内部**执行的命令，不受宿主环境限制。`docker compose exec mysql mysql ...` 里第二个 `mysql` 是容器里的客户端程序。

### 5.4 校验与预览

```bash
docker compose config              # 插值后完整渲染，最适合排路径/变量错误
docker compose config --services   # 只看会被启动的服务名
docker compose config --quiet      # 只做语法校验，不错就静默通过
```

### 5.5 按需 profile

```bash
docker compose --profile debug up -d rmq-dashboard
docker compose --profile debug stop rmq-dashboard
docker compose config --services                  # 默认不含 dashboard
docker compose --profile debug config --services  # 含 dashboard
```

---

## 六、速查表

**本机这套中间件的常用操作**

| 目的 | 命令 |
|---|---|
| 一键起来 | `docker compose up -d` |
| 只看 MySQL | `docker compose up -d mysql` |
| 看是否就绪 | `docker compose ps`（找 `(healthy)`） |
| 跟 MySQL 初始化 | `docker compose logs -f mysql` |
| 核对挂载是文件还是目录 | `docker compose exec mysql ls -la /docker-entrypoint-initdb.d/` |
| 验证建表是否成功 | `docker compose exec mysql mysql -uroot -pCampus@2026 -e "USE campus_market; SHOW TABLES;"` |
| 改了 yml | `docker compose up -d` |
| 改了 broker.conf | `docker compose restart rmq-broker` |
| 重建 MySQL 数据 | `down` → 删 `D:/docker/campus-market/mysql/data` → `up -d mysql` |
| 省内存临时停两个 | `docker compose stop minio rmq-namesrv rmq-broker` |

**心智模型一句话**：`docker-compose.yml` 描述"应该是什么样"，`docker compose <动词>` 负责把现实推向它——所以重复的 `up` 是安全的，而 `restart` 永远推不动新配置。
