-- 取关：本就没关注返回 0（幂等），否则移除两个 ZSET 的成员并记入写缓冲，返回 1。
-- KEYS / ARGV 同 follow.lua
if not redis.call('ZSCORE', KEYS[1], ARGV[2]) then
    return 0
end
redis.call('ZREM', KEYS[1], ARGV[2])
redis.call('ZREM', KEYS[2], ARGV[1])
redis.call('HSET', KEYS[3], ARGV[1] .. ':' .. ARGV[2], 'unfollow|' .. ARGV[3])
return 1
