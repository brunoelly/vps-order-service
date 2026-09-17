# vps-order-service

REST API for B2B orders. A partner has a credit limit; creating an order holds that amount so two concurrent creates cannot both spend the same remaining credit. 
Orders move through a fixed status list. There is no UI and no login.

## Prerequisites

- **Docker** (Engine is enough) — this is the intended way to run it
- **JDK 17** and the Maven wrapper — only if you run the jar on the host

## Run with Docker

From the **repo root**.

Linux / macOS / Ubuntu (WSL):

```bash
docker compose up --build
```

Windows:

```bash
wsl
cd /mnt/c/Projects/vps-backend
docker compose up --build
```

| | |
|---|---|
| API | http://localhost:8081 |
| Health | http://localhost:8081/actuator/health |
| Swagger UI | http://localhost:8081/swagger-ui.html (redirects to `/swagger-ui/index.html`) |
| OpenAPI | http://localhost:8081/v3/api-docs |
| Postgres (host) | `localhost:5433` — user/password/db `orders` |

The app reaches Postgres as `postgres:5433` on the Compose network.
Wait until health returns `{"status":"UP"}` before calling the API.

## Run on the host

Start Postgres yourself (or keep the Compose `postgres` service and stop `app`). Profile `dev` is the default:

```bash
cd vps-order-service
./mvnw spring-boot:run
```

`application-dev.yml` points at `localhost:5432`. If you are using the Compose database, it is on **5433**:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/orders \
SPRING_DATASOURCE_USERNAME=orders \
SPRING_DATASOURCE_PASSWORD=orders \
./mvnw spring-boot:run
```

The process then listens on **8080**. Point the curls below at that port, or pass `--server.port=8081`. Compose sets `prod` (`postgres` as the DB host). `test` / `it` are for Surefire only.

## Status

```
PENDENTE         -> APROVADO | CANCELADO
APROVADO         -> EM_PROCESSAMENTO | CANCELADO
EM_PROCESSAMENTO -> ENVIADO
ENVIADO          -> ENTREGUE
```

`ENTREGUE` and `CANCELADO` are final. Create checks partner credit and holds the total. Approve does not debit again. Cancel from `PENDENTE` or `APROVADO` releases the hold. Later statuses cannot be cancelled.

`POST /api/v1/orders` requires `Idempotency-Key`. The same key and the same lines return the original order; a different payload on that key is 409.

## API examples

Base: `http://localhost:8081`. Replace the ids from the create responses.

Create partner:

```bash
curl -sS -X POST http://localhost:8081/api/v1/partners \
  -H 'Content-Type: application/json' \
  -d '{"name":"Acme","creditLimit":500.00}'
```

Get partner:

```bash
curl -sS http://localhost:8081/api/v1/partners/$PARTNER_ID
```

Raise or lower the contractual limit (cannot go below what is already reserved):

```bash
curl -sS -X PATCH http://localhost:8081/api/v1/partners/$PARTNER_ID/credit-limit \
  -H 'Content-Type: application/json' \
  -d '{"creditLimit":750.00}'
```

Create order:

```bash
curl -sS -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"partnerId":"'"$PARTNER_ID"'","items":[{"sku":"SKU-1","productName":"Widget","quantity":2,"unitPrice":10.00}]}'
```

Get order:

```bash
curl -sS http://localhost:8081/api/v1/orders/$ORDER_ID
```

Search (`page` defaults to 0, `size` to 20, max 100; newest `createdAt` first):

```bash
curl -sS "http://localhost:8081/api/v1/orders?partnerId=$PARTNER_ID&status=PENDENTE&page=0&size=20"
```

Approve, then move along the machine:

```bash
curl -sS -X PATCH http://localhost:8081/api/v1/orders/$ORDER_ID/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"APROVADO"}'
```

Cancel (from `PENDENTE` or `APROVADO`):

```bash
curl -sS -X POST http://localhost:8081/api/v1/orders/$ORDER_ID/cancel
```

