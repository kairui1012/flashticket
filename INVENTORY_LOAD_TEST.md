# Inventory Service concurrency load test

This document keeps a repeatable real-runtime test for the Inventory Service. It simulates `1000` unique users competing for `100` tickets and verifies that exactly `100` reservations succeed without negative stock or overselling.

本文保存一套可重复执行的 Inventory Service 真实并发压测：使用 `1000` 个不同用户同时抢 `100` 张票，并验证成功数严格等于 `100`，Redis 和 MySQL 均不存在负库存或超卖。

## What this test proves / 验证目标

- `1000` unique users each request one ticket.
- The client concurrency level is `200`.
- Exactly `100` requests must return `200 OK`.
- Exactly `900` requests must return `409 Conflict`.
- Redis must finish with available stock `0` and exactly `100` reservation keys.
- MySQL must finish with `total_stock=100`, `available_stock=0`, and `reserved_stock=100`.
- The number of new `INVENTORY_RESERVED` processed events must be exactly `100`.
- No HTTP `5xx`, negative inventory, or success count above `100` is accepted.

This test calls Inventory Service directly on port `8083` to isolate inventory concurrency from Gateway authentication and rate limiting. Run it in an isolated local development environment with only one Inventory Service consumer using the `inventory-service` Kafka group.

本测试直接访问 `8083`，避免 Gateway 鉴权和限流影响库存并发结果。请在隔离的本地开发环境执行，并确保只有一个使用 `inventory-service` Kafka Consumer Group 的 Inventory Service 实例。

> Important: the current five-minute reservation timeout does not automatically return stock. Complete this test and collect the results within five minutes.

> 注意：当前五分钟预留过期后不会自动归还库存。请在五分钟内完成压测并收集结果。

## 1. Start dependencies / 启动依赖

From the repository root:

```bash
docker compose up -d mysql redis zookeeper kafka
docker compose ps
```

Start Ticket Service in one terminal:

```bash
cd ticket-service
./mvnw spring-boot:run
```

Start Inventory Service in another terminal:

```bash
cd inventory-service
./mvnw spring-boot:run
```

Confirm both services are healthy:

```bash
curl --fail --silent http://localhost:8082/actuator/health
curl --fail --silent http://localhost:8083/actuator/health
```

## 2. Prepare an active ticket and inventory / 准备票种与库存

Run the remaining commands from the repository root in the same terminal so the variables remain available:

```bash
LOAD_RUN_ID=$(date +%s)
LOAD_EVENT_ID="load-event-$LOAD_RUN_ID"
LOAD_DIR=$(mktemp -d /tmp/flashticket-inventory-load.XXXXXX)
LOAD_STARTED_AT=$(date '+%Y-%m-%d %H:%M:%S')

if SALE_START=$(date -v-1M '+%Y-%m-%dT%H:%M:%S' 2>/dev/null); then
  SALE_END=$(date -v+1H '+%Y-%m-%dT%H:%M:%S')
else
  SALE_START=$(date -d '1 minute ago' '+%Y-%m-%dT%H:%M:%S')
  SALE_END=$(date -d '1 hour' '+%Y-%m-%dT%H:%M:%S')
fi

curl --fail --silent --show-error \
  -X POST http://localhost:8082/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$LOAD_EVENT_ID\",\"name\":\"Inventory load test\",\"description\":\"Temporary 100-ticket concurrency test\",\"price\":10.00,\"totalStock\":100,\"saleStartTime\":\"$SALE_START\",\"saleEndTime\":\"$SALE_END\"}" \
  > "$LOAD_DIR/ticket-response.json"

LOAD_TICKET_ID=$(jq -r '.id' "$LOAD_DIR/ticket-response.json")

if [ -z "$LOAD_TICKET_ID" ] || [ "$LOAD_TICKET_ID" = "null" ]; then
  echo 'Unable to read ticket ID from Ticket Service response'
  exit 1
fi

printf '%s\n' "$LOAD_TICKET_ID" > "$LOAD_DIR/ticket-id.txt"
printf '%s\n' "$LOAD_STARTED_AT" > "$LOAD_DIR/started-at.txt"
export LOAD_TICKET_ID LOAD_DIR

curl --fail --silent --show-error \
  -X POST http://localhost:8083/api/v1/inventory/insert \
  -H 'Content-Type: application/json' \
  -d "{\"ticketId\":\"$LOAD_TICKET_ID\",\"totalStock\":100}" \
  | jq .

EVENT_COUNT_BEFORE=$(docker exec flashticket-mysql \
  mysql -uroot -proot -N -e \
  "SELECT COUNT(*) FROM flashticket_inventory.processed_events
   WHERE event_type='INVENTORY_RESERVED';")
```

