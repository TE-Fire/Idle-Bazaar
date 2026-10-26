-- 原子取走浏览量增量。
-- HGETALL 与 DEL 必须在同一脚本内：拆成两条命令的话，取到与删除之间新增的浏览会被
-- 随后的 DEL 一并删掉，白丢计数。返回扁平数组 [itemId, delta, itemId, delta, ...]。
local entries = redis.call('HGETALL', KEYS[1])
if #entries > 0 then
    redis.call('DEL', KEYS[1])
end
return entries
