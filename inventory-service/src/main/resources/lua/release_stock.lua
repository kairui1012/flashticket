-- STEP 1: Validate the requested release quantity.
-- STEP 2: Return the previous result when the same release ID is retried.
-- STEP 3: Confirm that the stock and reservation keys exist.
-- STEP 4: Confirm that the reserved quantity matches the requested quantity.
-- STEP 5: Return the reserved quantity to the available stock atomically.
-- STEP 6: Delete the reservation and sold-out keys.
-- STEP 7: Store the release result temporarily for idempotent retries.

-- Return values:
-- 0 or greater = Release succeeded; the value is the remaining stock.
-- -1 = The stock key does not exist.
-- -2 = The reservation quantity does not match.
-- -3 = The requested quantity is invalid.
-- -4 = The reservation key does not exist.

local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local soldOutKey = KEYS[3]
local releaseKey = KEYS[4]
local quantity = tonumber(ARGV[1])
local releaseKeyTtlSeconds = 604800

-- STEP 1: Reject a missing, non-numeric, or non-positive quantity.
if not quantity or quantity <= 0 then
    return -3
end

-- STEP 2: Treat a repeated release ID as a successful idempotent retry.
local previousReleaseResult = redis.call("GET", releaseKey)

if previousReleaseResult then
    return tonumber(previousReleaseResult)
end

-- STEP 3: Confirm that the available-stock counter exists.
if redis.call("EXISTS", stockKey) == 0 then
    return -1
end

local reservedQuantity = redis.call("GET", reservationKey)

if not reservedQuantity then
    return -4
end

reservedQuantity = tonumber(reservedQuantity)

-- STEP 4: Release exactly the quantity stored in the user's reservation.
if reservedQuantity ~= quantity then
    return -2
end

-- STEP 5: Restore the available stock.
local remainingStock = redis.call("INCRBY", stockKey, quantity)

-- STEP 6: Remove the completed reservation and any stale sold-out marker.
redis.call("DEL", reservationKey)
redis.call("DEL", soldOutKey)

-- STEP 7: Cache the result for seven days so retries do not release stock twice.
redis.call("SET", releaseKey, remainingStock, "EX", releaseKeyTtlSeconds)

return remainingStock
