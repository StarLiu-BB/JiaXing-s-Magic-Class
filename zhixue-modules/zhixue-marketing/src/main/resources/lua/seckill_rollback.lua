-- 秒杀扣减的补偿回滚（原子操作）
-- 用于 Redis 扣减成功但后续数据库写入失败的场景：
-- 若不回滚，库存会永久丢失，且该用户被永久标记为已抢购。
--
-- KEYS[1] = 库存 key
-- KEYS[2] = 已抢购用户集合 key
-- ARGV[1] = userId
--
-- 返回值：1=已回滚  0=无需回滚（该用户本就不在集合中）

local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]

-- 幂等保护：只有该用户确实被标记过才归还库存，
-- 否则重复调用回滚会导致库存虚增（卖出数超过实际库存）
if redis.call('SISMEMBER', userKey, userId) == 0 then
    return 0
end

redis.call('SREM', userKey, userId)
redis.call('INCR', stockKey)
return 1
