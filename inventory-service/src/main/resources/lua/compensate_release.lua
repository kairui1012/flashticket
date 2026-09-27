local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local soldOutKey = KEYS[3]
local releaseKey = KEYS[4]

local quantity = tonumber(ARGV[1])
local reservationTtlSeconds = tonumber(ARGV[2])

if not quantity or quantity <= 0 then
    return -3
end

if redis.call("EXISTS", stockKey) == 0 then
    return -1
end

if redis.call("EXISTS", reservationKey) == 1 then
    return -2
end

local availableStock =
tonumber(redis.call("GET", stockKey))

if availableStock < quantity then
    return -4
end

local remainingStock =
redis.call("DECRBY", stockKey, quantity)

redis.call(
        "SET",
        reservationKey,
        quantity,
        "EX",
        reservationTtlSeconds
)

if remainingStock == 0 then
    redis.call("SET", soldOutKey, "1")
end

redis.call("DEL", releaseKey)

return remainingStock