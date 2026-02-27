-- KEYS[1] = stock key
-- ARGV[1] = user set key
-- ARGV[2] = userId

local stockKey = KEYS[1]
local userKey = ARGV[1]
local userId = ARGV[2]

-- 已抢过
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


