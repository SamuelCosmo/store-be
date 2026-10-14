# Store Platform — Documentación

Documentación del happy path del proyecto Store Platform (importada desde Obsidian
y extendida con el modelo de negocio multi-tenant).

## Índice

1. [Business Flow](01-business-flow.md)
2. [Authentication](02-authentication.md)
3. [Customer Flow](03-customer-flow.md)
4. [Order Flow](04-order-flow.md)
5. [Backend Architecture](05-architecture.md)
6. [Database Flow](06-database.md)
7. [API Happy Path](07-api.md)
8. [MVP y Roadmap](08-mvp-and-roadmap.md)
9. [Interview Topics](09-interview-topics.md)
10. [Frontend Screens / Surfaces](10-frontend-screens.md)
11. [Frontend Integration Guide](11-frontend-integration.md) *(repo Next.js separado)*
12. [Extras del cliente](12-client-extras.md) *(fichas, reportes, barra, tickets, reservaciones)*
13. [Módulos y permisos por cliente](13-modules-and-permissions.md) *(producto modular: módulos contratados por tenant y asignados por usuario)*
14. [Terminales de pago con tarjeta](14-card-terminals.md) *(opciones por restaurante: Total POS, Billpocket, Mercado Pago, NetPay — pasos del cliente y de la plataforma)*

## Resumen

El producto se vende a **clientes** (restaurantes, cafés, etc.). Cada cliente
puede tener **varios establecimientos**. El catálogo se arma así: **extras y
tamaños** viven a nivel cliente; **categorías** se asignan a una o varias
tiendas (`category_stores`); **productos, inventario y órdenes** viven por
establecimiento.

Superficies de usuario:

- **Panel Admin** — el administrador del cliente da de alta/baja categorías y
  productos, y activa/desactiva productos para mostrarlos a los clientes.
- **Caja (POS)** — el personal arma la orden (comer aquí / para llevar) y cobra
  en efectivo o tarjeta.
- **Kiosko** — el cliente final hace lo mismo que en caja, pero solo paga con
  tarjeta; el kiosko opera con una cuenta `CUSTOMER` genérica ligada al
  establecimiento.
- **Pantalla de cocina (KDS)** — recibe las órdenes y las marca como en
  preparación, listas o canceladas.
- **Barra** *(extra del cliente)* — el bartender ve las bebidas pendientes y
  los canjes de fichas.
- **Login** — pantalla de inicio de sesión para staff/admin.

Extras solicitados por el cliente ([spec](12-client-extras.md)): fichas físicas
para bebidas, reportes de ventas (del día, top productos, top empleado),
impresión de tickets y reservación de mesas y barra (6 sillas).

**Goal:** Learn, practice and sell the application.
