# vps-order-service

B2B order management API (VPS Consulting challenge).

## Run

Docker. From the **repo root**.

Linux / macOS / Ubuntu (WSL):

```bash
docker compose up --build
```

Windows: use Ubuntu (WSL), not Git Bash. Git Bash has no `docker` and no `/mnt/...`.

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

The app reaches Postgres as `postgres:5432` on the Compose network. Host **5433** avoids clashing with another local Postgres on 5432. Host **8081** avoids clashing with another Java process on 8080. Inside the container the API still listens on 8080.

## Tests

```bash
cd vps-order-service
./mvnw test
```

Domain tests always run (`OrderStatus` matrix, totals, credit reserve/release, services). `./mvnw verify` also fails the build if JaCoCo line coverage on `order`/`partner` `domain` + `application` is under 80%.

`OrderPersistenceTest` and `OrderApiIntegrationTest` need Docker (Testcontainers) and are skipped if the daemon is not on the PATH (typical from Git Bash on this machine). From Ubuntu WSL with Docker **and** a JDK, they run: REST create/get/search/status/cancel, 400/404/409/422, idempotency, and 20 concurrent creates against a credit limit that only fits 5 orders.

## Domain

Orders start as `PENDENTE`. Allowed moves:

```
PENDENTE         -> APROVADO | CANCELADO
APROVADO         -> EM_PROCESSAMENTO | CANCELADO
EM_PROCESSAMENTO -> ENVIADO
ENVIADO          -> ENTREGUE
```

`ENTREGUE` and `CANCELADO` are final. Creating an order checks partner credit and holds the total until approval (already reserved) or cancel (released).

`OrderService.place` locks the partner row (`SELECT … FOR UPDATE`), reserves credit, then inserts the order in one transaction. A repeated `Idempotency-Key` with the same lines returns the original order; a different payload is rejected. `changeStatus` only allows legal moves (approve does not debit again). Cancel releases the hold.

HTTP is under `/api/v1`. Partners: create, get, patch credit limit. Orders: create (`Idempotency-Key` required), get, search (`partnerId`, `status`, `createdFrom`, `createdTo`, `page`, `size`; default `page=0`, `size=20`, max `100`), patch status, cancel. Responses are DTOs, not JPA entities.

Errors are `application/problem+json`: 404 not found, 409 illegal status or idempotency conflict, 422 validation or insufficient credit, 400 malformed JSON / bad query / missing header. Bodies include `timestamp` and `traceId` (`X-Request-Id` if the client sent one).

Create, status change, and cancel write a row to `outbox` in the same transaction. A poller publishes those rows through Spring’s `ApplicationEventPublisher` and a log listener; there is no Kafka or Rabbit. An identical `Idempotency-Key` replay does not write a second event.

Schema is Flyway (`partners`, `orders`, `order_items`, `outbox`). Hibernate only validates it.

## Why PostgreSQL

The brief allows Postgres or MongoDB. This API holds partner credit while many orders can hit the same partner at once, so two creates must not both pass a check-then-write. That needs a transaction, a row lock (or `UPDATE … WHERE available_credit >= :total`), and a check that credit never goes negative.

Postgres does that in the engine. Mongo can use a session, but there is no `SELECT … FOR UPDATE`, constraints are weaker, and the invariant would live only in application code. For a credit ledger that is the wrong default.