Expected initial Redis stock:

```bash
redis-cli GET "inventory:stock:$LOAD_TICKET_ID"
```

Expected output: `100`.

## 3. Send 1000 concurrent-user requests / 发送 1000 用户并发请求

The following command uses `200` concurrent workers. Each worker has a unique user ID and stores its HTTP status and response body under `$LOAD_DIR`.

以下命令使用 `200` 个并发 Worker。每个请求拥有独立的用户 ID，并将 HTTP 状态码及响应内容保存至 `$LOAD_DIR`。

```bash
LOAD_BEGIN_SECONDS=$(date +%s)

seq 1 1000 | xargs -P 200 -I {} sh -c '
  user_id="load-user-$1"
  http_code=$(curl --silent --show-error \
    --output "${LOAD_DIR}/body-$1.json" \
    --write-out "%{http_code}" \
    -X POST http://localhost:8083/api/v1/inventory/reserve \
    -H "Content-Type: application/json" \
    -d "{\"ticketId\":\"${LOAD_TICKET_ID}\",\"userId\":\"${user_id}\",\"reservedStock\":1}")
  printf "%s\n" "$http_code" > "${LOAD_DIR}/status-$1.txt"
' _ {}

LOAD_END_SECONDS=$(date +%s)
LOAD_DURATION_SECONDS=$((LOAD_END_SECONDS - LOAD_BEGIN_SECONDS))
```

## 4. Wait for Kafka/MySQL convergence / 等待 Kafka 与 MySQL 同步

```bash
for attempt in $(seq 1 60); do
  DB_RESERVED=$(docker exec flashticket-mysql \
    mysql -uroot -proot -N -e \
    "SELECT reserved_stock FROM flashticket_inventory.inventories
     WHERE ticket_id='$LOAD_TICKET_ID';")

  if [ "$DB_RESERVED" = "100" ]; then
    break
  fi

  sleep 1
done
```

## 5. Collect and assert results / 收集并判断结果

