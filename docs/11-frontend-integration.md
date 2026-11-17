# 14. Frontend Integration Guide

Guía para el repositorio del frontend (React + Next.js) que consumirá esta API.

## Stack recomendado

- **Next.js + React** (App Router)
- **No se necesita Express** — Express es un framework de backend en Node.js;
  este backend (Spring Boot) ya es el servidor. El frontend solo hace llamadas
  HTTP.
- HTTP client opciones:
  - `fetch` nativo — suficiente para empezar
  - `axios` — interceptores cómodos para adjuntar el JWT
  - `TanStack Query` / `SWR` — recomendado para cache, loading/error states
    (ideal para el KDS que hace polling de órdenes)
- Route Handlers de Next.js (`app/api/...`) — no hacen falta: el front
  guarda el JWT de la respuesta de login y lo manda como Bearer

## Base URL

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Autenticación (JWT)

```text
POST /api/auth/login
  → AuthResponse con `token` (JWT)

Siguientes requests:
  Authorization: Bearer <token>   (el front lo guarda en la sesión local)

Logout:
  borrar la sesión local (no hay endpoint — para revocar en el server
  se sube users.token_version, ver 02-authentication.md)
```

- El front guarda la sesión en localStorage (token + userId, role,
  storeId, loginAt para el TTL por rol).
- Tras login, **enrutar por rol** a su módulo — matriz en
  [02-authentication](02-authentication.md#acceso-a-módulos-por-rol).

## Pantallas a construir

Mapeo completo en [10-frontend-screens](10-frontend-screens.md). Rutas
implementadas en `store-fe` (route group `app/(panel)/` comparte el sidebar
sin prefijo en la URL):

| Pantalla         | Ruta        | Estado | Consume                                     |
| ---------------- | ----------- | ------ | ------------------------------------------- |
| Login            | `/`         | ✅     | `POST /api/auth/login`                      |
| Admin Panel      | `/dashboard` | ✅ parcial | landing; faltan métricas                |
| Categorías       | `/categories` | ✅   | CRUD `/api/categories` (multi-tienda)       |
| Productos        | `/products` | ✅     | CRUD `/api/products` + `/api/extras`, `/api/sizes` |
| Establecimientos | `/stores`   | ✅     | CRUD `/api/stores` + `/api/clients/{id}/stores` |
| Caja (POS)       | `/pos`      | placeholder | `POST /api/orders` (CASH/CARD)              |
| Kiosko           | `/kiosk`    | placeholder | `POST /api/orders` (solo CARD; sesión de kiosko ligada al establecimiento) |
| Cocina (KDS)     | `/kitchen`  | placeholder | `GET /api/orders?storeId=&status=` + PATCH  |
| Barra *(extra)*  | `/bar`      | pendiente | `GET /api/bar/items` + PATCH status       |

## Contrato de API

Referencia completa: [07-api](07-api.md). Durante desarrollo también sirve
Swagger UI en `http://localhost:8080/swagger-ui.html`.

## Estado del backend para soportar el frontend

- [x] **CORS** — `http://localhost:3000` permitido en `SecurityConfig`
- [x] `Client` (tenant) + endpoints `/api/clients/**`
- [x] `Order` con `channel`, `orderType`, `paymentMethod` + regla KIOSK→CARD
- [x] `PATCH /api/products/{id}/active`
- [x] `GET /api/orders` filtro por `status` (para el KDS)
- [x] Catálogos `/api/extras` y `/api/sizes` + `sku`/`image` en productos
- [ ] Items de orden con `sizeId`/`extraIds`
- [ ] `cashReceived`/`changeGiven` en `POST /api/orders` (efectivo)
- [ ] `GET /api/orders/{id}/ticket`
- [ ] Tokens, barra, reportes, mesas/reservaciones, módulos por usuario

## Formato de errores

Todos los errores llegan como `ErrorResponse`:

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "code": "OUT_OF_STOCK",
  "message": "Insufficient stock for product: Espresso",
  "params": { "product": "Espresso" },
  "path": "/api/orders",
  "fieldErrors": { "name": "must not be blank" }
}
```

- **`code`** es estable — el front lo traduce (`err.<CODE>` en
  `store-fe/lib/i18n/*.json`, con `params` como variables). Nunca mostrar
  `message` al usuario: es técnico, en inglés, para logs/soporte.
- Códigos genéricos: `VALIDATION` (400), `INVALID_CREDENTIALS` (401 —
  contraseña equivocada, **no** cierra sesión), `FORBIDDEN` (403),
  `NOT_FOUND` (404), `CONCURRENT_UPDATE` (409 — otro usuario cambió la
  misma orden), `RATE_LIMITED` (429 — ver rate limiting en `07-api.md`),
  `SERVER_ERROR` (500). Un 401 **sin** code = token
  vencido/revocado → cerrar sesión.
- Al agregar un `BusinessException(code, …)` nuevo, agregar también
  `err.<CODE>` en es/en del front.
