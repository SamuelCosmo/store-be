# Store Platform

Modular monolith — Java 21 + Spring Boot + PostgreSQL, multi-tenant
(un `Client` = negocio que compra el producto; cada cliente tiene N
establecimientos según su plan).

Documentación del happy path en [`docs/`](docs/README.md).

## Nuevo en el proyecto?

1. Lee [`docs/07-api.md`](docs/07-api.md) — el happy path de la API con
   estado real (✅ implementado / pendiente) — y [`docs/README.md`](docs/README.md)
   para el índice completo.
2. Convenciones del código: cada feature es un paquete con
   `Entity / Repository / Service / Controller / dto/` (ver `product/` o
   `extra/` como plantilla).
3. **Scoping multi-tenant**: nunca confíes en IDs del body ni del path —
   `AuthContext.current()` da `clientId`/`storeId`/`role` del JWT y
   `StoreService.resolveScope()` decide si el caller opera su tienda de
   sesión o, siendo ADMIN sin tienda, todo el cliente.
4. **Hibernate `ddl-auto: update`**: agregar columnas/tablas no requiere
   migración manual, pero el proceso corriendo tiene el esquema viejo —
   reinicia la app tras cambios de entidades (síntoma típico: `500` o
   campos que llegan `null`/`undefined` al frontend).
5. Relaciones lazy: marca lecturas con `@Transactional(readOnly = true)` o
   salta `LazyInitializationException` al serializar (ya pasó en
   `categories`/`products`).
6. Errores: lanza las excepciones de `common/exception` — el
   `GlobalExceptionHandler` las convierte en `ErrorResponse`.
7. Sin datos semilla: el SUPERADMIN se inserta directo en la DB
   (`users.role = 'SUPERADMIN'`, `client_id = NULL`) y desde `/superadmin`
   se crean los tenants con su ADMIN. Ninguna API asigna el rol SUPERADMIN.

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

`JWT_SECRET` es obligatorio (sin él la app no arranca). Genera uno propio
por entorno — nunca lo subas al repo:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
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
  -d '{"name":"Coffee","icon":"cup","storeIds":[1]}'

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
├── category/     # CRUD categories (multi-store: category_stores, icon, active)
├── client/       # Client (tenant) + límites del plan
├── common/       # ErrorResponse + GlobalExceptionHandler
├── config/       # OpenAPI/Swagger config
├── extra/        # CRUD extras (catálogo del cliente, N:M con products)
├── inventory/    # stock por producto (1:1)
├── order/        # Order + OrderItem + máquina de estados
├── product/      # CRUD products + active toggle + tokenCost + sku/image
├── security/     # JWT filter/service + SecurityConfig (claims: clientId/storeId/role)
├── size/         # CRUD sizes (catálogo del cliente, N:M con products)
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
