# vps-order-service

B2B order management API (VPS Consulting challenge).

## Run

From the repo root:

```bash
docker compose up --build
```


|            |                                                                                |
| ---------- | ------------------------------------------------------------------------------ |
| API        | [http://localhost:8080](http://localhost:8080)                                 |
| Health     | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI    | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)         |


## Tests

```bash
cd vps
./mvnw test
```

