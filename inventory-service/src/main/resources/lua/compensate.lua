-- Kafka failure compensation flow:
-- 1. Check whether the stock key still exists.
-- 2. Read the quantity stored in the user's reservation key.
-- 3. Return the reserved quantity to available stock.
-- 4. Delete the reservation key so the user can try again.

-- Return codes:
-- 1 = Compensation succeeded.
-- 0 = The reservation key does not exist.
-- -1 = The stock key does not exist.

local stockKey = KEYS[1]
local reservationKey = KEYS[2]

if redis.call("EXISTS", stockKey) == 0 then
    return -1
end

local quantity = redis.call("GET", reservationKey)

if not quantity then
    return 0
end

redis.call("INCRBY", stockKey, tonumber(quantity))
redis.call("DEL", reservationKey)

return 1
