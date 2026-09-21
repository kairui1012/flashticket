# FlashTicket

FlashTicket is a work-in-progress microservices ticketing platform for concerts, events, and live performances. The current backend provides authentication, JWT authorization, ticket management, inventory management, Redis-backed atomic stock reservation, gateway routing, and per-user rate limiting.

FlashTicket 是一个正在开发中的微服务票务平台，面向演唱会、活动及其他现场演出。目前后端已经实现身份认证、JWT 权限控制、票种管理、库存管理、基于 Redis 的原子库存预留、网关路由和按用户限流。

## Current status / 当前状态

Implemented / 已实现：

- `api-gateway` on port `8080`
- `auth-service` on port `8081`
- `ticket-service` on port `8082`
- `inventory-service` on port `8083`
- Registration and login with BCrypt password hashing
- RSA-signed JWT access tokens with `USER` and `ADMIN` roles
- JWT verification and role-based access control at the gateway
- Ticket creation, lookup, partial update, and cancellation
- Redis caching for individual tickets and event ticket lists
- Inventory creation, lookup, and manual stock adjustment
- Inventory caching and short-lived negative caching in Redis
- Atomic stock reservation with Redis Lua scripts and five-minute per-user reservation keys
- `inventory.reserved` Kafka events with idempotent MySQL synchronization
- Flyway-managed inventory and processed-event tables
- Redis-backed per-user gateway rate limiting
- MySQL persistence through MyBatis
- Local MySQL, Redis, Kafka, and ZooKeeper infrastructure through Docker Compose

Inventory release and Kafka-failure compensation are currently being completed. The release event is produced, but its MySQL consumer is not yet implemented, so the release workflow is not yet a complete end-to-end flow. Orders, payments, electronic ticket delivery, and notifications are also not yet implemented.

库存释放及 Kafka 发送失败后的补偿流程目前仍在完善中。系统已经会发布库存释放事件，但尚未实现对应的 MySQL 消费者，因此释放流程还没有形成完整闭环。订单、支付、电子票交付和通知服务也尚未实现。

For the proposed architecture and development roadmap, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Tech stack / 技术栈

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Gateway | Spring Cloud Gateway |
| Security | Spring Security, OAuth2 Resource Server, JWT |
| Persistence | MySQL 8, MyBatis |
| Cache and rate limiting | Redis |
| Messaging | Kafka, ZooKeeper |
| Database migrations | Flyway |
| Build tool | Maven Wrapper |
| Local infrastructure | Docker Compose |

## Repository structure / 项目结构

```text
FlashTicket/
├── api-gateway/       # Routing, JWT verification, authorization and rate limiting
├── auth-service/      # Registration, login and JWT generation
├── ticket-service/    # Ticket CRUD, validation and Redis caching
├── inventory-service/ # Inventory, Redis Lua reservation and Kafka synchronization
├── keys/              # Local RSA keys; never commit the private key
├── docker-compose.yml # MySQL, Redis, Kafka and ZooKeeper
└── DEVELOPMENT.md     # Proposed architecture and roadmap
```

## Local ports / 本地端口

| Service | Port |
| --- | ---: |
| API Gateway | `8080` |
| Auth Service | `8081` |
| Ticket Service | `8082` |
| Inventory Service | `8083` |
| MySQL | `3307` |
| Redis | `6379` |
| Kafka | `9092` |

## Prerequisites / 环境要求

- Java 21
- Docker and Docker Compose
- OpenSSL, only when new RSA keys need to be generated

Each service includes Maven Wrapper, so a separate Maven installation is not required.

每个服务都包含 Maven Wrapper，因此不需要另外安装 Maven。

## Local setup / 本地运行

### 1. Start infrastructure / 启动基础设施

Run from the repository root:

```bash
docker compose up -d
docker compose ps
```

Ensure the development database exists. This command is also useful after manually dropping the database while retaining the Docker volume:

如果曾经在保留 Docker Volume 的情况下手动删除数据库，请执行以下命令重新创建开发数据库：

