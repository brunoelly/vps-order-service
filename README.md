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
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI | http://localhost:8080/v3/api-docs |
| Postgres (host) | `localhost:5433` — user/password/db `orders` |

The app reaches Postgres as `postgres:5432` on the Compose network. Host **5433** avoids clashing with another local Postgres on 5432.

## Tests

```bash
cd vps-order-service
./mvnw test
```

Domain tests always run. `OrderPersistenceTest` needs Docker (Testcontainers) and is skipped if the daemon is not on the PATH (typical from Git Bash on this machine). From Ubuntu WSL with Docker, it runs.

## Domain

Orders start as `PENDENTE`. Allowed moves:

```
PENDENTE         -> APROVADO | CANCELADO
APROVADO         -> EM_PROCESSAMENTO | CANCELADO
EM_PROCESSAMENTO -> ENVIADO
ENVIADO          -> ENTREGUE
```

`ENTREGUE` and `CANCELADO` are final. Creating an order checks partner credit and holds the total until approval (already reserved) or cancel (released).

Schema is Flyway (`partners`, `orders`, `order_items`, `outbox`). Hibernate only validates it.

## Why PostgreSQL

The brief allows Postgres or MongoDB. This API holds partner credit while many orders can hit the same partner at once, so two creates must not both pass a check-then-write. That needs a transaction, a row lock (or `UPDATE … WHERE available_credit >= :total`), and a check that credit never goes negative.

Postgres does that in the engine. Mongo can use a session, but there is no `SELECT … FOR UPDATE`, constraints are weaker, and the invariant would live only in application code. For a credit ledger that is the wrong default.
