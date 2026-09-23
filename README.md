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
- `inventory.reserved` and `inventory.release` Kafka events with idempotent MySQL synchronization
- Redis compensation when reservation or release event publishing fails
- Flyway-managed inventory and processed-event tables
- Redis-backed per-user gateway rate limiting
- MySQL persistence through MyBatis
- Local MySQL, Redis, Kafka, and ZooKeeper infrastructure through Docker Compose

Reservation and explicit release now work end to end across Redis, Kafka, and MySQL. Automatic release after a five-minute reservation timeout is not yet enabled, so an expired reservation is not currently returned to available stock automatically. Orders, payments, electronic ticket delivery, and notifications are also not yet implemented.

库存预留及主动释放现在已经能够贯通 Redis、Kafka 和 MySQL。五分钟预留超时后的自动释放尚未启用，因此预留记录过期后，目前不会自动将库存归还至可用库存。订单、支付、电子票交付和通知服务也尚未实现。

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

On a new Docker volume, the initialization script creates one database for each current service. If an older MySQL volume already exists, create the databases manually because `/docker-entrypoint-initdb.d` only runs when MySQL initializes an empty data directory:

新的 Docker Volume 会通过初始化脚本为每个现有服务创建独立数据库。如果继续使用旧的 MySQL Volume，需要手动创建这些数据库，因为 `/docker-entrypoint-initdb.d` 只会在空数据目录首次初始化时执行：

```bash
docker exec flashticket-mysql mysql -uroot -proot -e \
  "CREATE DATABASE IF NOT EXISTS flashticket_auths CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE DATABASE IF NOT EXISTS flashticket_ticket CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   CREATE DATABASE IF NOT EXISTS flashticket_inventory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Auth, Ticket, and Inventory services apply their own Flyway migrations when they start.

Auth、Ticket 和 Inventory 服务启动时会分别执行各自的 Flyway 数据库迁移。

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
| `POST` | `/api/v1/inventory/release` | Release a user's reservation / 释放用户预留库存 |

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

### Release stock / 释放库存

The release flow reads the trusted quantity from the user's Redis reservation, atomically returns it to available stock, publishes `inventory.release`, and idempotently synchronizes MySQL.

释放流程会读取 Redis 中保存的可信预留数量，原子归还可用库存，发布 `inventory.release`，并以幂等方式同步 MySQL。

```bash
curl -X POST http://localhost:8080/api/v1/inventory/release \
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

## Inventory runtime verification / 库存真实运行验证

The following results were measured locally on September 23, 2026 using real HTTP requests against Inventory Service on port `8083`, with MySQL, Redis, Kafka, ZooKeeper, and Ticket Service running. These are runtime checks rather than unit-test claims.

以下结果于 2026 年 9 月 23 日在本机实测：启动 MySQL、Redis、Kafka、ZooKeeper 和 Ticket Service 后，直接通过 `8083` 对 Inventory Service 发送真实 HTTP 请求。这些结果属于运行验证，不是单元测试结论。

| Check | Observed result |
| --- | --- |
| Build and startup | Maven compile passed; Inventory Service started on `8083`; Flyway schema version was `2` |
| Create inventory | `POST /api/v1/inventory/insert` returned `201 Created` with stock `5` |
| Cached lookup | `GET /api/v1/inventory/{ticketId}` returned `200 OK`; JSON cache deserialization succeeded |
| Reserve stock | Reserving `2` returned `200 OK`; Redis available stock changed from `5` to `3` |
| Reservation TTL | `inventory:reserved:{ticketId}:{userId}` reported `300` seconds immediately after reservation |
| Duplicate reservation | A second reservation by the same user returned `409 Conflict` |
| Oversell protection | Requesting `4` while only `3` remained returned `409 Conflict` |
| Reservation event | MySQL became `total=5, available=3, reserved=2`; one `INVENTORY_RESERVED` event was recorded |
| Release stock | Release returned `200 OK`; Redis and MySQL both returned to `available=5, reserved=0` |
| Release event | One `INVENTORY_RELEASED` event was recorded; a second release returned `409 Conflict` |
| Duplicate inventory | Creating inventory again for the same ticket returned `409 Conflict` |
| Negative cache | A missing inventory returned `404 Not Found` and created a 30-second negative-cache marker |