```bash
docker exec flashticket-mysql mysql -uroot -proot -e \
  "CREATE DATABASE IF NOT EXISTS flashticket_auth CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

The Auth and Ticket services automatically create their tables from their respective `schema.sql` files when they start.

Auth 和 Ticket 服务启动时会通过各自的 `schema.sql` 自动创建数据表。

### 2. Prepare RSA keys / 准备 RSA 密钥

If `keys/private.pem` and `keys/public.pem` do not exist, generate them from the repository root:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -out keys/private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in keys/private.pem -out keys/public.pem
```

Never commit `keys/private.pem` or use these local development keys in production.

不要提交 `keys/private.pem`，也不要在生产环境继续使用本地开发密钥。

### 3. Start Auth Service / 启动认证服务

```bash
cd auth-service
./mvnw spring-boot:run
```

### 4. Start Ticket Service / 启动票务服务

Open another terminal from the repository root:

```bash
cd ticket-service
./mvnw spring-boot:run
```

### 5. Start Inventory Service / 启动库存服务

Open another terminal from the repository root. Ticket Service must already be available because Inventory Service validates ticket existence and sale times through OpenFeign.

打开另一个终端。Inventory Service 会通过 OpenFeign 验证票种是否存在及售卖时间，因此需要先启动 Ticket Service。

```bash
cd inventory-service
./mvnw spring-boot:run
```

Flyway automatically creates and validates the inventory tables when this service starts.

Inventory Service 启动时，Flyway 会自动创建并校验库存相关数据表。

### 6. Start API Gateway / 启动 API Gateway

Open another terminal from the repository root:

```bash
cd api-gateway
./mvnw spring-boot:run
```

Clients should normally access Auth, Ticket, and Inventory APIs through `http://localhost:8080`.

客户端正常情况下应通过 `http://localhost:8080` 访问 Auth、Ticket 和 Inventory API。

## Authentication API / 认证接口

### Register / 注册

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"change-me"}'
```

New accounts are created with the `USER` role. A successful registration returns `201 Created` and a signed JWT in `accessToken`.

新账号默认拥有 `USER` 角色。注册成功会返回 `201 Created`，并在 `accessToken` 中返回已签名的 JWT。

### Login / 登录

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"change-me"}'
```

Example response / 返回示例：

```json
{
  "id": "generated-uuid",
  "email": "user@example.com",
  "status": "ACTIVE",
  "accessToken": "signed-jwt"
}
```

## Ticket API / 票务接口

All Ticket endpoints require a valid JWT. Replace `<JWT>` and resource IDs in the examples below.

所有 Ticket 接口都需要有效的 JWT。请替换下面示例中的 `<JWT>` 和资源 ID。

| Method | Endpoint | Current gateway access | Description |
| --- | --- | --- | --- |
| `POST` | `/api/v1/tickets` | `ADMIN` | Create a ticket type / 创建票种 |
| `GET` | `/api/v1/tickets/{id}` | `USER`, `ADMIN` | Get a ticket by ID / 按 ID 查询 |
| `GET` | `/api/v1/tickets/event/{eventId}` | `USER`, `ADMIN` | List tickets for an event / 查询活动票种 |
| `PUT` | `/api/v1/tickets/{id}` | `USER`, `ADMIN` | Partially update a ticket / 部分更新票种 |
| `PATCH` | `/api/v1/tickets/{id}/status/cancel` | `USER`, `ADMIN` | Cancel a ticket / 取消票种 |

### Create a ticket / 创建票种

```bash
curl -X POST http://localhost:8080/api/v1/tickets \
  -H 'Authorization: Bearer <ADMIN_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "event-001",
    "name": "Early Bird",
    "description": "Limited early-bird allocation",
    "price": 49.90,
    "totalStock": 100,
    "saleStartTime": "2027-01-01T10:00:00",
    "saleEndTime": "2027-01-31T23:59:59"
  }'
```

For local development, there is not yet an Admin-management API. To promote a development account, update its role in MySQL and then log in again to receive a new JWT:

本地开发环境暂时没有管理员管理接口。可以通过 MySQL 将测试账号改为管理员，然后重新登录以获取新的 JWT：

```bash
docker exec flashticket-mysql mysql -uroot -proot flashticket_auth -e \
  "UPDATE auth_accounts SET role='ADMIN' WHERE email='user@example.com';"
```

### Query and update / 查询与更新

