# Store Platform

Modular monolith — Java 21 + Spring Boot + PostgreSQL, multi-tenant
(un `Client` = negocio que compra el producto; cada cliente tiene N
establecimientos según su plan).

Documentación del happy path en [`docs/`](docs/README.md).

## Requisitos

- Java 21
- Docker (solo para PostgreSQL)
- Maven (incluido via `./mvnw`)

## Levantar la base de datos

```bash
docker compose up -d
```

PostgreSQL queda en `localhost:5433` — db `storedb`, user `store`, password `store`
(credenciales de desarrollo, ver `docker-compose.yml`).

## Ejecutar la aplicación

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

```bash
./mvnw test
```

## Happy path rápido (smoke test)

```bash
# 1. Alta del tenant (plan: máx. establecimientos, cajas y kioskos por store)
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{"name":"Cosmo Coffee","maxStores":1,"maxPosPerStore":2,"maxKiosksPerStore":2}'

# 2. Primer ADMIN del cliente (register solo crea el primer usuario del tenant)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"clientId":1,"name":"Admin","email":"admin@cosmo.com","password":"secret1"}'

# 3. El admin crea el establecimiento (clientId sale del JWT)
curl -X POST http://localhost:8080/api/stores \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Cosmo Centro","businessType":"CAFE"}'

# 4. Login eligiendo el store activo → JWT con storeId
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cosmo.com","password":"secret1","storeId":1}'

# 5. Catálogo: category → product → inventory
curl -X POST http://localhost:8080/api/categories \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Coffee"}'

curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Espresso","price":3.50,"categoryId":1}'

curl -X PUT http://localhost:8080/api/inventory/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"quantity":10}'

# 6. Orden desde caja (storeId sale del JWT, no del body)
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"channel":"POS","orderType":"DINE_IN","paymentMethod":"CASH","items":[{"productId":1,"quantity":2}]}'

# 7. Cocina avanza el estado
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

## Estructura

```text
src/main/java/com/store/platform/
├── auth/         # register (primer ADMIN) + login (elige storeId)
├── category/     # CRUD categories
├── client/       # Client (tenant) + límites del plan
├── common/       # ErrorResponse + GlobalExceptionHandler
├── config/       # OpenAPI/Swagger config
├── inventory/    # stock por producto (1:1)
├── order/        # Order + OrderItem + máquina de estados
├── product/      # CRUD products + active toggle + tokenCost
├── security/     # JWT filter/service + SecurityConfig (claims: clientId/storeId/role)
├── store/        # CRUD stores + devices (cajas/kioskos por plan)
└── user/         # User entity + roles + user_stores
```

## Reglas de negocio implementadas

- Multi-tenant: `clientId` y `storeId` viajan en el JWT; nunca se confía en
  IDs del body. El `?storeId=` opcional solo lo puede usar el ADMIN (stores de
  su cliente); el resto opera sobre su store de sesión.
- Plan del cliente limita: `maxStores`, `maxPosPerStore`, `maxKiosksPerStore`
  (tabla `devices` cuenta lo registrado).
- Orden valida: productos activos y del store de la sesión, stock suficiente →
  descuenta inventario en la misma transacción. Items duplicados se fusionan.
- `channel=KIOSK` solo acepta `CARD`; cuentas `CUSTOMER` solo crean órdenes
  `KIOSK` (cuenta genérica del kiosko ligada a su store).
- Estados: `PENDING → CONFIRMED → PREPARING → READY → COMPLETED`;
  `CANCELLED` hasta `PREPARING` inclusive — restaura inventario y marca
  `refund_status = REFUNDED` (refund manual por staff).
- Roles: mutaciones de catálogo requieren `ADMIN`/`MANAGER`; crear usuarios y
  stores/devices solo `ADMIN`; PATCH de estado de orden lo hace staff.