The temporary ticket, inventory row, processed-event rows, and Redis keys used by this verification were removed afterward. The Inventory Service process started for the check was also stopped cleanly.

本次验证使用的临时票种、库存记录、已处理事件和 Redis Key 均已清理；测试期间启动的 Inventory Service 也已正常停止。

### Inventory concurrency load test / 库存并发压测

#### JMeter 10,000-user result / JMeter 万人压测结果

The latest test used Apache JMeter 5.6.3 to send `10,000` reservation requests against `1,000` available tickets. The test data came from [`jmeter/users-10000.csv`](./jmeter/users-10000.csv). JMeter completed the run at `1,347.5 requests/second`, with an average response time of `169 ms`, P95 of `294 ms`, P99 of `359 ms`, and a maximum response time of `471 ms`.

最新一次测试使用 Apache JMeter 5.6.3，让 `10,000` 个不同用户抢 `1,000` 张票，用户数据来自 [`jmeter/users-10000.csv`](./jmeter/users-10000.csv)。实测吞吐量为 `1,347.5 请求/秒`，平均响应时间 `169 ms`，P95 为 `294 ms`，P99 为 `359 ms`，最大响应时间为 `471 ms`。

| Metric / 指标 | JMeter result / 实测结果 |
| --- | ---: |
| Total requests / 总请求数 | `10,000` |
| Available tickets / 可售票数 | `1,000` |
| Throughput / 吞吐量 | `1,347.5 requests/second` |
| Average response / 平均响应 | `169 ms` |
| Median response / 中位响应 | `158 ms` |
| P90 | `271 ms` |
| P95 | `294 ms` |
| P99 | `359 ms` |
| Minimum response / 最小响应 | `8 ms` |
| Maximum response / 最大响应 | `471 ms` |
| Standard deviation / 标准差 | `72.86 ms` |
| Successful requests / 成功请求 | approximately `1,000` |
| Rejected requests / 拒绝请求 | approximately `9,000` (`90.00%`) |

The approximately `9,000` rejected requests are consistent with the expected stock-exhaustion conflicts after the first `1,000` reservations consumed all available tickets. In this flash-sale scenario, JMeter counts those expected non-2xx responses as errors. The Aggregate Report and Summary Report screenshots are retained below as test evidence.

约 `9,000` 个失败请求与库存耗尽后的预期冲突一致：前 `1,000` 个请求成功预留全部库存，后续请求被拒绝。在该秒杀场景中，JMeter 会将这些预期的非 2xx 响应计入错误率。Aggregate Report 和 Summary Report 截图保留如下，作为测试证据。

![JMeter Aggregate Report](./jmeter/image.png)

![JMeter Summary Report](./jmeter/image_2.png)

> The screenshots report aggregate HTTP errors but do not list individual response codes. Confirm expected `409 Conflict` responses in the JMeter result log when auditing a future run.
>
> 截图显示的是汇总 HTTP 错误率，不包含每条响应的具体状态码。后续审计压测时，应在 JMeter 结果日志中确认失败请求属于预期的 `409 Conflict`。

On September 23, 2026, a real HTTP load test sent `1000` unique-user reservation requests for `100` tickets with client concurrency `200`. The run completed in `8` seconds: exactly `100` requests returned `200`, `900` returned `409`, and no other status was returned. Redis finished at available stock `0` with `100` reservation keys; MySQL finished at `total=100`, `available=0`, `reserved=100`; and exactly `100` reservation events were processed. No overselling or negative stock was observed.

2026 年 9 月 23 日的真实 HTTP 压测使用 `1000` 个不同用户并发抢 `100` 张票，客户端并发数为 `200`。测试耗时 `8` 秒：恰好 `100` 个请求返回 `200`，`900` 个请求返回 `409`，没有其他 HTTP 状态。Redis 最终可用库存为 `0` 且有 `100` 个预留 Key；MySQL 最终为 `total=100`、`available=0`、`reserved=100`；成功处理的预留事件也正好为 `100`。未发现超卖或负库存。

