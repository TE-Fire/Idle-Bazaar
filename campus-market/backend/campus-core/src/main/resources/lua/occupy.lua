-- 商品占位抢占：OrderService.pay 的 Redis 前置拦截（口径见 tech/01 §9.1）
-- KEYS[1] = cm:item:state:{itemId}  占位状态标记，值 ON_SALE / LOCKED / SOLD
-- KEYS[2] = cm:item:holder           占位持有者 Hash，field = stateKey，value = 抢到锁的 orderId
-- ARGV[1] = ON_SALE   ARGV[2] = LOCKED   ARGV[3] = 锁 TTL（秒）   ARGV[4] = orderId
-- 返回：1 抢到；2 同一订单重入（幂等）；0 冲突（他人已锁定或已售出）；-1 缓存缺失，必须放行交给 DB 裁决
-- 约定：key 一律由 KEYS 传入，脚本内不出现字面量 key（Redis Cluster 下字面量 key 会算错 slot）
local cur = redis.call('GET', KEYS[1])
if cur == false then
  return -1
end
if cur == ARGV[2] then
  if redis.call('HGET', KEYS[2], KEYS[1]) == ARGV[4] then
    return 2
  end
  return 0
end
if cur ~= ARGV[1] then
  return 0
end
redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
redis.call('HSET', KEYS[2], KEYS[1], ARGV[4])
return 1