Use a second order if you want to walk the rest of the machine after approve (`EM_PROCESSAMENTO` → `ENVIADO` → `ENTREGUE`). Same `PATCH` body, different `status`.

Replay the create with the same `Idempotency-Key` and the same lines: you get the same order id and credit is not reserved twice. Change the lines but keep the key: 409.

### Errors

Bodies are `application/problem+json` (`timestamp`, `traceId`; send `X-Request-Id` to see it echoed).

Missing `Idempotency-Key` → 400:

```bash
curl -sS -D - -o /dev/null -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"partnerId":"'"$PARTNER_ID"'","items":[{"sku":"SKU-1","productName":"Widget","quantity":1,"unitPrice":1.00}]}'
```

Malformed JSON → 400:

```bash
curl -sS -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: bad-json' \
  -d '{"partnerId":'
```

Unknown id → 404:

```bash
curl -sS http://localhost:8081/api/v1/orders/00000000-0000-0000-0000-000000000001
curl -sS http://localhost:8081/api/v1/partners/00000000-0000-0000-0000-000000000001
```

Empty items / blank partner name / quantity `0` → 422. Over available credit → 422:

```bash
curl -sS -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: too-big' \
  -d '{"partnerId":"'"$PARTNER_ID"'","items":[{"sku":"SKU-1","productName":"Widget","quantity":1,"unitPrice":99999.00}]}'
```

Lower the credit limit below what is already reserved → 422.

Illegal jump (`PENDENTE` → `ENVIADO`) or cancel after `EM_PROCESSAMENTO` → 409:

```bash
curl -sS -X PATCH http://localhost:8081/api/v1/orders/$ORDER_ID/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"ENVIADO"}'
```

Same `Idempotency-Key`, different lines → 409:

```bash
curl -sS -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: demo-1' \
  -d '{"partnerId":"'"$PARTNER_ID"'","items":[{"sku":"SKU-1","productName":"Widget","quantity":9,"unitPrice":10.00}]}'
```

Twenty concurrent creates against one partner are in `OrderApiIntegrationTest`, not in these curls.

## Tests

```bash
cd vps-order-service
./mvnw test
./mvnw verify
```

`./mvnw verify` fails if JaCoCo line coverage on `order`/`partner` `domain` + `application` is under 80%.

`OrderPersistenceTest` and `OrderApiIntegrationTest` need Docker (Testcontainers) and are skipped if the daemon is not on the PATH (typical from Git Bash here). From Ubuntu with Docker and a JDK they run the HTTP flow, 400/404/409/422, idempotency, and 20 concurrent creates against a credit limit that only fits 5 orders.

## Credit and concurrency

`OrderService.place` locks the partner row (`SELECT … FOR UPDATE`), reserves credit, then inserts the order in one transaction. Hikari’s pool is 20. Create / status change / cancel also write an `outbox` row in that transaction; a poller publishes through Spring’s `ApplicationEventPublisher` (log listener, no Kafka). A replay of the same idempotency key does not write a second event.

Schema is Flyway (`partners`, `orders`, `order_items`, `outbox`). Hibernate only validates it. Responses are DTOs, not JPA entities.

| | |
|---|---|
| Database | PostgreSQL 16 — locks and `CHECK (available_credit >= 0)` |
| Money | `BigDecimal` / `NUMERIC(19,2)` |
| Time | UTC `Instant` |
| IDs | UUID |
| Errors | RFC 7807 |
| Pagination | `page` / `size` |

The brief allows Postgres or MongoDB. This API holds partner credit while many orders can hit the same partner at once, so two creates must not both pass a check-then-write. That needs a transaction and a row lock. Mongo can use a session, but there is no `SELECT … FOR UPDATE`, and the invariant would live only in application code. For a credit ledger that is the wrong default.

## Out of scope

No UI, no authentication, no Kafka/Rabbit, no Kubernetes.
