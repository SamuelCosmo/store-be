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
- Route Handlers de Next.js (`app/api/...`) — opcional, solo si se quiere
  proxy/BFF para ocultar el token en cookies httpOnly

## Base URL

```env
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## Autenticación (JWT)

```text
POST /api/auth/login
  → { "token": "...", "role": "ADMIN" | ... }

Siguientes requests:
  Authorization: Bearer <token>
```

- Guardar el token en `localStorage` (MVP) o en cookie httpOnly via Route
  Handler (más seguro, siguiente iteración).
- Tras login, **enrutar por rol** a su módulo — matriz en
  [02-authentication](02-authentication.md#acceso-a-módulos-por-rol).

## Pantallas a construir

Mapeo completo en [10-frontend-screens](10-frontend-screens.md):

| Pantalla         | Ruta sugerida      | Consume                                     |
| ---------------- | ------------------ | ------------------------------------------- |
| Login            | `/login`           | `POST /api/auth/login`                      |
| Admin Panel      | `/admin`           | CRUD stores/categories/products, inventory  |
| Caja (POS)       | `/pos`             | `POST /api/orders` (CASH/CARD)              |
| Kiosko           | `/kiosk`           | `POST /api/orders` (solo CARD; sesión de kiosko ligada al establecimiento) |
| Cocina (KDS)     | `/kitchen`         | `GET /api/orders?storeId=&status=` + PATCH  |
| Barra *(extra)*  | `/bar`             | `GET /api/bar/items` + PATCH status       |

## Contrato de API

Referencia completa: [07-api](07-api.md). Durante desarrollo también sirve
Swagger UI en `http://localhost:8080/swagger-ui.html`.

## Pendientes en el backend para soportar el frontend

- [ ] **CORS** — permitir origen `http://localhost:3000` (dev de Next.js) en
      `SecurityConfig`
- [ ] `Client` (tenant) + endpoints `/api/clients/**`
- [ ] `Order` con `channel`, `orderType`, `paymentMethod` + regla KIOSK→CARD
- [ ] `PATCH /api/products/{id}/active`
- [ ] `GET /api/orders` filtro por `status` (para el KDS)

## Formato de errores

Todos los errores llegan como `ErrorResponse`:

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product: Espresso",
  "path": "/api/orders",
  "fieldErrors": { "name": "must not be blank" }
}
```