```bash
SUCCESS_COUNT=$(awk '$1 == "200" { count++ } END { print count + 0 }' "$LOAD_DIR"/status-*.txt)
CONFLICT_COUNT=$(awk '$1 == "409" { count++ } END { print count + 0 }' "$LOAD_DIR"/status-*.txt)
OTHER_COUNT=$((1000 - SUCCESS_COUNT - CONFLICT_COUNT))

REDIS_STOCK=$(redis-cli GET "inventory:stock:$LOAD_TICKET_ID")
RESERVATION_COUNT=$(redis-cli --scan \
  --pattern "inventory:reserved:$LOAD_TICKET_ID:*" | wc -l | tr -d ' ')

DB_STATE=$(docker exec flashticket-mysql \
  mysql -uroot -proot -N -e \
  "SELECT CONCAT(total_stock,',',available_stock,',',reserved_stock)
   FROM flashticket_inventory.inventories
   WHERE ticket_id='$LOAD_TICKET_ID';")

EVENT_COUNT_AFTER=$(docker exec flashticket-mysql \
  mysql -uroot -proot -N -e \
  "SELECT COUNT(*) FROM flashticket_inventory.processed_events
   WHERE event_type='INVENTORY_RESERVED';")
EVENT_DELTA=$((EVENT_COUNT_AFTER - EVENT_COUNT_BEFORE))

printf 'duration_seconds=%s\n' "$LOAD_DURATION_SECONDS"
printf 'success_200=%s\n' "$SUCCESS_COUNT"
printf 'conflict_409=%s\n' "$CONFLICT_COUNT"
printf 'other_status=%s\n' "$OTHER_COUNT"
printf 'redis_available_stock=%s\n' "$REDIS_STOCK"
printf 'redis_reservation_keys=%s\n' "$RESERVATION_COUNT"
printf 'mysql_total_available_reserved=%s\n' "$DB_STATE"
printf 'new_inventory_reserved_events=%s\n' "$EVENT_DELTA"

if [ "$SUCCESS_COUNT" -eq 100 ] \
  && [ "$CONFLICT_COUNT" -eq 900 ] \
  && [ "$OTHER_COUNT" -eq 0 ] \
  && [ "$REDIS_STOCK" = "0" ] \
  && [ "$RESERVATION_COUNT" -eq 100 ] \
  && [ "$DB_STATE" = "100,0,100" ] \
  && [ "$EVENT_DELTA" -eq 100 ]; then
  echo 'PASS: no overselling or negative stock detected'
else
  echo 'FAIL: inspect status files, Redis, MySQL, and Inventory Service logs'
fi
```

Expected result:

```text
success_200=100
conflict_409=900
other_status=0
redis_available_stock=0
redis_reservation_keys=100
mysql_total_available_reserved=100,0,100
new_inventory_reserved_events=100
PASS: no overselling or negative stock detected
```

## Latest verified result / 最近一次实测结果

This script was executed locally on September 23, 2026. Ticket Service and Inventory Service used isolated ports `18082` and `18083` for this run; the request count and concurrency were unchanged.

本脚本已于 2026 年 9 月 23 日在本机执行。该次测试使用隔离端口 `18082` 和 `18083`，请求总数与并发参数保持不变。

| Metric | Observed result |
| --- | ---: |
| Total requests | `1000` |
| Client concurrency | `200` |
| Duration | `8 seconds` |
| Successful reservations (`200`) | `100` |
| Rejected reservations (`409`) | `900` |
| Other HTTP statuses | `0` |
| Redis available stock | `0` |
| Redis reservation keys | `100` |
| MySQL total / available / reserved | `100 / 0 / 100` |
| New processed reservation events | `100` |

Result: **PASS** — no overselling and no negative stock were observed.

结果：**通过**——未发现超卖或负库存。

### 1000-ticket / 10000-user timed run

A larger timed run was executed on September 23, 2026 with `1000` tickets, `10000` unique users, and client concurrency `500`. Each `curl` process recorded its own `time_total`; percentiles below are calculated from all `10000` client-observed request times.

2026 年 9 月 23 日进一步执行了 `1000` 张票、`10000` 个不同用户、客户端并发 `500` 的计时压测。每个 `curl` 进程都单独记录 `time_total`，以下延迟百分位由全部 `10000` 条客户端实测数据计算。

| Metric | Observed result |
| --- | ---: |
| Wall-clock duration | `97.568 seconds` |
| Throughput | `102.49 requests/second` |
| Average latency | `0.027099 seconds` (`27.099 ms`) |
| P50 latency | `0.017289 seconds` (`17.289 ms`) |
| P95 latency | `0.084921 seconds` (`84.921 ms`) |
| P99 latency | `0.149477 seconds` (`149.477 ms`) |
| Maximum latency | `0.475436 seconds` (`475.436 ms`) |
| Successful reservations (`200`) | `1000` |
| Rejected reservations (`409`) | `9000` |
| Other HTTP statuses / curl errors | `0 / 0` |
| Redis available stock / reservation keys | `0 / 1000` |
| MySQL total / available / reserved | `1000 / 0 / 1000` |
| New processed reservation events | `1000` |

