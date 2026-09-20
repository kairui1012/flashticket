-- Lua reservation flow:
-- 1. Check whether the stock key exists.
-- 2. Check whether the user has already reserved stock.
-- 3. Check whether availableStock is greater than or equal to quantity.
-- 4. Decrease the available stock using DECRBY.
-- 5. Store the user's reservation with a five-minute expiration.
-- 6. Return the corresponding status code.

-- Return codes:
-- 1 = Reservation succeeded.
-- 0 = Insufficient stock.
-- -1 = The stock key does not exist.
-- -2 = The user has already reserved stock.
-- -3 = The requested quantity is invalid.

local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local reservationTtlSeconds = 300

if not quantity or quantity <= 0 then
    return -3
end

local availableStock = redis.call("GET", stockKey)

if not availableStock then
    return -1
end

local exist = redis.call("EXISTS", reservationKey)

if exist == 1 then
    return -2
end

availableStock = tonumber(availableStock)

if availableStock < quantity then
    return 0
end

redis.call("DECRBY", stockKey, quantity)

redis.call("SET", reservationKey, quantity, "EX", reservationTtlSeconds)
return 1
