# FlashTicket 最终实机验收测试

## 1. 文档信息

| 项目 | 内容 |
| --- | --- |
| 文档类型 | 最终实机验收测试记录（Runtime Acceptance Test） |
| 测试日期 | 2026-10-01 |
| 测试时区 | Asia/Kuala_Lumpur（MYT，UTC+8） |
| 测试环境 | macOS 本机应用进程 + Docker 基础设施 |
| 测试入口 | API Gateway `http://localhost:8080` |
| 测试方法 | 真实 HTTP 请求，并交叉核对 MySQL、Redis、Kafka |
| 总体结论 | **条件通过（后端链路通过；待完成 1 笔 Stripe Test Mode 付款）** |

本文用于现场演示和验收留档。测试不使用 Mock 或单元测试替代真实服务调用，所有结论均来自 2026-10-01 的运行结果。

## 2. 测试目标与范围

本次测试覆盖以下关键链路：

```text
注册/登录 -> Gateway JWT 鉴权
  -> 创建 Ticket 和 Inventory
  -> Redis Lua 原子预留库存
  -> Kafka inventory.reserved
  -> Order PENDING_PAYMENT + Order Outbox
  -> Kafka order.created
  -> Payment PENDING
  -> 用户取消或五分钟超时
  -> Inventory Release Task
  -> Redis/MySQL 库存恢复
```

同时检查：

- 未授权访问和角色权限
- Payment 幂等创建
- Redis 库存、预留 Key 与 TTL
- Order Outbox 发布状态
- Kafka Consumer Lag
- 取消和过期后的库存补偿
- Stripe Checkout 的运行配置与可用性
- Order 与 Payment 的最终状态一致性

不包含 JMeter 压测。压测方案与历史结果见 `INVENTORY_LOAD_TEST.md`。

## 3. 测试环境

### 3.1 应用与基础设施

| 组件 | 地址或端口 | 2026-10-01 实测状态 |
| --- | --- | --- |
| API Gateway | `localhost:8080` | `UP`（携带有效 JWT） |
| Auth Service | `localhost:8081` | 端口可达；健康端点受安全规则保护 |
| Ticket Service | `localhost:8082` | `UP` |
| Inventory Service | `localhost:8083` | `UP` |
| Order Service | `localhost:8084` | `UP` |
| Payment Service | `localhost:8085` | `UP` |
| MySQL | `localhost:3307` | Running |
| Redis | `localhost:6379` | `PONG` |
| Kafka | `localhost:9092` | Running |
| ZooKeeper | Docker internal `2181` | Running |

### 3.2 测试数据

| 数据 | 实测值 |
| --- | --- |
| Event ID | `FINAL-EVENT-20261001` |
| Ticket ID | `96074c62-2f38-49c1-ae8e-d59628aacc09` |
| 初始库存 | `10` |
| 单价 | `88.00 MYR` |
| 取消复测 Order ID | `6923cda1-d560-4ae6-b832-7d02d548f13a` |
| 取消复测 Payment ID | `25b239b7-a305-4d03-bc3d-36b39ae39950` |
| 过期复测 Order ID | `fd5e3aec-bfb4-4fbb-bdf7-12afe395fda7` |
| 过期复测 Payment ID | `e6b17ee3-1d6e-44a2-a553-bb6c1e0ca509` |

测试账号密码和 JWT 不写入文档。执行时通过环境变量提供。

## 4. 前置条件

### 4.1 确认基础设施

```bash
docker compose ps
docker exec flashticket-redis redis-cli PING
docker exec flashticket-kafka \
  kafka-topics --bootstrap-server localhost:9092 --list
```

预期：MySQL、Redis、Kafka、ZooKeeper 均为 Running；Redis 返回 `PONG`；至少存在：

```text
inventory.reserved
inventory.release
order.created
payment.succeeded
```

### 4.2 确认应用健康

```bash
for port in 8082 8083 8084 8085; do
  curl -sS "http://localhost:${port}/actuator/health"
done
```

预期：四个请求均返回 HTTP `200`，Body 中的 `status` 为 `UP`。

Gateway 的健康端点需要 JWT：

```bash
curl -sS \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  http://localhost:8080/actuator/health
```

预期：HTTP `200`，返回 `{"status":"UP",...}`。

### 4.3 准备变量

使用已配置的本地管理员账号，不要把真实密码或 JWT 提交到 Git：

```bash
export BASE_URL='http://localhost:8080'
export ADMIN_EMAIL='<local-admin-email>'
export ADMIN_PASSWORD='<local-admin-password>'

export ADMIN_TOKEN=$(curl -sS \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --arg email "$ADMIN_EMAIL" \
    --arg password "$ADMIN_PASSWORD" \
    '{email:$email,password:$password}')" \
  "$BASE_URL/api/v1/auth/login" | jq -r '.accessToken')
```

