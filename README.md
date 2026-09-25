# Store Platform

Modular monolith MVP — Java 21 + Spring Boot + PostgreSQL.

> **Status:** early development. The repo currently contains the design docs;
> the backend code lands in the next commits.

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
# 1. Registrar un ADMIN y un CUSTOMER (devuelven el JWT)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Admin","email":"admin@mail.com","password":"secret1","role":"ADMIN"}'

curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Ana","email":"ana@mail.com","password":"secret1"}'

# 2. Crear store → category → product → inventory (con token ADMIN)
curl -X POST http://localhost:8080/api/stores \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Cosmo Coffee","description":"Coffee shop"}'

curl -X POST http://localhost:8080/api/categories \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Coffee","storeId":1}'

curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Espresso","price":3.50,"categoryId":1}'

curl -X PUT http://localhost:8080/api/inventory/1 \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"quantity":10}'

# 3. El cliente crea una orden (con token CUSTOMER)
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" -H "Content-Type: application/json" \
  -d '{"storeId":1,"items":[{"productId":1,"quantity":2}]}'

# 4. El staff avanza el estado de la orden
curl -X PATCH http://localhost:8080/api/orders/1/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"status":"CONFIRMED"}'
```

## Estructura

```text
src/main/java/com/store/platform/
├── auth/         # register + login (JWT)
├── category/     # CRUD categories
├── common/       # ErrorResponse + GlobalExceptionHandler
├── config/       # OpenAPI/Swagger config
├── inventory/    # stock por producto (1:1)
├── order/        # Order + OrderItem + máquina de estados
├── product/      # CRUD products
├── security/     # JWT filter/service + SecurityConfig
├── store/        # CRUD stores
└── user/         # User entity + roles
```

## Reglas de negocio implementadas

- Orden valida: store existe, productos pertenecen a esa store y están activos,
  stock suficiente → descuenta inventario en la misma transacción.
- Items duplicados en la misma orden se fusionan sumando cantidades.
- Estados: `PENDING → CONFIRMED → PREPARING → READY → COMPLETED`
  (`CANCELLED` permitido desde `PENDING`/`CONFIRMED` y restaura inventario).
- Clientes solo ven sus propias órdenes; staff las ve todas (o filtra por store).
- GET del catálogo (stores/categories/products) es público; mutaciones requieren rol.
