# 15. Extras del cliente

> Todos estos puntos son **módulos opcionales** que se activan por cliente —
> ver [13-modules-and-permissions](13-modules-and-permissions.md).

Puntos adicionales solicitados por el cliente fuera del alcance original del MVP:

1. **Sistema de fichas para bebidas** — el cliente final compra fichas y las
   intercambia por bebidas.
2. **Reportes de admin** — ventas del día, productos más vendidos y quién
   vendió más.
3. **Barra + tickets + reservaciones** — sección de barra (6 sillas),
   impresión de tickets y reservación de mesas y barra.

## 1. Sistema de fichas (tokens) para bebidas

El establecimiento vende **fichas físicas** que el cliente final canjea por
bebidas. Casos de uso: preventa de bebidas, promociones, eventos.

> **Decisión:** las fichas son físicas y anónimas — el cliente final no tiene
> cuenta. El sistema solo registra la venta (en caja) y el canje (en barra)
> para reportes e inventario; no hay wallet ni saldo digital.

### Flujo

```text
Compra de fichas (Caja POS)
    ↓
Staff vende N fichas (efectivo o tarjeta)
    ↓
TokenTransaction type=PURCHASE (+N)     ← ligada al staff que vendió
    ↓
Se entregan las fichas físicas al customer
    ↓
─────────────────────────────────────────
    ↓
Canje en barra
    ↓
Customer entrega fichas físicas al bartender
    ↓
Bartender elige la bebida (Product con tokenCost > 0)
    ↓
TokenTransaction type=REDEMPTION (−N)   ← ligada al staff que canjeó
    ↓
Se descuenta inventario de la bebida
    ↓
Aparece en la pantalla de barra para preparar/entregar
```

### Reglas

- Las fichas son **físicas y anónimas**: no existe `token_wallets` ni saldo
  digital — la ficha en mano ES el saldo.
- Solo los productos marcados como canjeables (`tokenCost > 0`) se pueden
  pagar con fichas — normalmente la categoría *Bebidas*.
- El precio de la ficha lo configura el establecimiento (`tokenPrice` en
  `stores` o tabla de configuración).
- Compra y canje quedan ligados al staff autenticado que los operó (alimenta
  el reporte *quién vendió más*).
- El canje descuenta inventario igual que una orden normal.

### Endpoints

```text
POST   /api/tokens                 # { quantity, paymentMethod: CASH|CARD } — caja (staff)
GET    /api/tokens                 # ?storeId=&from=&to= — ventas del rango
# El canje es una orden: POST /api/orders con paymentMethod=TOKEN
# (total = 0; tokenCost por producto; OrderResponse.tokenAmount)
```

> Implementado con ese contrato (ver `07-api.md` → Tokens): la venta cobra
> `quantity × clients.tokenPrice`; el canje es una orden con `paymentMethod:
> TOKEN` en vez de un endpoint `/redeem` dedicado — así el KDS la ve como
> orden normal y el corte de caja la distingue por método de pago.

## 2. Reportes de administración

Panel de reportes para `ADMIN`/`MANAGER`, siempre acotado al `storeId` del
establecimiento:

- **Ventas del día** — total vendido, desglose por canal (POS/KIOSK) y por
  método de pago (CASH/CARD), incluyendo venta de fichas.
- **Productos más vendidos** — ranking por cantidad y por importe en un
  rango de fechas.
- **Ventas por empleado** — ranking de staff por total vendido en un
  rango de fechas (órdenes `POS` creadas por cada `EMPLOYEE` + fichas
  vendidas).

```text
GET    /api/reports/sales/daily?date=            # ventas del día
GET    /api/reports/top-products?from=&to=&limit= # productos más vendidos
GET    /api/reports/sales-by-employee?from=&to=  # quién vendió más
```

## 3. Barra, tickets y reservaciones

### Sección de barra

Nueva superficie **Barra** (análoga al KDS de cocina): el bartender ve los
items de bebida de las órdenes confirmadas y los canjes de fichas
pendientes, y los marca como entregados.

- Lista items de bebida: `GET /api/bar/items?status=`
- Marca entregado: `PATCH /api/bar/items/{id}/status`
- Rol: `EMPLOYEE` (barra) / `MANAGER`.

### Impresión de tickets

Cada orden (y cada compra/canje de fichas) genera un **ticket** imprimible.

- `GET /api/orders/{id}/ticket` — devuelve los datos del ticket
  (establecimiento, items, totales, método de pago, folio).
- La impresión física la resuelve el frontend (impresora térmica /
  `window.print()`); el backend solo entrega el payload formateado.
- Opcional: `ticketNumber` folio consecutivo por establecimiento y
  `printedAt` para saber si ya se imprimió.

### Reservación de mesas y barra

El establecimiento tiene **mesas** y una **barra fija de 6 sillas**
(configurable por `store`). Staff y clientes pueden reservar.

```text
GET    /api/tables?zone=               # mesas y sillas de barra
POST   /api/tables                     # alta de mesas (ADMIN/MANAGER)
GET    /api/reservations/availability?date=&zone=   # huecos libres
POST   /api/reservations               # { tableId, date, timeSlot,
                                       #   partySize, customerName, phone }
                                       # (sillas de barra = tables zone=BAR)
GET    /api/reservations?date=&status=
PATCH  /api/reservations/{id}/status   # CONFIRMED/SEATED/CANCELLED/NO_SHOW
```

Reglas:

- La barra son exactamente **6 sillas** — se modelan como `tables` con
  `zone = BAR` y capacidad 1, o como entidad propia si crece.
- Una reservación bloquea mesa/silla en un slot de fecha+hora; no se
  permiten solapamientos sobre el mismo recurso.
- Estados: `PENDING → CONFIRMED → SEATED`, más `CANCELLED` y `NO_SHOW`.

## Nuevas tablas

Ver detalle de columnas en [06-database](06-database.md#tablas).

- `token_transactions` — movimientos `PURCHASE`/`REDEMPTION`/`ADJUSTMENT`,
  ligados al staff que operó y al establecimiento
- `tables` — mesas y sillas de barra (`zone`: `DINING`/`BAR`, `seats`)
- `reservations` — reservaciones sobre mesas/sillas
- `tickets` — folio y payload impreso por orden/transacción *(opcional;
  puede generarse on-the-fly desde `orders`)*

## Preguntas abiertas para el cliente

- ¿Las fichas caducan? (se imprime en la ficha física / política del negocio)
- ¿La reservación la hace solo el staff o también el customer desde una
  pantalla pública?
- ¿El ticket de compra de fichas es el mismo formato que el de orden?
- ¿Cuántos slots/horarios de reservación por día (duración por slot)?