Successful `200` requests averaged `84.436 ms`, with P95 `167.735 ms` and P99 `197.504 ms`. Rejected `409` requests averaged `20.728 ms`, with P95 `45.099 ms` and P99 `79.090 ms`. The successful path is slower because it publishes an event and synchronously waits for Kafka acknowledgement.

`200` 成功请求的平均延迟为 `84.436 ms`，P95 为 `167.735 ms`，P99 为 `197.504 ms`。`409` 拒绝请求平均为 `20.728 ms`，P95 为 `45.099 ms`，P99 为 `79.090 ms`。成功路径较慢，是因为它会发布事件并同步等待 Kafka 确认。

The wall-clock throughput is affected heavily by the shell-based load generator: `xargs` creates `10000` shell and `curl` processes and writes separate result files. Both services also had DEBUG logging enabled. Therefore, `102.49 RPS` is the throughput of this complete local test setup, not a reliable measurement of the Inventory Service's maximum capacity. Use a persistent-connection load generator such as k6, Gatling, or wrk for a capacity benchmark.

总吞吐量明显受到 Shell 压测器影响：`xargs` 会创建 `10000` 个 Shell 和 `curl` 进程并分别写入结果文件，同时两个服务均开启了 DEBUG 日志。因此，`102.49 RPS` 表示这套本地完整测试流程的吞吐量，不能视为 Inventory Service 的最大容量。若要测容量上限，应使用 k6、Gatling 或 wrk 等支持持久连接的压测工具。

Result: **PASS** — exactly `1000` reservations succeeded, with no overselling, negative stock, timeout, or HTTP `5xx` response.

结果：**通过**——成功预留数严格为 `1000`，没有超卖、负库存、超时或 HTTP `5xx`。

### Rerun after asynchronous Kafka change / 异步 Kafka 修改后复测

The same `1000`-ticket, `10000`-user, concurrency-`500` scenario was rerun after changing reservation publishing from blocking `KafkaTemplate.send(...).get()` to an asynchronous completion callback and lowering Inventory Service logging. The malformed empty Actuator endpoint entry in `application.yaml` first had to be removed because it prevented the service from starting.

库存事件由阻塞式 `KafkaTemplate.send(...).get()` 改为异步完成回调，并降低 Inventory Service 日志级别后，再次使用完全相同的 `1000` 张票、`10000` 用户、`500` 并发场景复测。开始测试前先删除了 `application.yaml` 中错误的空 Actuator Endpoint 配置，因为该配置会导致服务无法启动。

| Metric | Blocking baseline | Asynchronous rerun | Change |
| --- | ---: | ---: | ---: |
| Wall-clock duration | `97.568 s` | `117.242 s` | `+20.2%` |
| Throughput | `102.49 RPS` | `85.29 RPS` | `-16.8%` |
| Average latency | `27.099 ms` | `30.915 ms` | `+14.1%` |
| P95 latency | `84.921 ms` | `86.686 ms` | `+2.1%` |
| P99 latency | `149.477 ms` | `241.384 ms` | `+61.5%` |
| Maximum latency | `475.436 ms` | `621.630 ms` | — |
| Successful / rejected | `1000 / 9000` | `1000 / 9000` | unchanged |
| MySQL convergence after HTTP completion | not recorded | `0.156 s` | — |

The asynchronous rerun still passed every correctness assertion: no curl errors or unexpected statuses, Redis finished at `0` with `1000` reservation keys, MySQL finished at `1000 / 0 / 1000`, and exactly `1000` events were processed.

异步版本仍通过全部正确性断言：没有 curl 错误或异常 HTTP 状态，Redis 最终库存为 `0` 并保留 `1000` 个预留 Key，MySQL 为 `1000 / 0 / 1000`，处理事件数严格为 `1000`。

