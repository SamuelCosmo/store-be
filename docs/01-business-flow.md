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
Activate / Deactivate products  ← controla qué se muestra a los clientes
```

## Order flow por canal

```text
Caja (POS)                    Kiosko (self-service)
    │                              │
Staff selecciona categorías   Customer selecciona categorías
y productos                   y productos (solo activos)
    │                              │
Comer aquí / Para llevar      Comer aquí / Para llevar
    │                              │
Pago: efectivo o tarjeta      Pago: solo tarjeta
    │                              │
    └──────────┬───────────────────┘
               ▼
         POST /api/orders
               ↓
    Order Service validates Order
               ↓
    Inventory is checked
               ↓
    Inventory is updated
               ↓
    Order is confirmed
               ↓
    Kitchen screen (KDS) receives the Order
               ↓
    Kitchen marks PREPARING → READY
      (o CANCELLED)
               ↓
    Customer receives Order
               ↓
    Order becomes COMPLETED
```

## Canales de venta

| Canal            | Quién opera      | Pago            | Tipo de orden              |
| ---------------- | ---------------- | --------------- | -------------------------- |
| Caja (POS)       | Staff (EMPLOYEE) | Efectivo/tarjeta | Comer aquí / Para llevar   |
| Kiosko           | Customer         | Solo tarjeta    | Comer aquí / Para llevar   |

## Pantalla de cocina (KDS)

La cocina ve las órdenes confirmadas del establecimiento y las marca con botones:

- `PREPARING` — la orden está en preparación
- `READY` — la orden está lista para entregar
- `CANCELLED` — la orden se cancela (restaura inventario)

## Extras del cliente

Además del flujo base, el cliente pidió: **fichas para bebidas** (compra en
caja, canje en barra), **reportes de admin** (ventas del día, top productos,
top empleado), **sección de barra**, **impresión de tickets** y
**reservación de mesas y barra** (6 sillas). Spec completa en
[15-client-extras](15-client-extras.md).
