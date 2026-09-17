# FlashTicket

FlashTicket is a work-in-progress microservices ticketing platform for concerts, events, and live performances. The project focuses on building a reliable foundation for high-demand ticket sales, including authentication, gateway routing, rate limiting, inventory control, ordering, payment, and electronic ticket delivery.

FlashTicket 是一个正在开发中的微服务票务平台，面向演唱会、活动及其他现场演出。项目目标是为高并发抢票场景建立可靠的系统基础，包括身份认证、网关路由、限流、库存管理、订单、支付和电子票。

## Current status / 当前状态

The repository currently contains:

- `api-gateway`: a Spring Cloud Gateway service running on port `8080`
- `auth-service`: a Spring Boot authentication service running on port `8081`
- Registration and login endpoints backed by MySQL and MyBatis
- BCrypt password hashing
- Redis-based gateway rate-limit configuration
- Docker Compose services for MySQL, Redis, Kafka, and ZooKeeper

目前已完成基础网关和认证服务。活动、座位库存、订单、支付、电子票及通知等业务服务仍处于规划阶段，尚未实现。认证接口目前会返回账户资料，但 JWT 签发逻辑仍未完成，因此 `accessToken` 为 `null`。

For the proposed architecture and development roadmap, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Tech stack / 技术栈

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Gateway | Spring Cloud Gateway 2025.1.3 |
| Security | Spring Security, OAuth2 Resource Server |
| Persistence | MySQL 8, MyBatis |
| Cache and rate limiting | Redis |
| Messaging infrastructure | Kafka, ZooKeeper |
| Build tool | Maven Wrapper |
| Local infrastructure | Docker Compose |

## Repository structure / 项目结构

```text
FlashTicket/
├── api-gateway/       # API gateway and rate-limit configuration
├── auth-service/      # Registration and login service
├── keys/              # RSA key files (private key is ignored by Git)
├── docker-compose.yml # Local infrastructure
└── DEVELOPMENT.md     # Architecture proposal and development roadmap
```

## Prerequisites / 环境要求

- Java 21
- Docker and Docker Compose
- OpenSSL (only required when generating local RSA keys)

Both Spring Boot modules include Maven Wrapper, so a separate Maven installation is not required.

## Local setup / 本地运行

### 1. Start the infrastructure / 启动基础设施

From the repository root:

```bash
docker compose up -d
```

The project uses the following local ports. Docker Compose starts MySQL, Redis, Kafka, and ZooKeeper; the Java services are started separately in the following steps.

| Service | Host port |
| --- | ---: |
| API Gateway (manual) | `8080` |
| Auth Service (manual) | `8081` |
| MySQL | `3307` |
| Redis | `6379` |
| Kafka | `9092` |

### 2. Create the authentication table / 创建认证数据表

Connect to the `flashticket_auth` database on MySQL port `3307`, then run:

```sql
CREATE TABLE IF NOT EXISTS auth_accounts (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL
);
```

The current local configuration uses username `root` and password `root`. These development credentials must be replaced with secrets before any production deployment.

### 3. Prepare RSA keys / 准备 RSA 密钥

If the files do not already exist locally, generate them from the repository root:

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -out keys/private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -pubout -in keys/private.pem -out keys/public.pem
```

Never commit `keys/private.pem`. It is already excluded by `.gitignore`.

### 4. Start the authentication service / 启动认证服务

```bash
cd auth-service
./mvnw spring-boot:run
```

The service is available at `http://localhost:8081`.

### 5. Start the API Gateway / 启动 API Gateway

Open another terminal:

```bash
cd api-gateway
./mvnw spring-boot:run
```

The gateway is available at `http://localhost:8080`. Its current route is an early development configuration and does not yet expose the authentication endpoints, so test authentication directly through port `8081` for now.

## Authentication API / 认证接口

### Register / 注册

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"change-me"}'
```

### Login / 登录

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"change-me"}'
```

Example response:

```json
{
  "id": "generated-uuid",
  "email": "user@example.com",
  "status": "ACTIVE",
  "accessToken": null
}
```

## Tests / 测试

Run each module's tests independently:

```bash
cd api-gateway
./mvnw test
```

```bash
cd auth-service
./mvnw test
```

The authentication tests require the configured MySQL service and local RSA key files to be available.

## Roadmap / 开发计划

- Complete JWT access-token generation and verification
- Route authentication requests through the API Gateway
- Add event and venue management
- Implement temporary seat holds and concurrency-safe inventory
- Add order, payment, ticket, and notification services
- Add automated database migrations, integration tests, and observability

Detailed service boundaries, workflows, security requirements, and implementation phases are documented in [DEVELOPMENT.md](DEVELOPMENT.md).

## Security note / 安全说明

The current configuration is intended for local development only. Do not reuse the sample database credentials in production, commit private keys, or expose internal infrastructure ports publicly.
