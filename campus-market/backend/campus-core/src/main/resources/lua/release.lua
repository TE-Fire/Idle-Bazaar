-- 商品占位回滚：付款失败 / 订单取消时释放占位（口径见 tech/01 §9.1）
-- KEYS[1] = cm:item:state:{itemId}  占位状态标记
-- KEYS[2] = cm:item:holder           占位持有者 Hash，field = stateKey，value = 持有者 orderId
-- ARGV[1] = 发起释放的 orderId
-- 返回：1 已释放；0 未释放（holder 不是本方订单，锁已归属他人或本就不存在）
-- 约定：key 一律由 KEYS 传入，脚本内不出现字面量 key（Redis Cluster 下字面量 key 会算错 slot）
-- 安全前提：释放前必须校验 holder 是本订单号——否则慢请求失败后会误删他人刚抢到的锁，出现两个赢家
if redis.call('HGET', KEYS[2], KEYS[1]) == ARGV[1] then
  redis.call('DEL', KEYS[1])
  redis.call('HDEL', KEYS[2], KEYS[1])
  return 1
end
return 0
