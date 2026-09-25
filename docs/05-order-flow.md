# 5. Order Flow

```text
POST /api/orders
        ↓
Validate Request
        ↓
Validate Products (existen, activos, pertenecen al establecimiento)
        ↓
Check Inventory
        ↓
Calculate Total
        ↓
Create Order (con channel, orderType, paymentMethod)
        ↓
Create Order Items
        ↓
Update Inventory
        ↓
Return Order
        ↓
Aparece en la pantalla de cocina (KDS)
```

## Datos de la orden

Cada orden lleva:

- `storeId` — **derivado de la sesión autenticada** (el kiosko está ligado a
  su establecimiento por login; el staff a su tienda). No se confía en un
  `storeId` enviado en el body.
- `channel` — origen de la orden: `POS` (caja) o `KIOSK`
- `orderType` — `DINE_IN` (comer aquí) o `TAKEAWAY` (para llevar)
- `paymentMethod` — `CASH` (efectivo, solo caja) o `CARD` (tarjeta)

**Regla:** en `KIOSK` solo se permite `CARD`. En `POS` se permiten `CASH` y
`CARD`.

## Order Status

```text
PENDING
   ↓
CONFIRMED
   ↓
PREPARING   ← botón "en preparación" en cocina
   ↓
READY       ← botón "listo" en cocina
   ↓
COMPLETED
```

`CANCELLED` — permitido desde `PENDING`/`CONFIRMED` (botón "cancelar" en
cocina/caja); restaura el inventario.

## Endpoints

```text
POST   /api/orders
GET    /api/orders            # ?storeId=&status= para la pantalla de cocina
GET    /api/orders/{id}
PATCH  /api/orders/{id}/status
```
