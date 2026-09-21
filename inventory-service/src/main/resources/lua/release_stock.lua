-- Lua release flow:
-- 1. Validate the quantity read from the user's reservation.
-- 2. Check whether the stock key exists.
-- 3. Check whether the user's reservation still exists.
-- 4. Confirm that the stored reservation matches the requested release quantity.
-- 5. Return the reserved quantity to available stock.
-- 6. Delete the user's reservation key.

-- Return codes:
-- 1 = Release succeeded.
-- 0 = The reservation key does not exist.
-- -1 = The stock key does not exist.
-- -2 = The reservation quantity does not match.
-- -3 = The requested quantity is invalid.

local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local quantity = tonumber(ARGV[1])

if not quantity or quantity <= 0 then
    return -3
end

if redis.call("EXISTS", stockKey) == 0 then
    return -1
end

local reservedQuantity = redis.call("GET", reservationKey)

if not reservedQuantity then
    return 0
end

reservedQuantity = tonumber(reservedQuantity)

if reservedQuantity ~= quantity then
    return -2
end

redis.call("INCRBY", stockKey, quantity)
redis.call("DEL", reservationKey)

return 1
