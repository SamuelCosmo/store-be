# 9. API Happy Path

Estado real al 2026-09-28 — lo marcado *(pendiente)* está documentado pero
sin implementar.

## Convención de paginación ✅

Los `GET` de lista aceptan los params estándar de Spring `Pageable`:

```text
?page=0&size=10&sort=name,asc&search=ramen
```

- **Sin `page`/`size`** → devuelven el array completo como siempre
  (terminales, selects, KDS — compatibilidad conservada).
- **Con `page` o `size`** → devuelven `Page<T>` de Spring:
  `{ content: [...], totalElements, totalPages, number, size, ... }`.
- `sort` = `propiedad,(asc|desc)` — propiedad del entity (`category.name`
  ordena productos por categoría; `products` en categorías es un conteo
  dedicado, no una property). Sin sort, cada endpoint aplica su default.
- `search` = búsqueda case-insensitive en los campos visibles de la tabla
  (nombre/descripcion/email según la entidad).
- Endpoints paginados: `GET /api/products`, `GET /api/categories`,
  `GET /api/clients/{id}/stores`, `GET /api/users`, `GET /api/orders`
  (este último ordena `createdAt desc` por default y acepta `status=`).

## Authentication ✅

```text
POST   /api/auth/register       # solo crea el PRIMER usuario (ADMIN) del tenant
POST   /api/auth/login          # { email, password, storeId } → { token, role, ... }
POST   /api/auth/stores         # { email, password } → [{id, name}] — tiendas
                                # elegibles cuando login responde "storeId
                                # required" (roles operativos con varias
                                # asignadas; el ADMIN nunca elige — entra
                                # client-wide y filtra tiendas por página)
```

## Clients (tenants) ✅

```text
POST   /api/clients             # abierto en dev (onboarding); SUPERADMIN también lo usa
                                # + { adminName, adminEmail, adminPassword }
                                # opcionales → crea el ADMIN del tenant en el
                                # mismo commit
GET    /api/clients             # SUPERADMIN — lista todos los tenants
PUT    /api/clients/{id}        # SUPERADMIN — nombre + límites del plan
GET    /api/clients/{id}/admin  # SUPERADMIN — datos del admin del tenant
PUT    /api/clients/{id}/admin  # SUPERADMIN — {adminName, adminEmail,
                                #  adminPassword?, superadminPassword} — exige
                                #  la contraseña del superadmin; la nueva del
                                #  admin valida las mismas reglas de usuarios
GET    /api/clients/{id}
GET    /api/clients/{id}/stores
GET    /api/clients/{id}/settings   # settings del cliente (todos los roles del tenant)
PUT    /api/clients/{id}/settings   # ADMIN
```

## Stores / Establishments ✅

```text
POST   /api/stores              # ligado a un clientId
GET    /api/stores/{id}
PUT    /api/stores/{id}         # name, description, address, businessType, active
GET    /api/clients/{id}/stores # listado por cliente (la lista del admin)
POST   /api/stores/{id}/devices # alta de POS/KIOSK por tienda
GET    /api/stores/{id}/devices
GET    /api/stores/{id}/settings   # settings del establecimiento
PUT    /api/stores/{id}/settings   # ADMIN
```

- `active` default `true` al dar de alta; sin DELETE (activar/desactivar).
- Settings por establecimiento: `{ menuName, kitchenOkMin, kitchenWarnMin,
  kitchenSound }` — `menuName` vacío → se usa el `name` del store.
  **`menuName` quedó legacy** (sin input en `/settings`): el nombre que se
  muestra en pantallas y tickets es `clients.name` (Marca); el campo sigue
  aceptándose por compatibilidad.
- Settings por cliente: `{ name, logoUrl, currency, taxRate,
  kioskReceiptSeconds, sessionHours }` — `sessionHours` es
  `{ rol: horas }` (0 = no cerrar). `name`/`logoUrl` son la marca del
  tenant: `name` vacío se ignora, `logoUrl: ""` quita el logo; la
  imagen se sube con `POST /api/uploads/brand` (solo ADMIN, guarda en
  `clients/{id}/brand/` del bucket). `AuthResponse` también trae
  `clientName`/`clientLogoUrl` para pintar la marca sin otra llamada.
- Los admins sin `storeId` en su JWT ven todo el catálogo del cliente;
  los demás roles operan solo su tienda de sesión.

## Categories ✅

```text
POST   /api/categories
GET    /api/categories          # ?storeId= opcional (ADMIN puede filtrar)
GET    /api/categories/{id}
PUT    /api/categories/{id}
DELETE /api/categories/{id}
```

- **Multi-tienda**: `storeIds[]` en request/response (tabla `category_stores`),
  no un `store_id` único. Admin sin tienda debe mandar `storeIds`.
- Campos: `name`, `description`, `icon` (string, mappeado en el frontend),
  `active` (default `true`).
