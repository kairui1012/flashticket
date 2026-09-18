# FlashTicket

FlashTicket is a work-in-progress microservices ticketing platform for concerts, events, and live performances. The current backend provides authentication, JWT authorization, ticket management, Redis caching, gateway routing, and per-user rate limiting.

FlashTicket 是一个正在开发中的微服务票务平台，面向演唱会、活动及其他现场演出。目前后端已经实现身份认证、JWT 权限控制、票种管理、Redis 缓存、网关路由和按用户限流。

## Current status / 当前状态

Implemented / 已实现：

- `api-gateway` on port `8080`
- `auth-service` on port `8081`
- `ticket-service` on port `8082`
- Registration and login with BCrypt password hashing
- RSA-signed JWT access tokens with `USER` and `ADMIN` roles
- JWT verification and role-based access control at the gateway
- Ticket creation, lookup, partial update, and cancellation
- Redis caching for individual tickets and event ticket lists
- Redis-backed per-user gateway rate limiting
- MySQL persistence through MyBatis
- Local MySQL, Redis, Kafka, and ZooKeeper infrastructure through Docker Compose

The `inventory-service` directory is currently an early skeleton. Inventory reservation, orders, payments, electronic ticket delivery, and notifications are not yet implemented.

`inventory-service` 目前仍是初始骨架。库存锁定、订单、支付、电子票交付和通知服务尚未完成。

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
| Messaging infrastructure | Kafka, ZooKeeper |
| Build tool | Maven Wrapper |
| Local infrastructure | Docker Compose |

## Repository structure / 项目结构

```text
FlashTicket/
├── api-gateway/       # Routing, JWT verification, authorization and rate limiting
├── auth-service/      # Registration, login and JWT generation
├── ticket-service/    # Ticket CRUD, validation and Redis caching
├── inventory-service/ # Early inventory-service skeleton
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

### 5. Start API Gateway / 启动 API Gateway

Open another terminal from the repository root:

```bash
cd api-gateway
./mvnw spring-boot:run
```

Clients should normally access Auth and Ticket APIs through `http://localhost:8080`.

客户端正常情况下应通过 `http://localhost:8080` 访问 Auth 和 Ticket API。

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

## Redis behavior / Redis 行为

Ticket Service caches the following query results for 10 minutes:

- `ticket:{ticketId}` for a single ticket
- `tickets:event:{eventId}` for an event's ticket list

Creating a ticket invalidates its event-list cache. Updating or cancelling a ticket refreshes its individual cache and invalidates the related event-list cache.

创建票种时会清除对应活动列表缓存；更新或取消票种时会刷新单票缓存，并清除对应活动列表缓存。

API Gateway also uses Redis to limit each authenticated user to a replenish rate of 5 Ticket requests per second with a burst capacity of 10.

API Gateway 还会通过 Redis 按已登录用户进行限流：每秒补充 5 个请求额度，最大突发容量为 10。

## Manual smoke check / 手动快速检查

After all three Java services are running:

1. Register a new account through the Gateway and confirm `201 Created`.
2. Log in with the same credentials and confirm `200 OK` with a non-empty `accessToken`.
3. Call a Ticket endpoint without a token and confirm `401 Unauthorized`.
4. Call `POST /api/v1/tickets` with a `USER` token and confirm `403 Forbidden`.
5. Promote the account to `ADMIN`, log in again, and create a ticket through the Gateway.
6. Query the created ticket twice and inspect Redis to confirm that a cache key with a TTL is present.

## Roadmap / 开发计划

- Complete inventory reservation and concurrency control
- Add event and venue management
- Add order and payment workflows
- Generate and validate electronic tickets
- Publish and consume domain events through Kafka
- Add notification delivery
- Add database migrations, integration tests, tracing, and production-ready secrets

- 完成库存锁定和并发控制
- 增加活动与场馆管理
- 增加订单和支付流程
- 生成和验证电子票
- 通过 Kafka 发布及消费领域事件
- 增加通知服务
- 增加数据库迁移、集成测试、链路追踪和生产级密钥管理

## Security note / 安全说明

The current configuration is intended for local development only. Do not expose the database or infrastructure ports publicly, reuse the sample database password in production, or commit private RSA keys.

当前配置仅适用于本地开发。请勿公开暴露数据库或基础设施端口，不要在生产环境使用示例数据库密码，也不要提交 RSA 私钥。
