-- 关注：已是关注中返回 0（幂等），否则双写两个 ZSET 并记入写缓冲，返回 1。
-- KEYS[1] = 关注者的 following ZSET
-- KEYS[2] = 被关注者的 followers ZSET
-- KEYS[3] = pending Hash
-- ARGV[1] = 关注者 id   ARGV[2] = 被关注者 id   ARGV[3] = 当前毫秒
-- 约定：key 一律由 KEYS 传入，脚本内不出现字面量 key。
if redis.call('ZSCORE', KEYS[1], ARGV[2]) then
    return 0
end
redis.call('ZADD', KEYS[1], ARGV[3], ARGV[2])
redis.call('ZADD', KEYS[2], ARGV[3], ARGV[1])
redis.call('HSET', KEYS[3], ARGV[1] .. ':' .. ARGV[2], 'follow|' .. ARGV[3])
return 1