One local run is not enough to attribute the regression to the code change. The shell load generator does not sustain `500` active connections: it repeatedly spawns `sh` and `curl`, writes thousands of files, and is sensitive to local CPU and Docker contention. Each request also still performs a synchronous Feign Ticket lookup; `KafkaTemplate.send()` still serializes and enqueues the event even without `.get()`; Ticket Service DEBUG logging remains active; and Producer, Consumer, Redis, MySQL, and both Java services share one machine. Use a warmed-up k6 or Gatling test with multiple iterations before treating the difference as a code-level regression.

单次本机测试不足以证明性能下降由代码修改导致。当前 Shell 压测器无法持续维持 `500` 个活跃连接：它需要反复创建 `sh` 和 `curl`、写入数千个文件，并容易受到本机 CPU 与 Docker 资源竞争影响。每个请求仍会同步执行 Feign Ticket 查询；即使删除 `.get()`，`KafkaTemplate.send()` 仍需序列化并写入 Producer Buffer；Ticket Service 仍开启 DEBUG 日志；Producer、Consumer、Redis、MySQL 和两个 Java 服务也共享同一台机器。应使用预热后的 k6 或 Gatling 连续运行多轮，才能判断是否属于代码级性能回退。

After stock reached zero, the temporary Ticket still had status `DRAFT`; automatic transition to `SOLD_OUT` is not implemented yet. A future sold-out event may optimize rejection and close sales, but Redis atomic stock validation must remain to protect the asynchronous transition window.

库存归零后，临时 Ticket 的状态仍然是 `DRAFT`，当前尚未实现自动转换为 `SOLD_OUT`。后续可以通过售罄事件自动关闭销售并加快拒绝请求，但仍必须保留 Redis 原子库存校验，以保护异步状态更新的时间窗口。

To inspect unexpected HTTP responses:

```bash
sort "$LOAD_DIR"/status-*.txt | uniq -c
rg -n '"status":5|"error"' "$LOAD_DIR"/body-*.json
```

## 6. Cleanup / 清理测试数据

The inventory and ticket rows and all related Redis keys can be removed precisely:

```bash
docker exec flashticket-mysql mysql -uroot -proot -e "
DELETE FROM flashticket_inventory.inventories
WHERE ticket_id='$LOAD_TICKET_ID';

DELETE FROM flashticket_ticket.tickets
WHERE id='$LOAD_TICKET_ID';"

redis-cli DEL \
  "inventory:$LOAD_TICKET_ID" \
  "inventory:stock:$LOAD_TICKET_ID" \
  "inventory:not-found:$LOAD_TICKET_ID" \
  "ticket:$LOAD_TICKET_ID" \
  "ticket:not-found:$LOAD_TICKET_ID"

redis-cli --scan --pattern "inventory:reserved:$LOAD_TICKET_ID:*" \
  | while IFS= read -r key; do
      redis-cli DEL "$key"
    done
```

`processed_events` currently stores only `event_id`, `event_type`, and `processed_at`; it does not store `ticket_id`. For that reason, this reusable cleanup intentionally does not delete processed-event rows, avoiding accidental deletion of unrelated events. On a fully isolated local database, the test rows can be identified by the recorded `$LOAD_STARTED_AT` timestamp before removing them manually.

当前 `processed_events` 只保存 `event_id`、`event_type` 和 `processed_at`，没有保存 `ticket_id`。因此通用清理脚本不会自动删除事件记录，以免误删其他业务事件。如果使用完全隔离的本地数据库，可以通过 `$LOAD_STARTED_AT` 记录的时间范围确认后再手动删除。

Keep `$LOAD_DIR` if the response bodies are needed for later inspection. Remove that temporary directory only after reviewing the evidence.

如果之后仍需检查响应内容，请保留 `$LOAD_DIR`。确认不再需要证据后，再手动删除该临时目录。
