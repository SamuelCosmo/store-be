# 13. Frontend Screens / Surfaces

El producto tiene 5 superficies de usuario. Todas consumen la misma REST API del
monolito modular.

> **Acceso por rol:** cada tipo de usuario solo ve y opera las pantallas de su
> módulo. El admin solo accede al panel de administración, cocina solo al KDS,
> caja/kiosko solo al módulo de pedidos. El frontend enruta por rol tras el
> login y el backend lo valida con `@PreAuthorize`.
> Matriz completa en [02-authentication](02-authentication.md#acceso-a-módulos-por-rol).

```text
┌──────────┐  ┌─────────────┐  ┌────────────┐  ┌─────────┐  ┌─────────────┐
│  Login   │  │ Admin Panel │  │ Caja (POS) │  │ Kiosko  │  │ Cocina(KDS) │
└────┬─────┘  └──────┬──────┘  └─────┬──────┘  └────┬────┘  └──────┬──────┘
     └───────────────┴───────┬───────┴──────────────┴──────────────┘
                             ▼
                     REST API (modular monolith)
                             ▼
                         PostgreSQL
```

## 1. Login

Pantalla de inicio de sesión para staff y admin.

- `POST /api/auth/login` → JWT
- `POST /api/auth/register` (alta de usuarios)
- Redirige según rol: `ADMIN` → Panel Admin, `EMPLOYEE` → Caja, `CUSTOMER` →
  Kiosko

## 2. Panel Admin (paquete básico)

El administrador del cliente gestiona el catálogo de sus establecimientos.

Funciones:

- Alta/baja de **categorías** → `POST/PUT/DELETE /api/categories`
- Alta/baja de **productos** y precios → `POST/PUT/DELETE /api/products`
- **Activar/desactivar** productos → `PATCH /api/products/{id}/active`
  (solo los activos se muestran en caja y kiosko)
- Configurar **inventario** → `PUT /api/inventory/{productId}`
- Gestión de **establecimientos** del cliente → `POST/PUT /api/stores`

Rol: `ADMIN` (y `MANAGER` para operación del catálogo).

## 3. Caja (POS)

Pantalla operada por staff para tomar órdenes en el mostrador.

- Navega categorías y productos (solo activos)
- Arma la orden agregando productos
- Tipo de orden: **Comer aquí** (`DINE_IN`) o **Para llevar** (`TAKEAWAY`)
- Pago: **efectivo** (`CASH`) o **tarjeta** (`CARD`)
- Envía `POST /api/orders` con `channel: POS`

Rol: `EMPLOYEE` / `MANAGER`.

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

## 5. Pantalla de cocina (KDS)

Recibe las órdenes del establecimiento y muestra su estado en tiempo real.

- Lista órdenes: `GET /api/orders?storeId=&status=`
- Botones de acción por orden:
  - **En preparación** → `PATCH .../status` con `PREPARING`
  - **Listo** → `PATCH .../status` con `READY`
  - **Cancelar** → `PATCH .../status` con `CANCELLED` (restaura inventario)

Rol: `EMPLOYEE` / `MANAGER` (cocina).

## Resumen por superficie

| Superficie  | Rol             | Endpoints principales                              |
| ----------- | --------------- | -------------------------------------------------- |
| Login       | todos           | `POST /api/auth/login`, `register`                 |
| Admin Panel | ADMIN, MANAGER  | CRUD stores/categories/products, inventory, active |
| Caja (POS)  | EMPLOYEE        | `POST /api/orders` (CASH/CARD, DINE_IN/TAKEAWAY)   |
| Kiosko      | CUSTOMER        | `POST /api/orders` (solo CARD)                     |
| Cocina      | EMPLOYEE        | `GET /api/orders`, `PATCH .../status`              |
