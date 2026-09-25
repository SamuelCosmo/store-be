# 1. Business Flow

El producto es **multi-tenant**: se vende a clientes (restaurantes, cafés, etc.)
y cada cliente puede tener **varios establecimientos**. Las órdenes quedan
enlazadas al establecimiento, y el establecimiento al cliente.

## Tenant setup (cliente del producto)

```text
Client (business owner)
    ↓
Create Establishment(s)     ← un cliente puede tener varios
    ↓
Create Categories
    ↓
Create Products
    ↓
Set Product Prices
    ↓
Configure Inventory
    ↓
Activate / Deactivate products  ← flag `active`: qué se muestra en caja/kiosko
```

Desde el **Panel Admin**, el `ADMIN` del cliente da de alta/baja categorías y
productos, define precios, activa/desactiva productos para el canal de venta y
configura inventario.

## Jerarquía

```text
Client (tenant)
 │
 ├── Establishment A
 │      ├── Categories → Products → Inventory
 │      └── Orders
 │
 └── Establishment B
        ├── Categories → Products → Inventory
        └── Orders
```

## Order flow por canal

Dos canales crean órdenes (`POST /api/orders`):

- **Caja (POS)** — operada por staff (`EMPLOYEE`), pago en efectivo o tarjeta.
- **Kiosko** — autoservicio del customer, solo tarjeta, sesión ligada al
  establecimiento.

Ambas permiten *comer aquí* / *para llevar*. El backend valida productos e
inventario, confirma la orden y llega a la **pantalla de cocina (KDS)**, que la
marca `PREPARING → READY` o `CANCELLED`. Detalle por canal en
[03-customer-flow](03-customer-flow.md) y del backend en
[04-order-flow](04-order-flow.md).

## Extras del cliente

Módulos opcionales contratados por cliente: **fichas para bebidas** (físicas),
**reportes de ventas**, **barra**, **impresión de tickets** y **reservación de
mesas y barra**. Spec completa en [12-client-extras](12-client-extras.md) y
modelo modular en [13-modules-and-permissions](13-modules-and-permissions.md).

## Example: Cosmo Coffee (un establecimiento del cliente)

```text
Cosmo Coffee
│
├── Coffee
│   ├── Espresso
│   ├── Americano
│   └── Cappuccino
│
├── Cold Drinks
│   ├── Iced Coffee
│   └── Iced Tea
│
└── Bakery
    ├── Croissant
    └── Muffin
```
