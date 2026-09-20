--Lua:
--1. 检查 stock key 存不存在
--2. 检查用户是否已经 reserve
--3. 检查 availableStock >= quantity
--4. DECRBY stock
--5. SET reservation
--6. return 状态码

--1 = reserve 成功
--0 = 库存不足
---1 = stock key 不存在
---2 = 用户已经 reserve
---3 = quantity 非法

local stockKey = KEYS[1]
local reservationKey = KEYS[2]
local quantity = tonumber(ARGV[1])

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

redis.call("SET", reservationKey, quantity)

return 1