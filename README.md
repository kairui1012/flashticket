# FlashTicket

FlashTicket is a microservices backend for high-concurrency ticket sales. It uses Redis Lua scripts for atomic inventory operations, Kafka for asynchronous workflows, MySQL for durable state, and an API Gateway for routing, authentication, and rate limiting.

The project demonstrates the complete path from stock reservation to order creation, payment initialization, Stripe Checkout, payment confirmation, and inventory compensation.

> **Project status:** Active development. The system is suitable for local development and architecture demonstrations, but is not production-ready.

> **Payment scope:** Stripe Test Mode only. A payment success received after an order becomes `EXPIRED` or `CANCELLED` is recorded as processed, logged as `LATE_PAYMENT_IGNORED`, and acknowledged without retry. Automated refunds and real-money payment reconciliation are Future Work.

## Key Capabilities

- BCrypt password hashing and RSA-signed JWT authentication
- Role-based access control for `USER` and `ADMIN` accounts
- API Gateway routing, authorization, and Redis-backed rate limiting
- Ticket management with Redis caching
- Atomic inventory reservation and release through Redis Lua scripts
- Kafka-driven inventory synchronization and order creation
- Idempotent event consumption backed by MySQL
- Five-minute payment window for pending orders
- Durable, retryable inventory release tasks
- Transactional Order Outbox for reliable `order.created` publication
- Automatic creation of one pending payment per order
- Stripe-hosted Checkout Session creation and signature-verified webhooks
- Idempotent `payment.succeeded` handling and conditional order completion

## System Architecture

```text
Client
  |
  v
API Gateway :8080
  |-- Auth Service :8081 ---------> MySQL
  |-- Ticket Service :8082 -------> MySQL + Redis
  |-- Inventory Service :8083
  |      |-- Redis Lua reservation and release
  |      `-- inventory.reserved --> Kafka
  |               |-- Inventory Consumer --> Inventory MySQL
  |               `-- Order Consumer ------> Order Service :8084
  |                                               |-- Order MySQL
  |                                               `-- Order Outbox
  |                                                       |
  |                                                order.created
  |                                                       |
  `-- Payment Service :8085 <---------------- Payment Consumer
           |-- Payment MySQL
           |-- Redis payment cache
           `-- Stripe Checkout
                    |
              Signed webhook
                    |
             payment.succeeded --> Kafka --> Order Service --> PAID

Order cancellation or expiry
  --> durable release task
  --> Inventory release API
  --> Redis and MySQL stock recovery
