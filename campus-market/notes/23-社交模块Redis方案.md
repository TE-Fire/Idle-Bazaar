# 23-社交模块 Redis 方案

> 配套文档：`notes/22`（业务条目）、`db/01` §5.8（表结构）、`schema.sql` 十（DDL）。
> 范围：关注/取关、关注与粉丝列表、点赞、动态 Feed 流。不引入 RocketMQ，fan-out 与刷库用 Redis 自身机制兜底，留待后期接消息队列。

## 一、总览：读写分层与双方职责

**Redis 是热路径 + 写缓冲，MySQL 是持久真相源。** 所有写先落 Redis（更新结构 + 进 pending 缓冲），由定时任务批量刷库；读优先走 Redis，未命中回源 DB 并回填。Redis 不可用时不丢数据——写降级直落 DB、读降级查 DB。

| 层 | 角色 | 失败兜底 |
|---|---|---|
| 写请求 | 先改 Redis 结构，再进 pending 缓冲，立即返回 | Redis 不可用时直写 DB（uk 约束防重） |
| 定时任务 | 周期性把 pending 批量 upsert 到 DB | 刷库失败保留 pending，下轮重试 |
| 读请求 | 先读 Redis | miss 时回源 DB 并单飞回填 |

计数一律用 `ZCARD` / `SCARD`，**不另设计数器**，避免双源漂移。

## 二、Redis 数据结构选型总表

| 业务 | Key | 类型 | 存什么 | 选型理由（一句话） |
|---|---|---|---|---|
| 我的关注 | `cm:follow:following:{uid}` | ZSET | member=followeeId, score=关注时间 | 列表要按时间倒序、可翻页，ZSET 的 `ZREVRANGE`+score 天然游标分页，`ZCARD` 直接出关注数 |
| 我的粉丝 | `cm:follow:followers:{uid}` | ZSET | member=followerId, score=关注时间 | 同上；最新粉丝排前，契合"在意粉丝增长" |
| 关注写缓冲 | `cm:follow:pending` | HASH | field=`{follower}:{followee}`, value=`{op}\|{ts}` | field 以关系对为键，连续操作 HSET 覆盖（last-write-wins），刷库前自动合并；`HGETALL` 批量读、`HDEL` 逐条清 |
| 某动态点赞人 | `cm:like:users:{mid}` | SET | member=userId | `SADD/SREM` 天然幂等（已存在返回 0），`SCARD` 出赞数、`SISMEMBER` 出"是否赞" |
| 我的点赞记录 | `cm:like:mine:{uid}` | SET | member=momentId | 信息流渲染时对一个 key 批量 `SISMEMBER` 即可判"我赞过哪些"，避免逐动态查 |
| 点赞写缓冲 | `cm:like:pending` | HASH | field=`{mid}:{uid}`, value=`{op}\|{ts}` | 同关注缓冲 |
| 广场流 | `cm:feed:square` | ZSET | member=momentId, score=发布时间 | 全员可见时间流，`ZREVRANGE` 取最新 |
| 关注流(个性化) | `cm:feed:follow:{uid}` | ZSET | member=momentId, score=发布时间 | 推模式：作者发帖 fan-out 到粉丝该 ZSET，实现"优先看关注者内容" |

## 三、四大功能详述

### 3.1 关注与取关（聚合 + 批量落库，幂等保障）

写路径用一个 **Lua 脚本原子完成**（参照现有 `occupy.lua` 写法，key 走 KEYS）：

- **关注**：`SADD following(我→TA)` + `SADD followers(TA→我)` + `HSET pending(follow)`，返回"是否真新增"（SADD 返回值）。
- **取关**：`SREM` 两个 ZSET + `HSET pending(unfollow)`，返回"是否真移除"。

**幂等性三层保障：**
1. **Redis 层**：`SADD/SREM` 本身幂等。重复关注 `SADD` 返回 0，不再进 pending；重复取关 `SREM` 返回 0，不再进 pending。Lua 让"双写集合 + 记缓冲"原子，杜绝半成功。
2. **缓冲层**：pending 以关系对为 field，连点"关注→取关→关注"最终只留最后一条，刷库按末态落，不产生冗余写。
3. **DB 层**：`user_follow` 的 `uk(follower_id, followee_id, deleted=0)` 是终极防线——重复关注撞唯一键被 upsert 吞掉；B 类软删"复用行"（deleted=0 复活 / 时间戳删除）确保每对关系最多一行。

**批量落库**：定时任务（每 5s，参照 `ViewCounterCache` 节奏）`HGETALL cm:follow:pending` → 批量 upsert（关注：INSERT/复活行；取关：deleted=时间戳）→ 成功后 `HDEL` 已处理 field。失败保留 field 下轮重试。

### 3.2 关注 / 粉丝列表（按用户心理设计）

**心理前提**：用户更在意**粉丝数增长**与**已关注对象**，粉丝列表很少深翻页；看他人时常用"TA 是否已关注我 / 互关"判断。

存储即两个 ZSET（见选型表），score=关注时间，倒序即"最近互动在前"。

**展示结构：**
- **个人主页**：顶部突出 `ZCARD(followers)` 粉丝数、`ZCARD(following)` 关注数（均 O(1)）。
- **关注列表**：`ZREVRANGE` 取最近 N，每条带"已关注/取消"开关（本就在自己 following 集合，本地状态即可）。
- **粉丝列表**：`ZREVRANGE` 取最近 N（最新粉丝在上，呼应"在意增长"）；每条标"我是否关注 TA"——对当前页 ≤20 个 id，批量 `ZSCORE cm:follow:following:{viewerId} {fanId}`（pipeline）判定，互关即双向命中。
- **翻页**：`ZREVRANGE` 带 `LIMIT` 游标；深翻页少但支持。可 `ZREMRANGEBYRANK` 保留最近 1000 防无限膨胀。

