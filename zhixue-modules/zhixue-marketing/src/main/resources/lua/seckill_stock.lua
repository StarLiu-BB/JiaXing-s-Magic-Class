-- 秒杀扣减库存（原子操作）
-- KEYS[1] = 库存 key
-- KEYS[2] = 已抢购用户集合 key（必须走 KEYS，否则 Redis Cluster 报 CROSSSLOT）
-- ARGV[1] = userId
--
-- 返回值：1=扣减成功  0=已售罄  2=该用户已抢购过

local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]

-- 已抢过，拒绝重复参与
if redis.call('SISMEMBER', userKey, userId) == 1 then
    return 2
end

local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock <= 0 then
    return 0
end

redis.call('DECR', stockKey)
redis.call('SADD', userKey, userId)
return 1
