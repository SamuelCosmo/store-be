# 9. API Happy Path

## Authentication

```text
POST   /api/auth/register
POST   /api/auth/login
```

## Clients (tenants) *(pendiente de implementar)*

```text
POST   /api/clients
GET    /api/clients/{id}
GET    /api/clients/{id}/stores
```

## Stores / Establishments

```text
POST   /api/stores            # ligado a un clientId
GET    /api/stores
GET    /api/stores/{id}
PUT    /api/stores/{id}
```

## Categories

```text
POST   /api/categories
GET    /api/categories        # ?storeId=
GET    /api/categories/{id}
PUT    /api/categories/{id}
DELETE /api/categories/{id}
```

## Products

```text
POST   /api/products
GET    /api/products          # ?storeId=&categoryId=
GET    /api/products/{id}
PUT    /api/products/{id}
PATCH  /api/products/{id}/active   # activar/desactivar para caja y kiosko
DELETE /api/products/{id}
```

## Inventory

```text
GET    /api/inventory/{productId}
PUT    /api/inventory/{productId}
```

## Orders

```text
POST   /api/orders
GET    /api/orders            # ?storeId=&status= (pantalla de cocina filtra por estado)
GET    /api/orders/{id}
PATCH  /api/orders/{id}/status
```

### OrderRequest

```json
{
  "channel": "POS | KIOSK",
  "orderType": "DINE_IN | TAKEAWAY",
  "paymentMethod": "CASH | CARD",
  "cashReceived": 100.00,
  "items": [{ "productId": 1, "quantity": 2 }]
}
```

**Reglas:**

- `channel = KIOSK` ⇒ `paymentMethod` solo `CARD`.
- `paymentMethod = CASH` ⇒ `cashReceived` requerido y `>= total` — la respuesta
  incluye `changeGiven` (cambio a devolver).
- `storeId` **no va en el body** — se deriva de la sesión autenticada (kiosko
  ligado al establecimiento por login, staff ligado a su tienda).

### PATCH /api/orders/{id}/status

```json
{ "status": "PENDING | CONFIRMED | PREPARING | READY | COMPLETED | CANCELLED" }
```

Usado por la pantalla de cocina: `PREPARING`, `READY`, `CANCELLED`.

## Tokens (fichas para bebidas) *(extras del cliente)*

```text
POST   /api/tokens/purchase     # { quantity, paymentMethod } — venta en caja
POST   /api/tokens/redeem       # { productId, quantity } — canje en barra
```

**Reglas:** fichas físicas anónimas — no hay wallet ni cuenta de customer;
solo productos con `tokenCost > 0` son canjeables; el canje descuenta
inventario; compra y canje quedan ligados al staff autenticado que los operó.

## Reports *(extras del cliente — ADMIN/MANAGER)*

```text
GET    /api/reports/sales/daily?date=              # ventas del día
GET    /api/reports/top-products?from=&to=&limit=  # productos más vendidos
GET    /api/reports/sales-by-employee?from=&to=    # quién vendió más
```

## Bar *(extras del cliente)*

```text
GET    /api/bar/items?status=           # bebidas pendientes de órdenes y canjes
PATCH  /api/bar/items/{id}/status       # marcar entregado
GET    /api/orders/{id}/ticket          # payload del ticket para impresión
```

## Tables & Reservations *(extras del cliente)*

```text
GET    /api/tables?zone=                     # mesas y sillas de barra
POST   /api/tables                           # alta (ADMIN/MANAGER)
GET    /api/reservations/availability?date=&zone=
POST   /api/reservations                     # { tableId, date, timeSlot,
                                             #   partySize, customerName, phone }
GET    /api/reservations?date=&status=
PATCH  /api/reservations/{id}/status         # CONFIRMED/SEATED/CANCELLED/NO_SHOW
```

**Reglas:** la barra son 6 sillas (`zone=BAR`); no se permiten reservaciones
solapadas sobre la misma mesa/silla.
