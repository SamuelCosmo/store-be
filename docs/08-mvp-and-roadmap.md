# 8. MVP y Roadmap

## First Development Goal — MVP

Build only:

```text
Authentication
      +
Store
      +
Category
      +
Product
      +
Inventory
      +
Order
```

## Tech Stack

- Java 21
- Spring Boot
- Spring MVC
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- JUnit
- Mockito

## Definition of Done

The first MVP is complete when:

- [x] A user can register
- [x] A user can log in
- [x] A store can be created
- [x] Categories can be created
- [x] Products can be created
- [x] Inventory can be configured
- [x] Customers can browse products
- [x] Customers can create an order
- [x] The backend validates the order
- [x] Inventory is updated after an order
- [x] The order status can be updated
- [x] The customer can retrieve the order
- [x] The API has DTOs
- [x] Validation is implemented
- [x] Global exception handling is implemented
- [x] Unit tests cover the main business logic
- [x] PostgreSQL stores the application data
- [x] Swagger/OpenAPI documents the API

> Verificado el 2026-09-18 con smoke test end-to-end (ver README.md).

## Siguiente iteración — producto comercial multi-tenant

Requerimientos del modelo de negocio (ver [10-frontend-screens](10-frontend-screens.md)):

- [ ] Entidad `Client` (tenant): el cliente puede tener varios establecimientos
- [ ] `users` con FK → `clients` + `user_stores` (un usuario opera 1..N stores
      de su cliente)
- [ ] `Store` enlazado a `Client` (FK); órdenes acotadas por establecimiento
- [ ] Endpoint para activar/desactivar productos (`PATCH /api/products/{id}/active`)
- [ ] `Order` con `channel` (`POS`/`KIOSK`), `orderType` (`DINE_IN`/`TAKEAWAY`)
      y `paymentMethod` (`CASH`/`CARD`)
- [ ] Regla: en `KIOSK` solo se permite pago con `CARD`
- [ ] Sesión de kiosko ligada al establecimiento (cuenta `CUSTOMER` genérica);
      `storeId` derivado del JWT, no del body
- [ ] Cancelación hasta `PREPARING` + `refund_status` en `orders`
- [ ] `GET /api/orders` filtrable por `storeId` y `status` (pantalla de cocina)
- [ ] Pantalla de **Login**
- [ ] Enrutado por rol: cada usuario solo ve las pantallas de su módulo
      (matriz en [02-authentication](02-authentication.md#acceso-a-módulos-por-rol))
- [ ] Pantalla **Admin Panel**: alta/baja de categorías y productos, toggle activo
- [ ] Pantalla **Caja (POS)**: armar orden, comer aquí/para llevar, efectivo/tarjeta
- [ ] Pantalla **Kiosko**: autoservicio del cliente, solo tarjeta
- [ ] Pantalla **Cocina (KDS)**: ver órdenes y marcar PREPARING/READY/CANCELLED
- [ ] **CORS** para el frontend Next.js (`localhost:3000` en dev) —
      ver [11-frontend-integration](11-frontend-integration.md)

## Extras solicitados por el cliente

Ver spec completa en [12-client-extras](12-client-extras.md):

- [ ] **Sistema de fichas para bebidas** (físicas/anónimas): venta en caja
      (`POST /api/tokens/purchase`), canje en barra
      (`POST /api/tokens/redeem`, productos con `tokenCost > 0`),
      `token_transactions` ligadas al staff
- [ ] **Reportes admin**: ventas del día, productos más vendidos y ventas
      por empleado (`GET /api/reports/...`)
- [ ] **Sección de barra**: pantalla de items de bebida pendientes +
      canjes (`GET /api/bar/items`)
- [ ] **Impresión de tickets**: `GET /api/orders/{id}/ticket` con folio por
      establecimiento
- [ ] **Reservaciones**: mesas y barra (6 sillas, `zone=BAR`),
      `POST /api/reservations` con estados PENDING/CONFIRMED/SEATED/
      CANCELLED/NO_SHOW

## Producto modular (multi-giro)

Ver spec en [13-modules-and-permissions](13-modules-and-permissions.md):

- [ ] `stores.business_type` + preset de módulos por giro
- [ ] `client_modules` — módulos contratados por el cliente (plataforma)
- [ ] `user_modules` — subconjunto asignado por el admin del cliente
- [ ] Claims de módulos en el JWT + `@PreAuthorize` por módulo
- [ ] Enrutado del frontend por rol + módulos efectivos

## Enterprise Evolution

Once the happy path works, evolve the application.

```text
PHASE 1
Modular Monolith
      ↓
PHASE 2
DTOs + Validation + Exception Handling
      ↓
PHASE 3
Spring Security + JWT
      ↓
PHASE 4
Testing + SonarQube
      ↓
PHASE 5
Docker
      ↓
PHASE 6
MongoDB
      ↓
PHASE 7
Microservices
      ↓
PHASE 8
Kafka
      ↓
PHASE 9
CI/CD
      ↓
PHASE 10
AWS
```

## Estado actual

- [x] Fase 1 — Modular Monolith (MVP scaffold)
- [x] Fase 2 — DTOs + Validation + Exception Handling
- [x] Fase 3 — Spring Security + JWT
- [ ] Fase 4 — Testing + SonarQube *(tests unitarios incluidos; SonarQube pendiente)*
- [x] Fase 5 — Docker *(PostgreSQL en docker-compose; imagen de la app pendiente)*
- [ ] Fase 6 — MongoDB
- [ ] Fase 7 — Microservices
- [ ] Fase 8 — Kafka
- [ ] Fase 9 — CI/CD
- [ ] Fase 10 — AWS