- `DELETE` rechaza con 400 si la categoría tiene productos enlazados —
  hay que desvincularlos primero (no hay cascada).

## Products ✅

```text
POST   /api/products
GET    /api/products            # ?storeId=&categoryId=&activeOnly=
GET    /api/products/{id}
PUT    /api/products/{id}
PATCH  /api/products/{id}/active   # activar/desactivar para caja y kiosko
PATCH  /api/products/{id}/sold-out # "86'd" — agotado temporal (ADMIN/MANAGER/EMPLOYEE)
DELETE /api/products/{id}
```

```json
// ProductRequest
{
  "categoryId": 1,
  "name": "Ramen de chashu",
  "description": "...",
  "sku": "KFR-TON",
  "image": "https://…",          // TODO: upload service (Azure Blob)
  "price": 239.00,
  "tokenCost": 0,
  "sizeIds": [1, 2],
  "extraIds": [3]
}
```

- `sku` e `image` opcionales; `tokenCost > 0` = canjeable con fichas.
- `image` viene de `POST /api/uploads` (multipart `file` → `{url}`;
  el logo del tenant usa `POST /api/uploads/brand`) —
  bucket S3-compatible (`adobe/s3mock` en compose para dev; env
  `STORAGE_*` apuntan al bucket real en producción).
- `sizes`/`extras` se resuelven en la response; asignación también desde el
  lado del catálogo vía `productIds`.
- **Orden en el menú**: la response ordena `sizes` por el orden global del
  catálogo (`sizes.position` — ver `PUT /api/sizes/order`) y `extras`
  alfabéticamente. El kiosco preselecciona `sizes[0]` = primer tamaño.

## Extras ✅ *(nuevo — catálogo a nivel cliente)*

```text
POST   /api/extras
GET    /api/extras
PUT    /api/extras/{id}
DELETE /api/extras/{id}         # desasocia de productos antes de borrar
```

```json
{ "name": "Chashu extra", "price": 25.00, "active": true, "productIds": [1, 4] }
```

## Sizes ✅ *(nuevo — catálogo a nivel cliente)*

```text
POST   /api/sizes
GET    /api/sizes                     # ordenados por position (nulls al final)
PUT    /api/sizes/{id}
PUT    /api/sizes/order               # body: [ids] en el nuevo orden → setea position
DELETE /api/sizes/{id}
```

```json
{ "name": "Grande", "price": 20.00, "active": true, "productIds": [1] }
```

- `price` es **cargo adicional** sobre el precio base (0 = mismo precio).
- Asignación se hace desde el producto (`sizeIds`) o desde el tamaño
  (`productIds`) — ambas direcciones reconcilian la misma join table.

## Users ✅

```text
POST   /api/users                    # alta (ADMIN)
GET    /api/users                    # lista del cliente (ADMIN/MANAGER)
PUT    /api/users/{id}               # editar (ADMIN) — password opcional
PATCH  /api/users/{id}/active        # activar/desactivar (ADMIN)
DELETE /api/users/{id}               # baja (ADMIN)
```

- **Todas las mutaciones exigen `currentPassword`** — la contraseña del admin
  con la sesión iniciada (re-autenticación); 401 si no coincide.
- No puedes desactivarte, eliminarte ni cambiar tu propio rol.
- `users.active = false` ⇒ login rechazado (`Invalid credentials`).
- User: `{ name, email, password?, role, storeIds[], active, currentPassword }`
- TODO: `PUT /api/users/{id}/modules` — módulos por usuario.

## Inventory ✅

```text
GET    /api/inventory/{productId}
PUT    /api/inventory/{productId}
```

## Orders ✅ *(parcial)*

```text
POST   /api/orders
GET    /api/orders              # ?storeId=&status=&page=&size=&sort= (página para historial; array para KDS)
GET    /api/orders/{id}
GET    /api/orders/{id}/ticket   # OrderResponse + storeName (menuName ?: name)
                               #  + storeAddress — payload autocontenido para
                               #  imprimir/reimprimir; cualquier rol (kiosco incl.)
PATCH  /api/orders/{id}/status
```

### OrderRequest

```json
{
  "channel": "POS | KIOSK",
  "orderType": "DINE_IN | TAKEAWAY",
  "paymentMethod": "CASH | CARD",
  "items": [
    {
      "productId": 1,
      "quantity": 2,
      "sizeId": 3,                                       // opcional — debe estar asignado al producto
      "extras": [{ "extraId": 5, "quantity": 2 }],       // opcional — idem
      "notes": "sin cebolla"                             // opcional, máx 500
    }
  ]
}
```

**Reglas implementadas:**

