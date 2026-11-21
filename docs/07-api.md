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
POST   /api/auth/register       # SUPERADMIN — crea el PRIMER usuario (ADMIN)
                                # de un tenant que nació sin admin
POST   /api/auth/login          # { email, password, storeId } → AuthResponse
                                # con `token` (JWT) — el front lo manda como
                                # Bearer. Rechaza SUPERADMIN (usa su endpoint)
POST   /api/auth/superadmin     # { email, password } → AuthResponse — SOLO
                                # cuentas de plataforma; lo consume /superadmin
POST   /api/auth/stores         # { email, password } → [{id, name}] — tiendas
                                # elegibles cuando login responde "storeId
                                # required" (roles operativos con varias
                                # asignadas; el ADMIN nunca elige — entra
                                # client-wide y filtra tiendas por página)
```

Todas las requests autenticadas viajan con `Authorization: Bearer <jwt>`
(ver `02-authentication.md`).

**Rate limiting** (ventanas fijas en memoria — una instancia; multi-réplica
requeriría base/Redis):
- `login`, `superadmin` y `stores` cuentan **intentos fallidos**: 5 por
  correo o 30 por IP en 10 min → **429 `RATE_LIMITED`**. Un login exitoso
  reinicia el contador del correo.
- `POST /api/logs`: 60 eventos/min por IP — el exceso se descarta en
  silencio (204), sin 429, para no generar una línea WARN por request.

## Clients (tenants) ✅

```text
POST   /api/clients             # SUPERADMIN — alta de tenant
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
PATCH  /api/clients/{id}/active # SUPERADMIN — {active, superadminPassword}
                                #  suspende/reactiva el tenant sin borrar datos;
                                #  bloquea login (CLIENT_SUSPENDED) y sesiones
                                #  abiertas al instante (401 en JwtAuthFilter)
GET    /api/clients/{id}
GET    /api/clients/{id}/stores
GET    /api/clients/{id}/settings   # settings del cliente (todos los roles del tenant)
PUT    /api/clients/{id}/settings   # ADMIN
```

## Stores / Establishments ✅

```text
POST   /api/stores              # ligado a un clientId
GET    /api/stores/{id}
PUT    /api/stores/{id}         # name, description, address, businessType, active,
                              #  logoUrl, primaryColor, accentColor, canvasColor
GET    /api/clients/{id}/stores # listado por cliente (la lista del admin; ?deleted=true → solo borradas)
POST   /api/stores/{id}/devices # alta de POS/KIOSK por tienda
GET    /api/stores/{id}/devices
GET    /api/stores/{id}/settings   # settings del establecimiento
PUT    /api/stores/{id}/settings   # ADMIN
DELETE /api/stores/{id}            # baja lógica (ADMIN) — las órdenes históricas la conservan
POST   /api/stores/{id}/restore    # ADMIN
```

- `active` default `true` al dar de alta.
- **Marca por establecimiento**: `logoUrl` + `primaryColor`, `accentColor`,
  `canvasColor` (`#rrggbb`). En el PUT un campo ausente se conserva y `""`
  lo quita; el logo reemplazado se borra del bucket. Los llevan
  `StoreResponse` y `StoreOption` (`/api/users/me/stores`, `/api/auth/stores`),
  así las terminales aplican logo + paleta sin otra llamada — el front deriva
  la variante nocturna a partir de esos colores. Sin marca → tema base /
  logo del tenant. `OrderResponse`/`TicketResponse` exponen `storeName`
  (`menuName ?: name`) y `logoUrl = store.logoUrl ?: client.logo_url`.
- Settings por establecimiento: `{ menuName, kitchenOkMin, kitchenWarnMin,
  kitchenSound }` — `menuName` vacío → se usa el `name` del store.
  Validación: minutos 1–240, `menuName` ≤ 60, y `kitchenWarnMin >
  kitchenOkMin` sobre el valor resultante (400 si no).