验收前确认 `ADMIN_TOKEN` 不为空。

## 5. 实机测试步骤

### TC-01 注册、登录与 Gateway 鉴权

步骤：

1. 调用 `POST /api/v1/auth/register` 注册普通用户。
2. 调用 `POST /api/v1/auth/login` 获取 JWT。
3. 不带 JWT 请求 `GET /api/v1/tickets/{id}`。
4. 使用普通 USER JWT 调用管理接口 `POST /api/v1/tickets`。

请求示例：

```bash
export USER_EMAIL="final.user.$(date +%s)@flashticket.local"
export USER_PASSWORD='<local-test-password>'

REGISTER_RESPONSE=$(curl -sS \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --arg email "$USER_EMAIL" \
    --arg password "$USER_PASSWORD" \
    '{email:$email,password:$password}')" \
  "$BASE_URL/api/v1/auth/register")

export USER_ID=$(printf '%s' "$REGISTER_RESPONSE" | jq -r '.id')
export USER_TOKEN=$(printf '%s' "$REGISTER_RESPONSE" | jq -r '.accessToken')
```

预期结果：

- 注册返回 `201`，并返回用户 ID 和 JWT。
- 未携带 JWT 的受保护请求返回 `401`。
- USER 调用管理员管理接口返回 `403`。

2026-10-01 实测：**通过**。

### TC-02 创建 Ticket 与 Inventory

步骤：

1. 使用 ADMIN JWT 创建销售时间有效的 Ticket。
2. 使用返回的 Ticket ID 创建 Inventory。
3. 检查 Redis 原子库存 Key。

```bash
TICKET_RESPONSE=$(curl -sS \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId":"FINAL-EVENT-RETEST",
    "name":"Final Runtime Retest",
    "description":"Runtime acceptance fixture",
    "price":88.00,
    "totalStock":10,
    "saleStartTime":"<current-time-minus-5-minutes>",
    "saleEndTime":"<current-time-plus-1-day>"
  }' \
  "$BASE_URL/api/v1/tickets")

export TICKET_ID=$(printf '%s' "$TICKET_RESPONSE" | jq -r '.id')

curl -sS \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg ticketId "$TICKET_ID" \
    '{ticketId:$ticketId,totalStock:10}')" \
  "$BASE_URL/api/v1/inventory/insert"

docker exec flashticket-redis \
  redis-cli GET "inventory:stock:${TICKET_ID}"
```

预期结果：Ticket 与 Inventory 均返回 `201`；MySQL 库存为 `10/10/0`；Redis Stock 为 `10`。

2026-10-01 实测：**通过**。

### TC-03 普通用户预留权限

```bash
curl -i \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --arg ticketId "$TICKET_ID" \
    --arg userId "$USER_ID" \
    '{ticketId:$ticketId,userId:$userId,reservedStock:2}')" \
  "$BASE_URL/api/v1/inventory/${TICKET_ID}/reserve"
```

业务预期：普通已登录用户应能够预留库存，返回 HTTP `200`。

2026-10-01 14:29 MYT 复测：**通过**。新注册的普通 `USER` 经 Gateway 调用该接口返回 HTTP `200`，并成功触发 Order 与 Payment 创建。

为继续验证后端异步链路，本轮仅使用 ADMIN JWT 发送同一预留请求。此操作不能替代 TC-03 的修复与复测。

### TC-04 库存预留、订单与 Payment 自动创建

```bash
curl -sS \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc \
    --arg ticketId "$TICKET_ID" \
    --arg userId "$USER_ID" \
    '{ticketId:$ticketId,userId:$userId,reservedStock:2}')" \
  "$BASE_URL/api/v1/inventory/${TICKET_ID}/reserve"
```

请求返回后等待异步消费者收敛，再执行：

```bash
docker exec flashticket-mysql mysql -uroot -proot -e "
SELECT total_stock, available_stock, reserved_stock
FROM flashticket_inventory.inventories
WHERE ticket_id='${TICKET_ID}';

SELECT id, status, quantity, total_amount, expires_at
FROM flashticket_order.orders
WHERE ticket_id='${TICKET_ID}' AND user_id='${USER_ID}'
ORDER BY created_at DESC LIMIT 1;
"

docker exec flashticket-redis \
  redis-cli GET "inventory:stock:${TICKET_ID}"
docker exec flashticket-redis \
  redis-cli TTL "inventory:reserved:${TICKET_ID}:${USER_ID}"
```

预期结果：