```

## Service Catalog

| Service | Port | Responsibility |
| --- | ---: | --- |
| API Gateway | `8080` | Routes all public APIs, validates JWTs, and applies rate limits |
| Auth Service | `8081` | Registration, login, password hashing, and JWT issuance |
| Ticket Service | `8082` | Ticket lifecycle, sale status, and read caching |
| Inventory Service | `8083` | Stock management, atomic reservation, release, and inventory events |
| Order Service | `8084` | Order lifecycle, expiry, release tasks, Outbox publication, and payment-result consumption |
| Payment Service | `8085` | Payment creation, caching, Stripe Checkout, webhook processing, and success events |

Infrastructure is provided locally through Docker Compose:

| Component | Port |
| --- | ---: |
| MySQL | `3307` |
| Redis | `6379` |
| Kafka | `9092` |
| ZooKeeper | `2181` |

## Core Workflows

### Reservation and Order Creation

1. Inventory Service validates the ticket and sale window through Ticket Service.
2. A Redis Lua script checks stock and duplicate user reservations atomically.
3. Redis available stock is reduced and a reservation key is created.
4. Inventory Service publishes `inventory.reserved`.
5. Inventory Consumer idempotently moves MySQL stock from available to reserved.
6. Order Consumer creates a `PENDING_PAYMENT` order with a five-minute deadline.
7. The same database transaction creates a pending Order Outbox record.

### Payment

1. The Outbox worker publishes `order.created` and marks the record as `PUBLISHED` after Kafka acknowledgment.
2. Payment Consumer creates one `PENDING` payment for the order.
3. Payment Service caches the payment by payment ID and order ID for five minutes.
4. The client requests a Stripe-hosted Checkout Session.
5. Payment Service verifies Stripe webhook signatures and accepts paid `checkout.session.completed` events.
6. A conditional MySQL update changes the payment from `PENDING` to `SUCCEEDED`.
7. Payment Service publishes `payment.succeeded`.
8. Order Service consumes the event idempotently and changes an eligible order to `PAID`.

The order amount, status, and expiry are read from Order Service and are not trusted from the client request.

### Cancellation and Expiry

1. A user cancels an order, or the scheduler expires an unpaid order using `orders.expires_at`.
2. Order Service conditionally changes the order from `PENDING_PAYMENT` to `CANCELLED` or `EXPIRED`.
3. A durable `inventory_release_tasks` record is created with a stable `releaseId`.
4. The release worker calls Inventory Service.
5. Redis and MySQL idempotency controls ensure that duplicate release requests restore stock only once.
6. Temporary failures move the task to `RETRY`; successful retries move it to `SUCCESS`.

The 15-minute Redis reservation TTL is a safety buffer. The five-minute `orders.expires_at` value is the business deadline.

## Technology Stack

| Area | Technology |
| --- | --- |
| Runtime | Java 21, Spring Boot 4.1.1 |
| Gateway | Spring Cloud Gateway |
| Security | Spring Security, OAuth2 Resource Server, RSA JWT |
| Persistence | MySQL 8, MyBatis, Flyway |
| Cache and concurrency | Redis, Lua |
| Messaging | Apache Kafka, ZooKeeper |
| Payments | Stripe Java SDK, Stripe Checkout, Stripe webhooks |
| Build and infrastructure | Maven Wrapper, Docker Compose |
| Load testing | Apache JMeter 5.6.3 |

## Local Development

### Prerequisites

- Java 21
- Docker and Docker Compose
- OpenSSL
- Stripe CLI for local webhook testing, if required

### 1. Start Infrastructure

```bash
docker compose up -d
docker compose ps
```

### 2. Generate Local RSA Keys

```bash
mkdir -p keys
openssl genpkey -algorithm RSA \
  -out keys/private.pem \
  -pkeyopt rsa_keygen_bits:2048
openssl rsa \
  -pubout \
  -in keys/private.pem \
  -out keys/public.pem
```

Do not commit private keys or use local credentials in a production environment.

### 3. Configure Stripe

Payment Service reads the following environment variables:

| Variable | Required | Description |
| --- | --- | --- |
| `STRIPE_SECRET_KEY` | For Checkout | Stripe secret API key |
| `STRIPE_WEBHOOK_SECRET` | For webhooks | Signing secret used to verify Stripe webhook payloads |
| `STRIPE_CURRENCY` | No | Payment currency; defaults to `myr` |
| `STRIPE_SUCCESS_URL` | No | Browser redirect after successful checkout |
| `STRIPE_CANCEL_URL` | No | Browser redirect after checkout cancellation |

Keep all Stripe secrets outside source control.

### 4. Start the Services

Run each service in a separate terminal:

```bash
cd auth-service
JWT_PRIVATE_KEY=file:../keys/private.pem \
JWT_PUBLIC_KEY=file:../keys/public.pem \
./mvnw spring-boot:run
```

```bash
cd ticket-service && ./mvnw spring-boot:run
cd inventory-service && ./mvnw spring-boot:run
cd order-service && ./mvnw spring-boot:run
cd payment-service && ./mvnw spring-boot:run
cd api-gateway && ./mvnw spring-boot:run
```

### 5. Check Service Health

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
```

## API Summary