- `channel = KIOSK` ⇒ `paymentMethod` solo `CARD`.
- `storeId` **no va en el body** — se deriva de la sesión autenticada.
- Validación: productos activos, no agotados (`soldOut`) y pertenecientes
  al store de sesión;
  `sizeId`/`extras` deben pertenecer a las asignaciones del producto
  (si no → 400). Cada línea del request es una línea de orden — mismo
  producto con distinto tamaño/extras queda como líneas separadas.
  Si el producto tiene inventario configurado lo descuenta en la misma
  transacción (revienta si no alcanza); sin registro de inventario se
  trata como stock ilimitado — temporal hasta que aterrice el módulo de
  inventario.
- `unitPrice` lo calcula el servidor: `product.price + size.price +
  Σ(extra.price × qty)` — el cliente no puede inflar precios. Se guarda
  snapshot: `sizeName`, `extras[]` (`{name, price, quantity}` en jsonb)
  y `notes` en `order_items` — sobreviven ediciones/bajas del catálogo y
  se devuelven en `OrderItemResponse`.
- `CANCELLED` permitido hasta `PREPARING` inclusive → restaura inventario y
  `refund_status = REFUNDED` (reembolso manual por staff).
- Desglose de impuesto persistido: la orden guarda `subtotal`, `taxRate`
  (snapshot del `taxRate` del cliente al cobrar) y `taxAmount`. Los precios
  del catálogo **no** incluyen impuesto — el server lo agrega encima:
  `taxAmount = subtotal × rate/100`, `total = subtotal + taxAmount`.
  Órdenes anteriores al campo devuelven `subtotal = total`, `taxRate = 0`.
- Transiciones: `PENDING → PREPARING → READY → COMPLETED` (`PENDING` también
  admite `CONFIRMED`, pero el KDS pasa directo a `PREPARING`).
- `OrderResponse.ticketNumber` — folio semanal consecutivo por tienda,
  persistido en `orders.ticket_number` al crear (max+1 desde el lunes
  00:00, timezone del servidor; lock pesimista sobre el store evita
  duplicados entre cajas/kioscos en paralelo). Es el folio que se muestra
  en POS/KDS/`/orders` y se imprime en el ticket; órdenes anteriores al
  campo devuelven el `id` como fallback.

**Efectivo:**

- `OrderRequest.cashReceived` (solo CASH, `@PositiveOrZero`) — el server
  valida que alcance el total y calcula `changeGiven = cashReceived -
  total`. Ambos se persisten en `orders` y vuelven en
  `OrderResponse`/`TicketResponse` (el ticket impreso muestra
  Recibido/Cambio); en CARD quedan `null`.
- `GET /api/orders/{id}/ticket` → `TicketResponse` — los mismos campos de
  `OrderResponse` (incl. `ticketNumber`) más `storeName` (`menuName` del
  store si existe, si no
  `name`) y `storeAddress`. Mismo scoping por store de sesión; abierto a
  CUSTOMER para que el kiosco pueda imprimir su ticket.
- **Branding en órdenes**: `OrderResponse`/`TicketResponse` traen
  `clientName` (`clients.name`) y `logoUrl` (`clients.logo_url`) — el
  encabezado del recibo/ticket muestra la marca del negocio y el front
  cae a `storeName` si falta.

## Tokens (fichas para bebidas) *(pendiente de implementar)*

```text
POST   /api/tokens/purchase     # { quantity, paymentMethod } — venta en caja
POST   /api/tokens/redeem       # { productId, quantity } — canje en barra
```

**Reglas:** fichas físicas anónimas — no hay wallet ni cuenta de customer;
solo productos con `tokenCost > 0` son canjeables; el canje descuenta
inventario; compra y canje quedan ligados al staff autenticado que los operó.

## Reports ✅ *(ADMIN/MANAGER)*

```text
GET    /api/reports/dashboard?from=&to=&storeId=&top=   # todo en una llamada
```

Devuelve `DashboardResponse`: `totalSales`, `orders`, `avgTicket`,
`cancelledOrders`, `refundedTotal`, `channels[]`, `orderTypes[]`,
`payments[]`, `hourly[]`, `topProducts[]` (ordenado por revenue, `top=5`
default), `employees[]`. Las órdenes `CANCELLED` no cuentan en ventas;
`refundedTotal` suma las que quedaron `REFUNDED`.

- `from`/`to` = `YYYY-MM-DD` inclusive (default: hoy). Zona: `systemDefault`.
- `storeId` opcional solo para ADMIN; sin tienda en sesión y sin param,
  un ADMIN consolida todo el cliente.

## Bar *(pendiente de implementar)*

```text
GET    /api/bar/items?status=           # bebidas pendientes de órdenes y canjes
PATCH  /api/bar/items/{id}/status       # marcar entregado
```

## Tables & Reservations *(pendiente de implementar)*

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

## Módulos por cliente *(pendiente de implementar)*

```text
PUT    /api/clients/{id}/modules   # plataforma: módulos contratados
PUT    /api/users/{id}/modules     # admin: subconjunto ⊆ client_modules
GET    /api/modules/available
```