- HTTP 返回 `200`，available/reserved 为 `8/2`。
- Redis Stock 为 `8`；用户预留 Key 的值为 `2`，TTL 接近 `900` 秒。
- Inventory MySQL 收敛为 `10/8/2`。
- 创建一条 `PENDING_PAYMENT` Order，总额为 `176.00`。
- Outbox 最终为 `PUBLISHED` 且 `retry_count=0`。
- 自动创建一条 `PENDING` Payment，总额为 `176.00`。
- `payment:{paymentId}` 与 `payment:order:{orderId}` TTL 接近 `300` 秒。

2026-10-01 实测：**通过**。实测预留 TTL 为 `898` 秒，两个 Payment Cache TTL 均为 `299` 秒。

### TC-05 Payment 查询与幂等创建

步骤：

1. 通过 Order ID 查询 Payment。
2. 再次调用 `POST /api/v1/payments`，Body 只提交同一个 `orderId`。
3. 比较两次返回的 Payment ID。

```bash
curl -sS \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "$BASE_URL/api/v1/payments/order/${ORDER_ID}"

curl -sS \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --arg orderId "$ORDER_ID" '{orderId:$orderId}')" \
  "$BASE_URL/api/v1/payments"
```

预期：查询返回 `200`；重复创建不会插入第二条记录，并返回相同 Payment ID。

2026-10-01 实测：**通过**。重复请求返回 `201`，但 Payment ID 与自动创建的记录一致。

### TC-06 Stripe Checkout Session

前置条件：Payment Service 启动时必须配置 Stripe Test Mode：

```text
STRIPE_SECRET_KEY
STRIPE_WEBHOOK_SECRET
```

执行：

```bash
curl -i -X POST \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "$BASE_URL/api/v1/payments/${PAYMENT_ID}/checkout-session"
```

预期：HTTP `201`，返回非空 `sessionId` 和 Stripe `checkoutUrl`。

2026-10-01 14:30 MYT 复测：**Checkout Session 创建通过**。经 Gateway 调用返回 HTTP `201`，`sessionId` 非空，且 `checkoutUrl` 为有效的 `https://checkout.stripe.com/` Test Mode 地址。本轮尚未在 Stripe 托管页面提交测试卡，因此不宣称 Webhook -> Payment `SUCCEEDED` -> Order `PAID` 已通过。

### TC-07 用户取消与库存恢复

```bash
curl -sS -X POST \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  "$BASE_URL/api/v1/orders/${ORDER_ID}/cancel"
```

等待 Release Worker 后检查：

```bash
docker exec flashticket-mysql mysql -uroot -proot -e "
SELECT status FROM flashticket_order.orders WHERE id='${ORDER_ID}';
SELECT reason, status, retry_count, release_id
FROM flashticket_order.inventory_release_tasks
WHERE order_id='${ORDER_ID}';
SELECT total_stock, available_stock, reserved_stock
FROM flashticket_inventory.inventories
WHERE ticket_id='${TICKET_ID}';
SELECT status FROM flashticket_payment.payments
WHERE order_id='${ORDER_ID}';
"
```

预期结果：

- Order 为 `CANCELLED`。
- Release Task 为 `CANCELLED / SUCCESS / retry_count=0`。
- Redis 预留 Key 被删除；Redis Stock 恢复为 `10`。
- Inventory MySQL 恢复为 `10/10/0`。
- 重复取消仍返回 `CANCELLED`，并且 Release Task 总数保持 `1`。
- Payment 不应永久停留在可支付的 `PENDING` 状态。

2026-10-01 14:30 MYT 复测：**通过**。Order 为 `CANCELLED`，Payment 同步为 `CANCELLED`，`failure_reason=ORDER_CANCELLED`；`ORDER_TERMINATED` Outbox 为 `PUBLISHED / retry_count=0`，`order.terminated` Consumer Lag 为 `0`。

### TC-08 五分钟自动过期与库存恢复

使用另一个普通用户重新执行 TC-04，记录 Order 的 `expires_at`，不要发送取消或支付请求。轮询：

```bash
while true; do
  docker exec flashticket-mysql mysql -uroot -proot -N -e "
  SELECT status, expires_at
  FROM flashticket_order.orders
  WHERE id='${EXPIRY_ORDER_ID}';"
  sleep 10
done
```

过期后检查 Order、Release Task、Inventory、Payment 和 Redis 预留 Key。

预期结果：

- 截止时间之前保持 `PENDING_PAYMENT`。
- 截止后一个调度周期内变成 `EXPIRED`。
- Release Task 为 `EXPIRED / SUCCESS / retry_count=0`。
- Redis 与 MySQL 库存恢复到 `10/10/0`。
- Payment 同步为不可支付的终态，或存在明确的取消/失效标记。

