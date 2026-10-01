# FlashTicket Local Deployment Guide

## 1. Purpose

This guide describes the verified local deployment used for the 2026-10-01 runtime acceptance test. It covers Docker infrastructure, IntelliJ IDEA run configurations, Stripe Test Mode, startup order, health checks, and shutdown.

For runtime evidence and the current acceptance result, see [FINAL_RUNTIME_TEST.md](./FINAL_RUNTIME_TEST.md).

## 2. Prerequisites

- JDK 21
- IntelliJ IDEA with Spring Boot support
- Docker Desktop with Docker Compose
- `curl` and `jq`
- Stripe CLI for payment and webhook verification

Verified local ports:

| Component | Port |
| --- | ---: |
| API Gateway | `8080` |
| Auth Service | `8081` |
| Ticket Service | `8082` |
| Inventory Service | `8083` |
| Order Service | `8084` |
| Payment Service | `8085` |
| MySQL | `3307` |
| Redis | `6379` |
| Kafka | `9092` |

## 3. Start Infrastructure

Run from the repository root:

```bash
docker compose up -d
docker compose ps
```

Verify Redis and Kafka:

```bash
docker exec flashticket-redis redis-cli PING

docker exec flashticket-kafka \
  kafka-topics --bootstrap-server localhost:9092 --list
```

Expected Redis result: `PONG`.

The following databases are created by the repository's Docker initialization script:

```text
flashticket_auths
flashticket_ticket
flashticket_inventory
flashticket_order
flashticket_payment
```

## 4. IntelliJ IDEA Run Configurations

Open the repository root as one IntelliJ project:

```text
/path/to/FlashTicket
```

For each service, go to:

```text
Run -> Edit Configurations -> + -> Spring Boot
```

Use these common settings:

```text
JRE: Java 21
Working directory: $PROJECT_DIR$
Active profiles: leave empty
Program arguments: leave empty
VM options: leave empty
```

Create the following six configurations:

| Configuration name | Main class | Module classpath |
| --- | --- | --- |
| `AuthServiceApplication` | `com.flashticket.authservice.AuthServiceApplication` | `auth-service` |
| `TicketServiceApplication` | `com.flashticket.ticketservice.TicketServiceApplication` | `ticket-service` |
| `InventoryServiceApplication` | `com.flashticket.inventoryservice.InventoryServiceApplication` | `inventory-service` |
| `OrderServiceApplication` | `com.flashticket.orderservice.OrderServiceApplication` | `order-service` |
| `PaymentServiceApplication` | `com.flashticket.paymentservice.PaymentServiceApplication` | `payment-service` |
| `ApiGatewayApplication` | `com.flashticket.apigateway.ApiGatewayApplication` | `api-gateway` |

### 4.1 Auth Service environment variables

Paste this into the IntelliJ environment-variable editor:

```text
JWT_PRIVATE_KEY=file:keys/private.pem;JWT_PUBLIC_KEY=file:keys/public.pem
```

The default local database configuration already points to `flashticket_auths` on MySQL port `3307`. When overriding it, use:

```text
AUTH_DB_URL=jdbc:mysql://localhost:3307/flashticket_auths?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kuala_Lumpur
AUTH_DB_USERNAME=root
AUTH_DB_PASSWORD=<local-password>
```

### 4.2 API Gateway environment variables

```text
JWT_PUBLIC_KEY=file:keys/public.pem
```

The Gateway does not need the private key.

### 4.3 Ticket, Inventory, and Order environment variables

No environment variables are required for the verified local Docker setup. Their `application.yaml` defaults use local MySQL, Redis, Kafka, and service ports.

Optional overrides:

```text
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
TICKET_SERVICE_BASE_URL=http://localhost:8082
INVENTORY_SERVICE_BASE_URL=http://localhost:8083
PAYMENT_SERVICE_BASE_URL=http://localhost:8085
```

### 4.4 Payment Service and Stripe environment variables

Do not commit real Stripe values. Paste the following template into the IntelliJ environment-variable editor, then fill only the two empty values:

```text
STRIPE_SECRET_KEY=;STRIPE_WEBHOOK_SECRET=;STRIPE_CURRENCY=myr;STRIPE_SUCCESS_URL=http://localhost:3000/payment/success?session_id={CHECKOUT_SESSION_ID};STRIPE_CANCEL_URL=http://localhost:3000/payment/cancel
```

Required value prefixes in Stripe Test Mode:

```text
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Important IntelliJ input rules:

- Enter five separate environment variables, not one long value assigned to `STRIPE_SECRET_KEY`.
- Do not wrap URLs in quotes.
- Do not add `[` or `]` around a URL.
- Keep `{CHECKOUT_SESSION_ID}` exactly as shown.
- Restart Payment Service after changing any environment variable.

## 5. Stripe CLI Webhook Forwarding

Authenticate the Stripe CLI once if required:

```bash
stripe login
```

Start the listener in a separate terminal:

```bash
stripe listen \
  --events checkout.session.completed \
  --forward-to http://localhost:8080/api/v1/payments/webhooks/stripe
