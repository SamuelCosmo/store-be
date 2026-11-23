# 13. Frontend Screens / Surfaces

El producto tiene 6 superficies: **Login**, **Panel Admin**, **Caja (POS)**,
**Kiosko**, **Cocina (KDS)** y **SUPERADMIN** (operación de la plataforma).
**Barra** queda planeada como extra del cliente. Todas consumen la misma REST
API del monolito modular.

> **Acceso por rol:** cada tipo de usuario solo ve y opera las pantallas de su
> módulo. El admin solo accede al panel de administración, cocina solo al KDS,
> caja/kiosko solo al módulo de pedidos. El frontend enruta por rol tras el
> login y el backend lo valida con `@PreAuthorize`.
> Matriz completa en [02-authentication](02-authentication.md#acceso-a-módulos-por-rol).

```text
┌──────────┐  ┌─────────────┐  ┌────────────┐  ┌─────────┐  ┌─────────────┐  ┌────────────┐
│  Login   │  │ Admin Panel │  │ Caja (POS) │  │ Kiosko  │  │ Cocina(KDS) │  │ SUPERADMIN │
└────┬─────┘  └──────┬──────┘  └─────┬──────┘  └────┬────┘  └──────┬──────┘  └─────┬──────┘
     └───────────────┴───────┬───────┴──────────────┴──────────────┴─────────────┘
                             ▼
                     REST API (modular monolith)
                             ▼
                         PostgreSQL
```

## 1. Login

Pantalla de inicio de sesión para staff y admin.

- `POST /api/auth/login` → JWT
- `POST /api/auth/register` (alta de usuarios)
- Redirige según rol + módulos: `ADMIN`/`MANAGER` → Panel Admin, `EMPLOYEE` →
  Caja o KDS según módulos asignados, `CUSTOMER` (cuenta genérica) → Kiosko,
  `KITCHEN` → KDS (puede actualizar estatus de órdenes: PLACED → PREPARING →
  READY)

## 2. Panel Admin (paquete básico)

El administrador del cliente gestiona el catálogo de sus establecimientos.

Funciones:

- **Dashboard** → ventas del día, productos top y tiempos de cocina
  (`GET /api/reports/dashboard`)
- **Órdenes** → historial con detalle y reimpresión de ticket
  (`GET /api/orders`, `GET /api/orders/{id}/ticket`)
- Alta/baja de **categorías** → `POST/PUT/DELETE /api/categories`
- Alta/baja de **productos** y precios → `POST/PUT/DELETE /api/products`
  (con `sku`, `image`, `tokenCost`; extras/tamaños se asignan desde aquí)
- Catálogo de **extras** → `POST/PUT/DELETE /api/extras`, asignables a
  productos N:M
- Catálogo de **tamaños** → `POST/PUT/DELETE /api/sizes`, asignables a
  productos N:M (`price` = cargo adicional)
- **Activar/desactivar** productos → `PATCH /api/products/{id}/active`
  (solo los activos se muestran en caja y kiosko)
- Configurar **inventario** → `PUT /api/inventory/{productId}`
- Gestión de **establecimientos** del cliente → `POST/PUT /api/stores`
  (con marca propia por tienda: logo y colores)
- Gestión de **usuarios** → `POST/PUT/DELETE /api/users`, roles, tiendas
  asignadas y módulos por usuario (`PUT /api/users/{id}/modules`)
- **Settings** → `PUT /api/clients/{id}/settings` y settings por tienda:
  moneda, impuesto, precio de ficha, marca, tiempos KDS, auto-impresión
  (flag por equipo, localStorage)
- **Auditoría** → `GET /api/audit` — quién tocó qué y cuándo en el tenant
- **Mesas y reservaciones** *(planeado)* → `POST /api/tables`,
  `GET/POST/PATCH /api/reservations`

Rol: `ADMIN` (y `MANAGER` para operación del catálogo).

## 3. Caja (POS)

Pantalla operada por staff para tomar órdenes en el mostrador.

- Navega categorías y productos (solo activos)
- Arma la orden agregando productos
- Tipo de orden: **Comer aquí** (`DINE_IN`) o **Para llevar** (`TAKEAWAY`)
- Pago: **efectivo** (`CASH`) o **tarjeta** (`CARD`)
- Envía `POST /api/orders` con `channel: POS`
- **Cancelar orden** (hasta `PREPARING`) → `PATCH /api/orders/{id}/status`
  con `CANCELLED`; restaura inventario. Si estaba pagada, el staff devuelve
  el dinero manualmente y la orden queda `refund_status = REFUNDED`
- **Venta de fichas** *(extra del cliente)* → `POST /api/tokens`
  `{quantity, paymentMethod}` (cobra `quantity × clients.tokenPrice`)
- **Menú de fichas**: si hay productos con `tokenCost > 0`, un toggle alterna
  el grid a modo canje (cobra con `paymentMethod: TOKEN`)
- **Selector de tienda** para `ADMIN`/`MANAGER` con varias asignadas — cambiar
  limpia el carrito
- **86'd**: toggle de agotado por tarjeta → `PATCH /api/products/{id}/sold-out`
- **Pantalla de espera** tras 3 min sin tocar (continuar o nueva venta)
- **Imprime ticket** de la orden → `GET /api/orders/{id}/ticket`
  (auto-impresión térmica si el flag por equipo está activo)

Rol: `EMPLOYEE` / `MANAGER` / `ADMIN`.

## 4. Kiosko (self-service)

El cliente final hace su propia orden. El kiosko es un dispositivo **ligado a
un establecimiento**: el staff lo activa con un login y la sesión (JWT) queda
enlazada a ese `storeId`.

- **Activación:** login de staff o cuenta de kiosko del establecimiento
- Mismo flujo que caja: categorías → productos → carrito
- Tipo de orden: `DINE_IN` / `TAKEAWAY`
- Pago: **solo tarjeta** (`CARD`) — regla de negocio
- Envía `POST /api/orders` con `channel: KIOSK`; el `storeId` sale del token,
  no del body — así las órdenes siempre quedan enlazadas al establecimiento
  correcto
- El cliente final no se loguea; opera sobre la sesión del kiosko
- **Attract screen** tras 90 s sin tocar — al despertar, la terminal se
  reinicia sola (carrito y modales se descartan)

## 5. Pantalla de cocina (KDS)

Recibe las órdenes del establecimiento y muestra su estado en tiempo real.

- Lista órdenes: `GET /api/orders?storeId=&status=`
- Botones de acción por orden:
  - **En preparación** → `PATCH .../status` con `PREPARING`
  - **Listo** → `PATCH .../status` con `READY`
  - **Cancelar** → `PATCH .../status` con `CANCELLED` (hasta `PREPARING`;
    restaura inventario)

Rol: `EMPLOYEE` / `MANAGER` (cocina).

## 6. SUPERADMIN

Operación de la plataforma — no pertenece a ningún tenant (`client_id` null).

- Lista/crea/edita **clientes** → `GET/POST/PUT /api/clients`
- **Suspender/reactivar** tenant → `PATCH /api/clients/{id}/active` (con
  contraseña de superadmin; bloquea login y JWTs vigentes al instante)
- **Módulos contratados** por cliente → `PUT /api/clients/{id}/modules`
- Tiendas de cada cliente → `GET /api/clients/{id}/stores`
- **Auditoría global** → `GET /api/audit?clientId=` (puede filtrar por tenant)
- Login propio → `POST /api/auth/superadmin`
- Único rol con acceso a Swagger/OpenAPI

## 7. Barra *(planeada — extra del cliente)*

Pantalla del bartender, análoga al KDS: items de bebida de las órdenes
confirmadas y canjes de fichas pendientes de entregar.

- Lista items de bebida: `GET /api/bar/items?status=`
- Marca item entregado: `PATCH /api/bar/items/{id}/status`
- Canje de fichas por bebida: orden con `paymentMethod: TOKEN`
  (`POST /api/orders`, `total = 0`, `tokenAmount` = fichas)

## Resumen por superficie

| Superficie  | Rol             | Endpoints principales                              |
| ----------- | --------------- | -------------------------------------------------- |
| Login       | todos           | `POST /api/auth/login`, `register`                 |
| Admin Panel | ADMIN, MANAGER  | dashboard, órdenes, CRUD catálogo/stores/users,    |
|             |                 | inventory, settings, auditoría                     |
| Caja (POS)  | EMPLOYEE/MGR/   | `POST /api/orders` (CASH/CARD/TOKEN), venta de     |
|             | ADMIN           | fichas, 86'd, selector de tienda, ticket           |
| Kiosko      | CUSTOMER        | `POST /api/orders` (solo CARD), idle auto-reset    |
| Cocina      | EMPLOYEE        | `GET /api/orders`, `PATCH .../status`              |
| SUPERADMIN  | SUPERADMIN      | CRUD clients, suspensión, módulos, auditoría       |
| Barra       | EMPLOYEE        | *(planeada)* `GET /api/bar/items`, canjes          |
