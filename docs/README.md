# Store Platform — Documentación

Documentación del happy path del proyecto Store Platform (importada desde Obsidian
y extendida con el modelo de negocio multi-tenant).

## Índice

1. [Business Flow](01-business-flow.md)
2. [Authentication](02-authentication.md)
3. [Store Setup](03-store-setup.md)
4. [Customer Flow](04-customer-flow.md)
5. [Order Flow](05-order-flow.md)
6. [Backend Architecture](06-architecture.md)
7. [Request Flow](07-request-flow.md)
8. [Database Flow](08-database.md)
9. [API Happy Path](09-api.md)
10. [Enterprise Evolution](10-roadmap.md)
11. [First Development Goal (MVP)](11-mvp.md)
12. [Interview Topics](12-interview-topics.md)
13. [Frontend Screens / Surfaces](13-frontend-screens.md)
14. [Frontend Integration Guide](14-frontend-integration.md) *(repo Next.js separado)*
15. [Extras del cliente](15-client-extras.md) *(fichas, reportes, barra, tickets, reservaciones)*
16. [Módulos y permisos por cliente](16-modules-and-permissions.md) *(producto modular: módulos contratados por tenant y asignados por usuario)*

## Resumen

El producto se vende a **clientes** (restaurantes, cafés, etc.). Cada cliente
puede tener **varios establecimientos**, y cada establecimiento tiene su propio
catálogo (categorías, productos, inventario) y sus propias órdenes.

Superficies de usuario:

- **Panel Admin** — el administrador del cliente da de alta/baja categorías y
  productos, y activa/desactiva productos para mostrarlos a los clientes.
- **Caja (POS)** — el personal arma la orden (comer aquí / para llevar) y cobra
  en efectivo o tarjeta.
- **Kiosko** — el cliente final hace lo mismo que en caja, pero solo paga con
  tarjeta.
- **Pantalla de cocina (KDS)** — recibe las órdenes y las marca como en
  preparación, listas o canceladas.
- **Barra** *(extra del cliente)* — el bartender ve las bebidas pendientes y
  los canjes de fichas.
- **Login** — pantalla de inicio de sesión para staff/admin.

Extras solicitados por el cliente ([spec](15-client-extras.md)): fichas para
bebidas, reportes de ventas (del día, top productos, top empleado), impresión
de tickets y reservación de mesas y barra (6 sillas).

**Goal:** Learn, practice and sell the application.
