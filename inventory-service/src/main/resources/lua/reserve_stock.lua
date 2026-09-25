-- Lua reservation flow:
-- 1. Check whether the stock key exists.
-- 2. Check whether the user has already reserved stock.
-- 3. Check whether availableStock is greater than or equal to quantity.
-- 4. Decrease the available stock using DECRBY.
-- 5. Store the user's reservation with a five-minute expiration.
-- 6. Mark the ticket as sold out when the remaining stock reaches zero.
-- 7. Return the remaining stock or the corresponding failure code.

-- Return values:
-- 0 or greater = Reservation succeeded; the value is the remaining stock.
-- -1 = The stock key does not exist.
-- -2 = The user has already reserved stock.
-- -3 = The requested quantity is invalid.
-- -4 = Insufficient stock.

local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local soldOutKey = KEYS[3]

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
    if availableStock == 0 then
        redis.call("SET", soldOutKey, "1")
    end
    return -4
end

local remainingStock = redis.call("DECRBY", stockKey, quantity)

redis.call("SET", reservationKey, quantity, "EX", reservationTtlSeconds)

if remainingStock == 0 then
    redis.call("SET", soldOutKey, "1")
end

return remainingStock