- Settings por cliente: `{ name, logoUrl, currency, taxRate,
  kioskReceiptSeconds, sessionHours, tokenPrice, timezone }` — `sessionHours` es
  `{ rol: horas }` (0 = no cerrar). Validación (400): `currency` ISO 4217
  real, `taxRate` 0–100, `tokenPrice` ≥ 0, `kioskReceiptSeconds` 3–60,
  `sessionHours` solo roles del tenant y 0–720 h, `timezone` zona IANA.
  `timezone` (default `America/Mexico_City`) define el "hoy" del
  dashboard y de `/api/tokens`, la hora de la gráfica por hora y el lunes
  en que reinicia el folio semanal — el server corre en UTC. `name`/`logoUrl` son la marca del
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
DELETE /api/users/{id}               # baja (ADMIN) — soft delete
POST   /api/users/{id}/restore       # ADMIN — recupera el email original (409 si ya se re-usó)
```

- `GET /api/users?deleted=true` lista solo los borrados.
- **Todas las mutaciones exigen `currentPassword`** — la contraseña del admin
  con la sesión iniciada (re-autenticación); 401 si no coincide.
  (Restore no la pide — la fila nunca se destruyó).
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
POST   /api/orders              # ADMIN/MANAGER/EMPLOYEE/CUSTOMER (cocina no vende)
                               #  header opcional Idempotency-Key ([A-Za-z0-9-]{8,64})
GET    /api/orders              # staff (no CUSTOMER) — ?storeId=&status=&page=&size=&sort=
                               #  (página para historial; array para KDS)
                               #  `status` admite varios separados por coma:
                               #  ?status=PENDING,CONFIRMED,PREPARING,READY
GET    /api/orders/{id}         # staff
GET    /api/orders/{id}/ticket   # staff — OrderResponse + storeName (menuName ?: name)
                               #  + storeAddress — payload autocontenido para reimprimir.
                               #  El kiosco arma su ticket con la respuesta del POST.
PATCH  /api/orders/{id}/status
```

**Idempotency-Key:** un id por cobro. Reintentar `POST /api/orders` con el
mismo key en la misma tienda devuelve la orden ya creada (sin descontar
inventario de nuevo) en vez de duplicarla — cubre el caso "se cortó la red
justo después de cobrar". El front genera uno nuevo cada vez que cambia el
carrito. Unique `(store_id, idempotency_key)`.

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
- `CANCELLED` permitido hasta `PREPARING` inclusive → restaura inventario.
  `PATCH /status` acepta `{ status, refunded }`: con `refunded=true` y pago
  CASH/CARD queda `refund_status = REFUNDED` (el staff devolvió el dinero);
  si no → `NONE`. Órdenes TOKEN ($0) nunca cuentan como reembolso. Se
  registra `cancelled_by`/`cancelled_at` y vuelven como
  `cancelledBy`/`cancelledAt` en `OrderResponse`.
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

## Tokens (fichas para bebidas) ✅

```text
POST   /api/tokens              # { quantity, paymentMethod: CASH|CARD } — venta en caja
GET    /api/tokens              # ?storeId=&from=&to= — ventas del rango (default hoy)
```

- **Venta**: `POST /api/tokens` (ADMIN/MANAGER/EMPLOYEE con tienda de sesión;
  nunca kiosco) crea un `TokenSale` — cobra `quantity × clients.tokenPrice`
  (snapshot en `unit_price`/`total_price`). 400 si `tokenPrice` no está
  configurado (≤ 0) — el POS oculta el botón en ese caso.
- **Canje**: NO es endpoint propio — es una orden normal
  (`POST /api/orders`) con `paymentMethod: TOKEN`: `total = 0`, se descuenta
  `tokenCost` por producto y `OrderResponse.tokenAmount` trae el total de
  fichas (`order_items.token_cost` es el snapshot por ítem). Solo caja — el
  kiosco no acepta TOKEN (guard de canal).
- **Reglas:** fichas físicas anónimas — no hay wallet ni cuenta de customer;
  solo productos con `tokenCost > 0` son canjeables (si el pedido mezcla
  canjeables con no-canjeables el pago debe ser CASH/CARD); venta y canje
  quedan ligados al staff autenticado que los operó.

## Borrado lógico y restauración

Catálogos (`categories`, `products`, `extras`, `sizes`), `users` y `stores`:

```text
GET    /api/{recurso}?deleted=true    # lista SOLO los borrados
POST   /api/{recurso}/{id}/restore    # reactiva la fila
```

- Los `DELETE` marcan `deleted_at` (soft delete) — nada se borra físicamente.
- Restaurar un usuario recupera su email original (el delete lo reescribe con
  sufijo `.deleted-{id}` para liberar el UNIQUE); 409 si ya fue re-registrado.
- Restaurar un extra/tamaño no lo re-vincula a productos — hay que reasignarlo.