```bash
curl http://localhost:8080/api/v1/tickets/<TICKET_ID> \
  -H 'Authorization: Bearer <JWT>'
```

```bash
curl http://localhost:8080/api/v1/tickets/event/<EVENT_ID> \
  -H 'Authorization: Bearer <JWT>'
```

```bash
curl -X PUT http://localhost:8080/api/v1/tickets/<TICKET_ID> \
  -H 'Authorization: Bearer <JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Updated ticket name","price":59.90}'
```

```bash
curl -X PATCH http://localhost:8080/api/v1/tickets/<TICKET_ID>/status/cancel \
  -H 'Authorization: Bearer <JWT>'
```

## Inventory API / 库存接口

Inventory endpoints are routed through the Gateway. `GET` requests allow `USER` and `ADMIN`; write operations currently require an `ADMIN` token.

库存接口已经接入 Gateway。`GET` 请求允许 `USER` 和 `ADMIN` 访问，写操作目前需要 `ADMIN` Token。

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/v1/inventory/insert` | Create inventory for an existing ticket / 为现有票种创建库存 |
| `GET` | `/api/v1/inventory/{ticketId}` | Get inventory by ticket ID / 按票种 ID 查询库存 |
| `PUT` | `/api/v1/inventory/update/{ticketId}` | Replace available stock / 修改可用库存 |
| `PUT` | `/api/v1/inventory/{ticketId}/increase` | Increase total and available stock / 增加总库存及可用库存 |
| `PUT` | `/api/v1/inventory/{ticketId}/decrease` | Decrease total and available stock / 减少总库存及可用库存 |
| `POST` | `/api/v1/inventory/reserve` | Atomically reserve stock / 原子预留库存 |
| `POST` | `/api/v1/inventory/release` | Release a user's reservation; workflow still in progress / 释放用户预留库存，流程仍在完善 |

### Create and query inventory / 创建及查询库存

The referenced ticket must already exist.

对应的票种必须已经存在。

```bash
curl -X POST http://localhost:8080/api/v1/inventory/insert \
  -H 'Authorization: Bearer <ADMIN_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{"ticketId":"<TICKET_ID>","totalStock":100}'
```

```bash
curl http://localhost:8080/api/v1/inventory/<TICKET_ID> \
  -H 'Authorization: Bearer <JWT>'
```

### Reserve stock / 预留库存

The service validates the ticket sale window, atomically decrements `inventory:stock:{ticketId}`, and creates `inventory:reserved:{ticketId}:{userId}` with a five-minute TTL. A successful reservation publishes `inventory.reserved`; the idempotent consumer then synchronizes MySQL.

服务会先验证票种售卖时间，再原子扣减 `inventory:stock:{ticketId}`，并创建有效期为五分钟的 `inventory:reserved:{ticketId}:{userId}`。预留成功后会发布 `inventory.reserved`，再由具备幂等处理的消费者同步 MySQL。

```bash
curl -X POST http://localhost:8080/api/v1/inventory/reserve \
  -H 'Authorization: Bearer <ADMIN_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{
    "ticketId":"<TICKET_ID>",
    "userId":"<USER_ID>",
    "reservedStock":2
  }'
```

## Redis behavior / Redis 行为

Ticket Service caches the following query results for 10 minutes:

- `ticket:{ticketId}` for a single ticket
- `tickets:event:{eventId}` for an event's ticket list

Creating a ticket invalidates its event-list cache. Updating or cancelling a ticket refreshes its individual cache and invalidates the related event-list cache.

创建票种时会清除对应活动列表缓存；更新或取消票种时会刷新单票缓存，并清除对应活动列表缓存。

Inventory Service uses these keys:

- `inventory:{ticketId}`: inventory-detail cache with a 10-minute TTL
- `inventory:not-found:{ticketId}`: missing-inventory marker with a 30-second TTL
- `inventory:stock:{ticketId}`: available-stock counter used by Lua scripts
- `inventory:reserved:{ticketId}:{userId}`: per-user reservation with a five-minute TTL

Inventory Service 使用以上 Redis Key 缓存库存详情、防止缓存穿透，并通过 Lua 脚本原子处理可用库存和用户预留记录。

API Gateway also uses Redis to limit each authenticated user to a replenish rate of 5 Ticket requests per second with a burst capacity of 10.

API Gateway 还会通过 Redis 按已登录用户进行限流：每秒补充 5 个请求额度，最大突发容量为 10。

## Redis performance check / Redis 性能测试

This is a real HTTP comparison, not a unit test. Send requests directly to Ticket Service on port `8082` so that Gateway authentication and rate limiting do not affect the timing.

这是一个真实 HTTP 性能对比，不是单元测试。测试时直接访问 `8082` 的 Ticket Service，避免 Gateway 鉴权和限流影响测量结果。

Before starting, make sure MySQL and Redis are running, start Ticket Service, and prepare an existing Ticket ID:

开始前请确认 MySQL 和 Redis 正常运行，启动 Ticket Service，并准备一个已经存在的 Ticket ID：

```bash
docker compose up -d mysql redis