2026-10-01 14:40 MYT 复测：**通过**。订单在 `expires_at=14:36:21 MYT` 后由调度器自动转为 `EXPIRED`，Payment 同步为 `EXPIRED`，`failure_reason=ORDER_EXPIRED`；`ORDER_TERMINATED` Outbox 为 `PUBLISHED / retry_count=0`，`order.terminated` Consumer Lag 为 `0`。

### TC-09 Kafka 收敛

```bash
docker exec flashticket-kafka \
  kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --all-groups --describe
```

2026-10-01 实测：

| Consumer Group | Topic | Current Offset | Log End Offset | Lag |
| --- | --- | ---: | ---: | ---: |
| `inventory-service` | `inventory.reserved` | `18115` | `18115` | `0` |
| `inventory-service` | `inventory.release` | `10` | `10` | `0` |
| `order-service` | `inventory.reserved` | `18115` | `18115` | `0` |
| `payment-service` | `order.created` | `3` | `3` | `0` |

`order-service / payment.succeeded` 尚无成功支付消息，本轮不能据此证明支付成功消费链路。

## 6. 验收结果汇总

| Case | 测试项 | 结果 | 证据摘要 |
| --- | --- | --- | --- |
| TC-01 | 注册、登录、JWT、角色鉴权 | PASS | `201 / 401 / 403` 符合预期 |
| TC-02 | Ticket 与 Inventory 初始化 | PASS | `201 / 201`，Redis Stock=`10` |
| TC-03 | 普通用户预留库存 | PASS | USER 经 Gateway 请求返回 `200` |
| TC-04 | Reserve -> Order -> Outbox -> Payment | PASS | `8/2`、`PENDING_PAYMENT`、`PUBLISHED`、Payment `PENDING` |
| TC-05 | Payment 查询与幂等创建 | PASS | 重复创建返回同一个 Payment ID |
| TC-06 | Stripe Checkout | PASS / MANUAL PAYMENT PENDING | HTTP `201`，Session 与 Test Mode URL 有效；待提交 1 笔测试付款 |
| TC-07 | 用户取消、幂等与库存释放 | PASS | Order/Payment 均为 `CANCELLED`，Lag=`0` |
| TC-08 | 五分钟过期与库存释放 | PASS | Order/Payment 均为 `EXPIRED`，Lag=`0` |
| TC-09 | Kafka 消费收敛 | PASS | 已产生业务消息的 Topic Lag=`0` |

## 7. 缺陷与阻断项

### CLOSED-01：普通用户预留库存

- 复测结果：USER 调用 `POST /api/v1/inventory/{ticketId}/reserve` 返回 `200`。
- 权限边界：仅 reserve 对 USER/ADMIN 开放，其他 Inventory 写接口仍保持 ADMIN 限制。

### OPEN-01：Stripe Test Mode 付款最终确认

- 已通过：Stripe 环境变量已注入，Checkout Session 返回 `201`，Test Mode URL 有效。
- 待验证：在托管 Checkout 页面提交一笔 Stripe 测试卡付款，确认签名 Webhook、Payment `SUCCEEDED`、Order `PAID` 与 `payment.succeeded` Lag=`0`。

### CLOSED-02：Order 终态同步 Payment

- 复测结果：Order `CANCELLED` 后 Payment 为 `CANCELLED`；Order `EXPIRED` 后 Payment 为 `EXPIRED`。
- 事件证据：两个 `ORDER_TERMINATED` Outbox 均发布成功，Payment Consumer Lag=`0`。

## 8. 最终结论

当前版本已证明以下后端能力可运行：

- JWT 鉴权与管理员权限
- Redis Lua 原子预留
- Kafka 驱动的 Inventory、Order、Outbox 与 Payment 创建
- Payment 创建幂等
- 用户取消与五分钟过期
- 持久化 Release Task 与幂等库存恢复
- 已产生消息的 Kafka Consumer Lag 收敛为 `0`

复测已确认 USER 预留、Stripe Checkout Session 创建、Order 取消/过期与 Payment 终态同步全部正常。尚缺一项需要在 Stripe 托管页面完成的人工 Test Mode 付款，因此本轮结论为：

> **CONDITIONALLY READY / 条件通过。后端下单、Checkout 创建、取消和过期补偿链路已通过实机验收；完成 Stripe 测试付款后才可宣称完整支付成功闭环为 READY。**

## 9. 最后人工验证项

只剩一项：完成一笔 Stripe Test Mode 支付后，核对 Payment=`SUCCEEDED`、Order=`PAID`、`payment.succeeded` Lag=`0`。此项通过后，可将本文档总体结论更新为 `READY / PASS`。