All public routes are available through the API Gateway at `http://localhost:8080`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Register an account |
| `POST` | `/api/v1/auth/login` | Authenticate and issue a JWT |
| `GET` | `/api/v1/tickets/{ticketId}` | Get a ticket |
| `POST` | `/api/v1/tickets` | Create a ticket |
| `GET` | `/api/v1/inventory/{ticketId}` | Get inventory |
| `POST` | `/api/v1/inventory/{ticketId}/reserve` | Reserve stock |
| `POST` | `/api/v1/inventory/release` | Release reserved stock idempotently |
| `GET` | `/api/v1/orders/{orderId}` | Get an order |
| `GET` | `/api/v1/orders/user/{userId}` | List orders for a user |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Cancel a pending order |
| `POST` | `/api/v1/payments` | Create or retrieve the payment for an order |
| `GET` | `/api/v1/payments/{paymentId}` | Get a payment by ID |
| `GET` | `/api/v1/payments/order/{orderId}` | Get a payment by order ID |
| `POST` | `/api/v1/payments/{paymentId}/checkout-session` | Create a Stripe Checkout Session |
| `POST` | `/api/v1/payments/webhooks/stripe` | Receive a signed Stripe webhook |

Under the normal event-driven flow, `order.created` creates the payment automatically. The manual payment creation endpoint remains idempotent for recovery and local diagnostics.

## Verification

### Build Verification

On 2026-10-01, the following modules compiled successfully with `./mvnw -DskipTests compile`:

- API Gateway
- Order Service
- Payment Service

Compilation confirms source compatibility only; it does not replace runtime or integration testing.

### Runtime Validation

The following scenarios were validated against running services with real HTTP requests, MySQL, Redis, and Kafka:

| Date | Scenario | Result |
| --- | --- | --- |
| 2026-09-27 | Normal reservation | Redis stock decreased, the reservation key existed, MySQL reserved stock increased, and a `PENDING_PAYMENT` order was created |
| 2026-09-27 | User cancellation | Order became `CANCELLED`, the release task reached `SUCCESS`, and Redis/MySQL stock was restored |
| 2026-09-27 | Five-minute expiry | Order became `EXPIRED`, the release task reached `SUCCESS`, and stock was restored |
| 2026-09-27 | Duplicate release | Two requests with the same `releaseId` were safe; Redis and MySQL released stock once |
| 2026-09-27 | Inventory outage | The release task entered `RETRY`, then reached `SUCCESS` after Inventory Service recovered |
| 2026-09-29 | Order Outbox and payment creation | Outbox moved to `PUBLISHED`, Kafka consumer lag reached zero, one `PENDING` payment was inserted, and both payment cache keys were created |

The Stripe Checkout and `payment.succeeded` path is implemented and compiles, but is not included in the runtime evidence above.

## Performance Test

JMeter called Inventory Service directly with 200 threads, a five-second ramp-up, 50 iterations per thread, and 1,000 units of initial stock.

| Metric | Result |
| --- | ---: |
| Requests | 10,000 |
| Average response time | 16 ms |
| Maximum response time | 609 ms |
| Throughput | 1,905.85 requests/second |
| Successful reservations | 1,000 |
| Expected sold-out conflicts | 9,000 |

The reported 90% JMeter error rate represents expected sold-out conflicts after the available stock was exhausted. This benchmark covers direct Inventory Service calls and does not represent API Gateway or full end-to-end capacity.

![JMeter aggregate report](./jmeter/image.png)

![JMeter response summary](./jmeter/image_2.png)

## Known Limitations

- Stripe integration is restricted to Test Mode; real-money refunds and reconciliation are not implemented.
- The Stripe Checkout and webhook workflow still requires end-to-end runtime validation.
- An expired order can leave its existing payment record in `PENDING`; expiry-to-payment cancellation is not implemented.
- `order.created` uses the Outbox pattern, but not every cross-service state change has equivalent durable publication.
- Administrative recovery, alerting, and reconciliation for `DEAD` release tasks are not implemented.
- Ticket sold-out synchronization, notifications, and ticket issuance are outside the current completed scope.
- Local infrastructure uses development credentials and does not include production hardening, observability, or deployment automation.

## Additional Documentation

See [DEVELOPMENT.md](./DEVELOPMENT.md) for lower-level implementation notes and historical development details. Where it differs from this README, the current source code and this README take precedence.