cd ticket-service
./mvnw spring-boot:run
```

### Quick comparison / 快速对比

Open another terminal from the repository root and replace the example ID:

打开另一个终端，并将示例 ID 替换为真实的 Ticket ID：

```bash
TICKET_ID='replace-with-a-real-ticket-id'
TICKET_URL="http://localhost:8082/api/v1/tickets/$TICKET_ID"
```

Delete the cache and send the first request. This request must read from MySQL and then write the result to Redis:

先删除缓存并发送第一次请求。该请求会查询 MySQL，然后将结果写入 Redis：

```bash
docker exec flashticket-redis redis-cli DEL "ticket:$TICKET_ID"
curl -sS -o /dev/null -w 'cache_miss=%{time_total}s\n' "$TICKET_URL"
```

Send the same request again without deleting the key. This request should read from Redis:

不要删除 Key，直接发送相同请求。该请求应该从 Redis 读取：

```bash
curl -sS -o /dev/null -w 'cache_hit=%{time_total}s\n' "$TICKET_URL"
```

Confirm that the cache exists and inspect its remaining TTL:

确认缓存已经存在，并查看剩余 TTL：

```bash
docker exec flashticket-redis redis-cli EXISTS "ticket:$TICKET_ID"
docker exec flashticket-redis redis-cli TTL "ticket:$TICKET_ID"
```

Expected results:

- `EXISTS` returns `1`.
- `TTL` returns a value close to `600` seconds.
- The first request produces a MyBatis `SELECT` in the Ticket Service log.
- The second request does not produce another Ticket `SELECT` because it is served by Redis.
- The second request is normally faster, but a single request can be affected by JVM warm-up and operating-system scheduling.

预期结果：

- `EXISTS` 返回 `1`。
- `TTL` 返回接近 `600` 秒的数值。
- 第一次请求会在 Ticket Service 日志中产生 MyBatis `SELECT`。
- 第二次请求由 Redis 返回，不会再次产生 Ticket `SELECT`。
- 第二次请求通常更快，但单次结果可能受到 JVM 预热及系统调度影响。

### Repeated benchmark / 多次统计测试

Use multiple samples for a more reliable comparison. The Redis deletion command is completed before timing each cache-miss HTTP request, so Docker command time is not included in the measurement.

为了得到更可靠的结果，可以进行多次采样。每次缓存未命中测试都会先完成 Redis 删除，再开始计算 HTTP 请求时间，因此 Docker 命令本身不会计入结果。

```bash
TICKET_ID='replace-with-a-real-ticket-id'
TICKET_URL="http://localhost:8082/api/v1/tickets/$TICKET_ID"
MISS_RESULTS=$(mktemp /tmp/flashticket-cache-miss.XXXXXX)
HIT_RESULTS=$(mktemp /tmp/flashticket-cache-hit.XXXXXX)

# Warm up the servlet and JVM paths before collecting results.
for run in {1..10}; do
  curl -sS -o /dev/null "$TICKET_URL"
done

# 40 cache misses: delete the exact key before every request.
for run in {1..40}; do
  docker exec flashticket-redis redis-cli DEL "ticket:$TICKET_ID" >/dev/null
  curl -sS -o /dev/null -w '%{time_total}\n' "$TICKET_URL" >> "$MISS_RESULTS"
done

