# 11. First Development Goal — MVP

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

Requerimientos del modelo de negocio (ver [13-frontend-screens](13-frontend-screens.md)):

- [ ] Entidad `Client` (tenant): el cliente puede tener varios establecimientos
- [ ] `Store` enlazado a `Client` (FK); órdenes acotadas por establecimiento
- [ ] Endpoint para activar/desactivar productos (`PATCH /api/products/{id}/active`)
- [ ] `Order` con `channel` (`POS`/`KIOSK`), `orderType` (`DINE_IN`/`TAKEAWAY`)
      y `paymentMethod` (`CASH`/`CARD`)
- [ ] Regla: en `KIOSK` solo se permite pago con `CARD`
- [ ] Sesión de kiosko ligada al establecimiento (login de activación);
      `storeId` derivado del JWT, no del body
- [ ] `GET /api/orders` filtrable por `storeId` y `status` (pantalla de cocina)
- [ ] Pantalla de **Login**
- [ ] Enrutado por rol: cada usuario solo ve las pantallas de su módulo
      (matriz en [02-authentication](02-authentication.md#acceso-a-módulos-por-rol))
- [ ] Pantalla **Admin Panel**: alta/baja de categorías y productos, toggle activo
- [ ] Pantalla **Caja (POS)**: armar orden, comer aquí/para llevar, efectivo/tarjeta
- [ ] Pantalla **Kiosko**: autoservicio del cliente, solo tarjeta
- [ ] Pantalla **Cocina (KDS)**: ver órdenes y marcar PREPARING/READY/CANCELLED
- [ ] **CORS** para el frontend Next.js (`localhost:3000` en dev) —
      ver [14-frontend-integration](14-frontend-integration.md)

## Extras solicitados por el cliente

Ver spec completa en [15-client-extras](15-client-extras.md):

- [ ] **Sistema de fichas para bebidas**: compra en caja
      (`POST /api/tokens/purchase`), saldo por customer (`token_wallets`),
      canje por bebidas (`POST /api/tokens/redeem`, productos con
      `tokenCost > 0`)
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

Ver spec en [16-modules-and-permissions](16-modules-and-permissions.md):

- [ ] `stores.business_type` + preset de módulos por giro
- [ ] `client_modules` — módulos contratados por el cliente (plataforma)
- [ ] `user_modules` — subconjunto asignado por el admin del cliente
- [ ] Claims de módulos en el JWT + `@PreAuthorize` por módulo
- [ ] Enrutado del frontend por rol + módulos efectivos
