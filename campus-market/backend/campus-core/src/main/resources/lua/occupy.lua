-- 商品占位抢占：OrderService.pay 的 Redis 前置拦截（口径见 tech/01 §9.1）
-- KEYS[1] = cm:item:state:{itemId}  占位状态标记，值 ON_SALE / LOCKED / SOLD
-- KEYS[2] = cm:item:holder           占位持有者 Hash，field = stateKey，value = 抢到锁的 orderId
-- ARGV[1] = ON_SALE   ARGV[2] = LOCKED   ARGV[3] = 锁 TTL（秒）   ARGV[4] = orderId
-- 返回：1 抢到；2 同一订单重入（幂等）；0 已售出（终态）；-2 他人付款中（可重试）；-1 缓存缺失，放行交给 DB 裁决
-- 约定：key 一律由 KEYS 传入，脚本内不出现字面量 key（Redis Cluster 下字面量 key 会算错 slot）
local cur = redis.call('GET', KEYS[1])
if cur == false then
  return -1
end
-- 他人持锁只是「有单正在走 DB 提交」，不是卖掉了：单独回 -2，让上层提示「稍后重试」而非「已被买走」。
-- 重入判据是 holder，不是 LOCKED 本身——同一订单的重复回调必须放行，否则幂等会退化成报错。
if cur == ARGV[2] then
  if redis.call('HGET', KEYS[2], KEYS[1]) == ARGV[4] then
    return 2
  end
  return -2
end
-- 走到这里只剩 SOLD（或其他未知值），视作终态。顺手清掉可能残留的 holder：
-- LOCKED 的 TTL 到期后 field 会变成孤儿，SOLD 一旦写入就再没有释放路径，只能在这里收尾。
if cur ~= ARGV[1] then
  redis.call('HDEL', KEYS[2], KEYS[1])
  return 0
end
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
redis.call('HSET', KEYS[2], KEYS[1], ARGV[4])
return 1