```

The listener prints a signing secret beginning with `whsec_`. Copy it into `STRIPE_WEBHOOK_SECRET` in the Payment IntelliJ configuration, then restart Payment Service.

When a new `stripe listen` session prints a different signing secret, update the IntelliJ value and restart Payment Service again.

Keep this listener running while testing Checkout. A Checkout Session returning `201` proves session creation only; the payment flow is complete only after the signed webhook changes Payment to `SUCCEEDED` and Order to `PAID`.

## 6. Startup Order

Recommended order:

1. Docker infrastructure
2. Auth Service
3. Ticket Service
4. Inventory Service
5. Order Service
6. Stripe CLI listener
7. Payment Service
8. API Gateway

Kafka consumers may start before their topics or producers; the order above is chosen to make startup logs and troubleshooting easier.

After changing Java source, stop and rerun the affected IntelliJ configuration. A successful Maven build does not reload an already-running JVM.

## 7. Build Verification

Run from the repository root:

```bash
for service in \
  api-gateway \
  auth-service \
  ticket-service \
  inventory-service \
  order-service \
  payment-service
do
  (cd "$service" && ./mvnw clean package -DskipTests)
done
```

## 8. Runtime Verification

### 8.1 Listener check

```bash
for port in 8080 8081 8082 8083 8084 8085; do
  lsof -nP -iTCP:${port} -sTCP:LISTEN
done
```

### 8.2 Direct service health

```bash
for port in 8082 8083 8084 8085; do
  curl -sS "http://localhost:${port}/actuator/health"
done
```

Expected result: HTTP `200` with `status=UP`.

An unauthenticated Gateway health request can return `401`; this does not mean port `8080` is down. Verify the listener or call it with a valid JWT.

### 8.3 Kafka consumer convergence

```bash
docker exec flashticket-kafka \
  kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --all-groups --describe
```

Relevant topics:

```text
inventory.reserved
inventory.release
order.created
order.terminated
payment.succeeded
```

For completed test traffic, the relevant consumer lag should converge to `0`.

## 9. Verified Runtime Status

Verified on 2026-10-01 through the API Gateway with MySQL and Kafka cross-checks:

- A normal `USER` can reserve inventory through the Gateway and receives HTTP `200`.
- Reservation creates one `PENDING_PAYMENT` Order and one `PENDING` Payment.
- Stripe Checkout Session creation returns HTTP `201` with a valid Test Mode URL.
- Cancelling an Order changes its Payment to `CANCELLED` with `ORDER_CANCELLED`.
- Automatic five-minute expiration changes its Payment to `EXPIRED` with `ORDER_EXPIRED`.
- `order.terminated` consumer lag converges to `0`.

Still requiring manual confirmation:

- Submit one Stripe Test Mode payment on the hosted Checkout page.
- Confirm Payment=`SUCCEEDED`, Order=`PAID`, and `payment.succeeded` lag=`0`.

Until that manual payment succeeds, describe the deployment as `CONDITIONALLY READY`, not full payment-closure `READY`.

## 10. Troubleshooting

### Port already in use

```bash
lsof -nP -iTCP:8085 -sTCP:LISTEN
kill -TERM <exact-pid>
```

Resolve the exact PID first. Do not use broad Java process termination commands.

### Source changed but behavior did not

Check the process start time:

```bash
ps -axo pid,lstart,command | \
  rg 'ApiGatewayApplication|OrderServiceApplication|PaymentServiceApplication'
```

Rebuild and rerun the affected IntelliJ configuration. A process started before the source change is still running the old bytecode.

### Stripe Checkout returns `503`

Confirm that Payment Service was restarted with both required variables and that their prefixes are correct. Do not print full secret values in logs or screenshots.

### Stripe Checkout returns `502`

Check `STRIPE_SUCCESS_URL` and `STRIPE_CANCEL_URL` for extra quotes, brackets, or malformed text. The success URL must be exactly:

```text
http://localhost:3000/payment/success?session_id={CHECKOUT_SESSION_ID}
```

### USER reserve returns `403`

Confirm that Gateway was restarted after the security-rule change. The source allows:

```text
POST /api/v1/inventory/{ticketId}/reserve -> USER or ADMIN
```

Other Inventory write endpoints remain ADMIN-only.

## 11. Secret Handling

- Never commit Stripe keys, webhook secrets, JWTs, passwords, or test-account credentials.
- Keep local IntelliJ environment values in the untracked workspace configuration.
- Do not paste secret values into Markdown, screenshots, issues, or pull requests.
- Rotate a Stripe key if it has been exposed outside the intended local environment.

## 12. Shutdown

Stop application configurations from IntelliJ, stop the Stripe listener with `Ctrl+C`, then stop infrastructure:

```bash
docker compose stop
```

Use `docker compose down` only when you intentionally want to remove the Compose containers and network. Do not add `-v` unless deleting local database volumes is explicitly intended.