A second timed run used `1000` tickets, `10000` unique users, and client concurrency `500`. Exactly `1000` requests returned `200` and `9000` returned `409`; there were no curl errors, other HTTP statuses, negative stock, or overselling. Wall-clock duration was `97.568` seconds (`102.49 RPS`), with average latency `27 ms`, P95 `84.921 ms`, and P99 `149.477 ms`. These throughput figures include the overhead of spawning `10000` shell/curl processes, writing individual result files, and DEBUG logging, so they are not a server-capacity ceiling.

第二次计时压测使用 `1000` 张票、`10000` 个不同用户及 `500` 客户端并发。恰好 `1000` 个请求返回 `200`，`9000` 个请求返回 `409`；没有 curl 错误、其他 HTTP 状态、负库存或超卖。总耗时为 `97.568` 秒（`102.49 RPS`），平均延迟 `27 ms`、P95 `84.921 ms`、P99 `149.477 ms`。吞吐量包含创建 `10000` 个 Shell/curl 进程、写入独立结果文件和 DEBUG 日志的开销，因此不能视为服务端容量上限。

After reservation-event publishing was changed from blocking Kafka acknowledgement to an asynchronous callback, the same scenario was rerun. Correctness still passed (`1000` successes, `9000` conflicts, Redis and MySQL both consistent), but this single run measured `85.29 RPS`, average `30.915 ms`, P95 `86.686 ms`, and P99 `241.384 ms`. MySQL converged only `0.156` seconds after all HTTP requests completed. Because the shell-based client is process-spawn limited and all dependencies share one local machine, this one-run difference is not sufficient evidence that the asynchronous code itself caused a regression.

库存事件从阻塞等待 Kafka 确认改为异步回调后，使用相同场景再次复测。正确性仍然通过（`1000` 个成功、`9000` 个冲突，Redis 与 MySQL 一致），但单次实测为 `85.29 RPS`、平均 `30.915 ms`、P95 `86.686 ms`、P99 `241.384 ms`；全部 HTTP 请求完成后，MySQL 仅需 `0.156` 秒便完成同步。由于 Shell 客户端受进程创建速度限制，并且全部依赖共享一台本机，不能仅凭这一轮差异断定异步代码本身造成性能回退。

The Ticket status remained `DRAFT` after stock reached zero. Automatic `SOLD_OUT` transition is not implemented yet, and atomic Redis stock validation must still remain even after that feature is added.

库存归零后 Ticket 状态仍为 `DRAFT`，说明自动转换为 `SOLD_OUT` 尚未实现。即使后续增加自动售罄功能，也仍需保留 Redis 原子库存校验。

The complete reusable script, assertions, inspection commands, and cleanup steps are kept in [INVENTORY_LOAD_TEST.md](./INVENTORY_LOAD_TEST.md).

完整的可重复执行脚本、断言、检查命令和清理步骤保存在 [INVENTORY_LOAD_TEST.md](./INVENTORY_LOAD_TEST.md)。

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
10. Release the reservation and confirm that Redis and MySQL both return to the original available stock.
11. Confirm that `inventory.release` is processed once and that a repeated release returns `409 Conflict`.

## Roadmap / 开发计划

- Implement automatic stock release when a five-minute reservation expires
- Add event and venue management
- Add order and payment workflows
- Generate and validate electronic tickets
- Expand Kafka domain events across order, payment, ticket delivery, and notification workflows
- Add notification delivery
- Add integration tests, tracing, and production-ready secrets

- 实现五分钟预留过期后的自动库存释放
- 增加活动与场馆管理
- 增加订单和支付流程
- 生成和验证电子票
- 将 Kafka 领域事件扩展至订单、支付、电子票交付及通知流程
- 增加通知服务
- 增加集成测试、链路追踪和生产级密钥管理

## Security note / 安全说明

The current configuration is intended for local development only. Do not expose the database or infrastructure ports publicly, reuse the sample database password in production, or commit private RSA keys.

当前配置仅适用于本地开发。请勿公开暴露数据库或基础设施端口，不要在生产环境使用示例数据库密码，也不要提交 RSA 私钥。
