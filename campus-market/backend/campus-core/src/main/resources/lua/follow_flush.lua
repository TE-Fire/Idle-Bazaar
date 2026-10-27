-- 原子取走关注关系写缓冲。
-- HGETALL 与 DEL 必须在同一脚本内：拆成两条命令的话，取到与删除之间新增的写会被随后的
-- DEL 一并删掉，白丢一次关系变更。返回扁平数组 [field, value, field, value, ...]。
local entries = redis.call('HGETALL', KEYS[1])
if #entries > 0 then
    redis.call('DEL', KEYS[1])
end
return entries
