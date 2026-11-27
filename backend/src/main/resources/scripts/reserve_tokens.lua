-- KEYS[1] = tenant:{tenantId}:tokens:{yyyy-MM}
-- ARGV[1] = monthly_limit (e.q. 100000)
-- ARGV[2] = tokens_to_reserve (e.q. estimated 2000)
-- ARGV[3] = ttl_seconds (e.q. 3024000 = 35 days)

local existed = redis.call('EXISTS', KEYS[1])
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local limit = tonumber(ARGV[1])
local requested = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

if current + requested > limit then
    return {limit - current, existed == 0 and 1 or 0}
end

local updated = redis.call('INCRBY', KEYS[1], requested)
if updated == requested then
    redis.call('EXPIRE', KEYS[1], ttl)
end

return {limit - updated, existed == 0 and 1 or 0}