# Redis 缓存结构与失效设计

> 技术笔记 · 本项目四处缓存的结构选择依据，以及为什么另外几处"看起来该用 Hash"的地方反而保持字符串。

缓存结构不该按"数据长什么样"选，而该按**怎么读它**选。同一个"分类 → 均价"的映射，如果每页只查一个分类，Hash 的 `HGET` 是最优解；如果每次要全表，`HGETALL` 与整包 `GET` 都是一个来回，选谁就取决于别的东西——内存形态、能否表达空集、写路径是否只改一个字段。本文按这个思路把项目里的缓存逐个过一遍，结论先行，理由在后。

## 四处缓存各自的结构

| 缓存 | 结构 | 载荷 | TTL | 主动失效 |
|---|---|---|---|---|
| `cm:dict:all` / `cm:dict:admin:full` | String（JSON） | 整份字典，含分类 / 校区 / 面交点 / 学院 | 60s | 字典写操作后 DEL 两个 key |
| `cm:item:catAvg` | **Hash** | field = `categoryId`，value = 均价十进制字符串 | 5min | 订单完成 / 裁决完成时 DEL |
| `cm:user:pub:{userId}` | String（JSON） | `UserPubVO`：昵称 / 信用分 / 认证态 | 10min | 改昵称后 DEL |
| `cm:item:state:{itemId}` + `cm:item:holder` | String + Hash | 占位状态字符串 / field = state key、value = 订单号 | 60s | 回滚 Lua 释放 |

## 字典：整包读，字符串最省事

字典的访问模式是「前端进页面要一份完整的」，`/api/dict/all` 一次返回全部启用项，没有任何按单项读的场景。这种整包读用 String 存整份 JSON 最直接：一次 `GET` 拿到全部，反序列化一次成对象，之后在内存里随便查。改成 Hash 反而要 `HGETALL` 再自己拼对象，多一层且没有收益。

真正需要注意的是**两份 key 不能合并**：`cm:dict:all` 只含启用项（学生端发布页只能选这些），`cm:dict:admin:full` 含停用项（管理端要能编辑、商品要能解析历史引用）。合并成一份看似省内存，实际会让「已停用的分类改名后，历史商品在管理端显示 null」——商品可以引用停用项，这是业务事实，不是脏数据。

## 分类均价：映射语义 + 整表替换，改用 Hash

这条缓存原来存的是 `List<Row>` 的 JSON，每行是一个 `CategoryAvgRow` 对象。当时这么写是为了绕一个坑：JSON 的对象 key 只能是字符串，存 `Map<Long, BigDecimal>` 取回来 key 会变成 `String`，调用方拿 `Long` 去 `get` 静默返回 null（notes/10 记过）。但绕过的代价是**结构违背语义**——业务上它就是一个映射，代码里却是一串行对象，读的时候整包反序列化、每行各带一份类型信息。

Hash 天然把这个坑填了：field 本来就是字符串，`categoryId` 转成字符串存在 field 上，读的时候再 `Long.valueOf` 回来，转换点集中在一处，不会静默失败。现在 `HGETALL` 一次取全，值都是纯字符串，没有中间对象。

写入是整表重建（删除 → `HSET` → `EXPIRE`），因为均价从来不会"只变一个分类"——它是全量 `GROUP BY` 的结果，要么整份换掉，要么整份不变。这也解释了为什么不追求用 pipeline 把三条命令压成一个来回：5 分钟才发生一次，不在热路径上，可读性比省两个 RTT 值钱。

空集需要一个哨兵 field `_empty`，因为 Redis 里「key 不存在」与「集合为空」是同一件事。不区分的话，一个还没有任何成交订单的系统会每次请求都白查一次库——这正是 notes/10 §八 那个"缓存写了等于没写"的翻版，只不过这次错在空值语义。

## 卖家公开信息：批量点查，字符串反而更优

