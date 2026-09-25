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
- **Login** — pantalla de inicio de sesión para staff/admin.

**Goal:** Learn, practice and sell the application.