# Warm the cache once, then collect 100 cache-hit requests.
curl -sS -o /dev/null "$TICKET_URL"
for run in {1..100}; do
  curl -sS -o /dev/null -w '%{time_total}\n' "$TICKET_URL" >> "$HIT_RESULTS"
done

print_stats() {
  sort -n "$1" | awk -v label="$2" '
    { values[NR]=$1; total+=$1 }
    END {
      p50=values[int((NR-1)*0.50)+1]
      p95=values[int((NR-1)*0.95)+1]
      printf "%s count=%d avg=%.3fms p50=%.3fms p95=%.3fms\n", \
        label, NR, (total/NR)*1000, p50*1000, p95*1000
    }'
}

print_stats "$MISS_RESULTS" 'cache_miss'
print_stats "$HIT_RESULTS" 'cache_hit '

MISS_AVG=$(awk '{total+=$1} END {print total/NR}' "$MISS_RESULTS")
HIT_AVG=$(awk '{total+=$1} END {print total/NR}' "$HIT_RESULTS")
awk -v miss="$MISS_AVG" -v hit="$HIT_AVG" 'BEGIN {
  printf "speedup=%.2fx latency_reduction=%.1f%%\n", \
    miss/hit, (1-hit/miss)*100
}'

rm -f "$MISS_RESULTS" "$HIT_RESULTS"
```

Example result measured locally on September 19, 2026. Results will vary between machines:

2026 年 9 月 19 日的本机实测示例。不同电脑的结果会有所差异：

```text
cache_miss count=40  avg=7.626ms p50=7.574ms p95=9.177ms
cache_hit  count=100 avg=3.901ms p50=3.618ms p95=4.603ms
speedup=1.95x latency_reduction=48.8%
```

For an event-list cache, use the same procedure with the following URL and Redis key:

活动票种列表缓存也可以使用相同方法，只需要替换 URL 和 Redis Key：

```bash
EVENT_ID='replace-with-a-real-event-id'
EVENT_URL="http://localhost:8082/api/v1/tickets/event/$EVENT_ID"
docker exec flashticket-redis redis-cli DEL "tickets:event:$EVENT_ID"
curl -sS -o /dev/null -w 'event_cache_miss=%{time_total}s\n' "$EVENT_URL"
curl -sS -o /dev/null -w 'event_cache_hit=%{time_total}s\n' "$EVENT_URL"
```

## Manual smoke check / 手动快速检查

After all four Java services are running:

1. Register a new account through the Gateway and confirm `201 Created`.
2. Log in with the same credentials and confirm `200 OK` with a non-empty `accessToken`.
3. Call a Ticket endpoint without a token and confirm `401 Unauthorized`.
4. Call `POST /api/v1/tickets` with a `USER` token and confirm `403 Forbidden`.
5. Promote the account to `ADMIN`, log in again, and create a ticket through the Gateway.
6. Query the created ticket twice and inspect Redis to confirm that a cache key with a TTL is present.
7. Create inventory for the ticket and confirm that `inventory:stock:{ticketId}` contains the initial stock.
8. Reserve stock and confirm that the available-stock counter decreases atomically and the per-user reservation key has a TTL.
9. Confirm that the Inventory Service consumes `inventory.reserved` and updates MySQL only once for the event ID.

## Roadmap / 开发计划

- Complete and verify the inventory release/compensation workflow
- Add event and venue management
- Add order and payment workflows
- Generate and validate electronic tickets
- Expand Kafka domain events across order, payment, ticket delivery, and notification workflows
- Add notification delivery
- Add integration tests, tracing, and production-ready secrets

- 完成并验证库存释放及补偿闭环
- 增加活动与场馆管理
- 增加订单和支付流程
- 生成和验证电子票
- 将 Kafka 领域事件扩展至订单、支付、电子票交付及通知流程
- 增加通知服务
- 增加集成测试、链路追踪和生产级密钥管理

## Security note / 安全说明

The current configuration is intended for local development only. Do not expose the database or infrastructure ports publicly, reuse the sample database password in production, or commit private RSA keys.

当前配置仅适用于本地开发。请勿公开暴露数据库或基础设施端口，不要在生产环境使用示例数据库密码，也不要提交 RSA 私钥。