这份缓存是本篇最容易误判的地方：它也是"小对象、固定几个字段"，按上一节的逻辑似乎该用 Hash。**不用的理由在读取方式上——它永远是按一批 id 点查**：列表页一页要回填十几个卖家，`multiGet` 一条命令拿全；换成 per-key Hash，`HGETALL` 只能一个个来，批量要靠 pipeline 才勉强追平，反而把简单的事做复杂。

Hash 的另一个卖点是字段级更新：改昵称只 `HSET nickname`，不必整条失效。但本项目的写路径是"公开资料变了就 DEL 整条"，下次读时回库拿最新全量——这比字段级更新更保守，也避免了一种难查的坏状态：只 `HSET` 一个字段而 key 恰好不存在时，会留下一条只有昵称、缺信用分和认证态的残缺记录，读侧分不清"字段为空"和"没写进去"。

顺带记一个实测：字符串方案里每个 key 都带一份 `@class` 与包装过的 Long（`"userId":["java.lang.Long",3007]`），一条约 130 字节，其中类型信息占了近半。这笔开销改用紧凑编码能省掉，但收益是每条几十字节、代价是再引一套序列化，本期不值得——**缓存结构的收益要能抵过它带来的复杂度，否则就是自我感动**。

## 商品占位：字符串存状态，Hash 存持有者

占位是两个 key 配合：`cm:item:state:{itemId}` 存 `ON_SALE` / `LOCKED` / `SOLD`，`cm:item:holder` 是一个 Hash，field 抄上面那个 state key、value 是抢到占位的订单号。

状态用 String 是因为值就是单个枚举字符串，配合 Lua 的 `SET NX EX` 一句话完成"检查并设置"，没什么可设计的。持有者另开 Hash 是因为回滚时要回答一个精确问题——「现在这个占位还是我这单抢的吗」：field 直取比把订单号拼进 value 再解析安全得多，也省掉一次字符串切割。**同一个业务对象拆成两个 key 看似冗余，换来的是两条路径各自一条命令搞定**。

## 失效与降级的统一口径

四处缓存遵循同一套规则，写在 `RedisKeys` 与各 key 类的注释里：**读穿**（miss 才回库并回填）、**异常降级**（`catch` 后 `log.warn`，直查库或返回本次查库结果，不让 Redis 抖动变成 500）、**失效用 DEL 不用 SET**。最后一条的理由是并发下的顺序不可控：重算后 `SET` 可能把旧快照写回，而 `DEL` 只是让下次读回库，没有这个窗口。

TTL 的定位要分清：字典 60 秒是"写侧已主动失效，TTL 只是兜底"；均价与卖家信息则一度把 TTL 当唯一手段（对应写入口还没落地），所以 2026-10-26 把管理端两处也接到卖家缓存之后，昵称一改就要靠 `evict` 覆盖三处展示，不能再等 10 分钟。

## 验证命令

结构选对了、失效接上了，都只能靠 `redis-cli` 确认，不能靠"看代码觉得对"：

```bash
docker exec cm-redis redis-cli -a 'PWD' --no-auth-warning TYPE  cm:item:catAvg      # 应为 hash
docker exec cm-redis redis-cli -a 'PWD' --no-auth-warning HGETALL cm:item:catAvg | head -6
docker exec cm-redis redis-cli -a 'PWD' --no-auth-warning TTL   cm:item:catAvg      # 连打两次必须递减
docker exec cm-redis redis-cli -a 'PWD' --no-auth-warning GET   cm:user:pub:3007    # 看载荷有没有多余字段
```

结构改型时要特别小心旧值：同一 key 从 String 改成 Hash，读侧会撞 `WRONGTYPE`。`CategoryAvgPriceCache` 的处理是读异常一律当 miss，写侧第一次 `delete` 就把旧 String 删掉了，所以上线后第一次访问会打一条 warn 然后自愈——不依赖人工清 key，但那条 warn 要认得出来。
