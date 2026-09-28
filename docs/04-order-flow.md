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

## Pagos — decisión

El cobro con tarjeta es **con terminal física** (tarjeta presente); **no se
usa VPOS** ni cobro en línea (tarjeta no presente).

- **Caja (POS), tarjeta** — si la integración de terminal está habilitada, el
  sistema manda el monto a la terminal y espera aprobado/rechazado; si no está
  habilitada (o falla), el cajero cobra a mano en la terminal (standalone) y
  confirma en el sistema que se cobró con `CARD`.
- **Caja (POS), efectivo** — el staff ingresa `cashReceived` (cuánto dinero le
  entrega el cliente), el sistema calcula el cambio a devolver
  (`changeGiven = cashReceived - total`); al aceptar, el cobro queda
  registrado. Validación: `cashReceived >= total`. Ambos campos quedan en la
  orden para el ticket y el corte de caja.
- **Kiosko** — requiere terminal **integrada** siempre: el kiosko manda el
  monto a la terminal y espera aprobado/rechazado + código de autorización
  (no hay staff que cobre a mano).
- Cuando aterrice la integración de terminal: agregar `payments` (intento de
  cobro con `status`, `authCode`, `cardLast4`) y `orders.payment_status`
  (`UNPAID`/`PAID`/`REFUNDED`). La orden cancelada ya restaura inventario —
  sirve para el caso de pago rechazado.

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

`CANCELLED` — permitido desde `PENDING`, `CONFIRMED` y `PREPARING` (botón
"cancelar" en cocina y en caja); restaura el inventario. Una vez `READY` la
comida ya está lista y no se cancela. Si la orden ya estaba pagada, el staff
devuelve el dinero manualmente y la orden queda marcada
`refund_status = REFUNDED`.

## Endpoints

```text
POST   /api/orders
GET    /api/orders            # ?storeId=&status= para la pantalla de cocina
GET    /api/orders/{id}
PATCH  /api/orders/{id}/status
```