### 3.3 点赞（聚合读 + 异步落库）

写路径（Lua/事务内完成）：
- **点赞**：`SADD cm:like:users:{mid} 我` + `SADD cm:like:mine:{uid} mid` + `HSET pending(like)`。
- **取消赞**：`SREM` 两个集合 + `HSET pending(unlike)`。

读路径（聚合，近似 O(1)）：
- 赞数：`SCARD cm:like:users:{mid}`。
- "我赞过吗"：信息流对观众 uid 批量 `SISMEMBER cm:like:mine:{uid} {mid}`。
- 点赞列表：`SMEMBERS/SSCAN cm:like:users:{mid}`（作者查看谁赞了）。

异步落库：`LikeFlushJob` 每 5s `HGETALL cm:like:pending` → 批量 upsert `moment_like`（B 类：赞=INSERT/复活，取消=deleted=时间戳）→ `HDEL`。幂等同关注（SADD 返回 0 不进 pending；uk 兜底）。

### 3.4 动态广播 / Feed 流（全员可见 + 优先关注者）

两条流并存：

- **广场流 `cm:feed:square`（基线）**：所有 `audit_status=0 AND deleted=0` 的动态按发布时间入 ZSET。任何人开"广场"都见全量时间流——对应"内容全员可见"。
- **关注流 `cm:feed:follow:{uid}`（推模式）**：作者 U 发帖时，取 `ZRANGE cm:follow:followers:{U}` 得粉丝，对每个粉丝 F `ZADD cm:feed:follow:{F} {发布时间} {momentId}`。粉丝开"关注" tab 直接读自己 ZSET，优先看见关注者内容——即"优先推送给关注者"的推荐机制。

**fan-out 时机**：校园场景粉丝量小，发帖时同步 fan-out（一次 pipeline `ZADD`）即可，发布接口不阻塞。若后续粉丝量变大，把 fan-out 投入 `cm:feed:fanout:pending`（LIST/Stream），worker 异步推，发布接口只入队（不引 RocketMQ，用 Redis 自身队列兜底）。

**读端**："关注" tab 读 `cm:feed:follow:{uid}`，"广场" tab 读 `cm:feed:square`，均 `ZREVRANGE` 按 score 时间序；如需在关注流内加权（如互关加权）改 score 公式即可，本期纯时间序。

## 四、幂等性保障小结

| 操作 | Redis 幂等返回 | 缓冲合并 | DB 终极防线 |
|---|---|---|---|
| 关注 | `SADD` 返回 0 → 不进 pending | HSET 覆盖 | `uk(follower,followee,deleted=0)` |
| 取关 | `SREM` 返回 0 → 不进 pending | HSET 覆盖 | B 类复用行，deleted 置时间戳 |
| 点赞 | `SADD` 返回 0 → 不进 pending | HSET 覆盖 | `uk(moment,user,deleted=0)` |
| 取消赞 | `SREM` 返回 0 → 不进 pending | HSET 覆盖 | B 类复用行 |

## 五、数据同步与保底兜底

1. **常规同步**：写→Redis（结构 + pending）；定时任务批量刷 DB；读→Redis，miss→DB 并回填。
2. **Redis 写失败/不可用**：降级为**直写 DB**（走 uk 约束防重），返回成功；Redis 恢复后下次读 miss 自动从 DB 回填。社交操作非资金，可接受短暂 Redis 缺失。
3. **Redis 读 miss / 冷启动**：回源 DB（关注关系 / 点赞 / 动态），用**单飞(single-flight)或分布式锁**防缓存击穿——首个请求加载、其余等待，加载完写回 Redis。
4. **pending 刷库失败**：保留 field，下轮重试；绝不静默丢弃。Redis 重启靠 **AOF(everysec)** 保住未刷盘的 pending 与结构，DB 是最终真相。
5. **Feed 流降级**：`cm:feed:follow:{uid}` 缺失时退化为"拉"——查该用户关注列表，`WHERE user_id IN (...) AND deleted=0 AND audit_status=0 ORDER BY create_time DESC` 现算；`cm:feed:square` 缺失则从 `user_moment` 全量重建 `ZADD`。

## 六、与现有 Redis 规范的衔接

- **Key 前缀**：统一 `cm:`（与 `cm:item:view`、`cm:chat:unread`、`cm:user:pub` 一致）。
- **客户端**：沿用 `StringRedisTemplate`（社交结构多为 id 字符串，无需 `@class` 的 JSON 序列化），避开 `GenericJackson2JsonRedisSerializer` 的 `@class` 坑。
- **原子脚本**：关注/取关、点赞/取消的"集合双写 + 记缓冲"放 `campus-core/src/main/resources/lua/`（`follow.lua`、`like.lua`），经 `LuaScripts.load` 读入，key 一律 KEYS 传入、不写字面量（Cluster 安全）。
- **定时刷库**：新增 `FollowFlushJob` / `LikeFlushJob`，参照 `ViewCounterCache` 的 `@Scheduled` 每 5s 取走 pending 批量 upsert，失败保留重试。
- **不引 RocketMQ**：fan-out 同步或 Redis LIST 兜底，留待后期接入消息队列做解耦与削峰。