## Reports ✅ *(ADMIN/MANAGER)*

```text
GET    /api/reports/dashboard?from=&to=&storeId=&top=   # todo en una llamada
```

Devuelve `DashboardResponse`: `totalSales`, `tokenSales` (dinero por venta
de fichas en el rango), `tokensSold` (piezas), `orders`, `avgTicket`,
`cancelledOrders`, `refundedTotal`, `channels[]`, `orderTypes[]`,
`payments[]`, `hourly[]`, `topProducts[]` (ordenado por revenue, `top=5`
default), `employees[]` (incluye la cuenta del kiosco — es venta real).
Las órdenes `CANCELLED` no cuentan en ventas; `refundedTotal` suma las que
quedaron `REFUNDED`.

- `from`/`to` = `YYYY-MM-DD` inclusive (default: hoy). Zona: `clients.timezone`.
- `storeId` opcional solo para ADMIN; sin tienda en sesión y sin param,
  un ADMIN consolida todo el cliente.

## Audit ✅ *(ADMIN/SUPERADMIN)*

```text
GET    /api/audit?clientId=&entityType=&action=&storeId=&from=&to=&page=&size=&sort=
```

Bitácora de cambios de negocio — las mutaciones relevantes (catálogo,
stores, usuarios, clientes, órdenes, ventas de fichas, inventario,
dispositivos) escriben un `audit_events` dentro de la misma transacción:
si la operación hace rollback, el evento también desaparece.

- Siempre paginado (`Page<AuditEventResponse>`); sin `sort` ordena
  `createdAt desc`.
- Filtros opcionales: `entityType` (`product`, `order`, `token_sale`, …),
  `action` (`create`, `update`, `delete`, `cancel`, `settings`, …),
  `storeId`, `from`/`to` (ISO instant).
- **Scope forzado en backend:** ADMIN solo ve su tenant — un `clientId`
  arbitrario en el query se ignora. SUPERADMIN puede filtrar por
  `clientId` o ver todos los tenants (sin param).
- `details` = snapshot del alta o diff `{campo: {from, to}}` en updates;
  nunca contiene passwords, tokens ni secretos.
- El actor viaja como snapshot (`actorId`/`actorName`/`actorEmail`/
  `actorRole`) — el historial sobrevive a cambios o baja del usuario.

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

## Módulos por cliente ✅ *(HP-MT-01)*

Catálogo: `CATALOG, INVENTORY, POS, KIOSK, KDS, BAR, TOKENS, REPORTS,
TICKETS, RESERVATIONS` (`GET /api/modules` los lista a cualquier
autenticado). Reglas (docs/13): `client_modules` = lo que la plataforma
vendió; `user_modules ⊆ client_modules` = lo que el ADMIN asigna.
`GET /api/modules/available` devuelve los contratados del tenant actual.

```text
PUT    /api/clients/{id}/modules   # SUPERADMIN: módulos contratados {modules:[...]}
PUT    /api/users/{id}/modules     # ADMIN: asignación ⊆ client_modules {modules:[...]}
GET    /api/modules                # catálogo completo
GET    /api/modules/me             # efectivos del usuario actual
GET    /api/modules/available      # ADMIN: contratados del tenant propio
```

- Ambos PUT exigen ≥1 módulo (`MODULES_REQUIRED`) y devuelven `List<String>`.
- `PUT /api/users/{id}/modules` responde 404 si el usuario es de otro
  tenant y `MODULE_NOT_PURCHASED` si se pide un módulo no contratado.
- Los módulos llegan al front en `AuthResponse.modules` (efectivos),
  `UserResponse.modules`, `ClientResponse.modules` (contratados) y en el
  header `X-Effective-Modules` de cada respuesta autenticada (sincronización
  en caliente sin re-login).
- Enforcement: `JwtAuthFilter` agrega authorities `MODULE_<NAME>` por
  request (frescos desde DB, sin claims stale en el JWT); los controllers
  combinan `hasRole(...)` con `hasAuthority('MODULE_X')`. Roles: ADMIN →
  todos los del cliente; demás roles → `user_modules ∩ client_modules`;
  SUPERADMIN → todos.
- **Convención legacy**: colección vacía = "nunca configurado" → equivale
  al catálogo completo. Los PUT rechazan vacío, así que no hay ambigüedad
  y no hace falta backfill.
- Auditoría: cada cambio de módulos registra evento `client|user /
  modules` con el detalle.
