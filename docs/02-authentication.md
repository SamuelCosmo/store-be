# 2. Authentication

## Store Owner / Employee

```text
User
 ↓
Login screen (pantalla de inicio de sesión)
 ↓
Register / Login
 ↓
Receive JWT
 ↓
Access protected resources
```

## Transporte del token: `Authorization: Bearer`

El JWT llega en el body del login (`AuthResponse.token`) y el cliente lo
envía como `Authorization: Bearer <jwt>` en cada request autenticada:

- `POST /api/auth/login` y `/api/auth/superadmin` devuelven
  `AuthResponse` con `token`.
- `POST /api/auth/logout` es público e idempotente — quedó para limpiar
  cookies `store_token` de una versión previa que las usaba; la sesión
  real muere al borrar el token del cliente.
- `JwtAuthFilter` lee solo el header `Bearer`.

Nota: un usuario desactivado/eliminado con token vigente conserva acceso
hasta su `exp` — el filtro no consulta la DB por request (mismo alcance
que `active=false`).

Tras el login, el frontend redirige según el rol del usuario:

- `ADMIN` / `MANAGER` → Panel Admin
- `EMPLOYEE` → Caja (POS) o Pantalla de cocina (KDS)
- `CUSTOMER` → Kiosko
- `SUPERADMIN` → `/superadmin` (administración de la plataforma)

## Roles

- `ADMIN`
- `MANAGER`
- `EMPLOYEE`
- `CUSTOMER`
- `SUPERADMIN` — cuenta de plataforma: `users.client_id` es `NULL`, el JWT
  no lleva `clientId` ni `storeId`, y solo accede a `/api/clients`
  (listar/editar plan). Se crea por SQL directo, no por la app.

Un usuario pertenece a un **cliente** (`client_id`) y opera uno o varios de
sus establecimientos (`user_stores`) — ej. un ADMIN puede administrar todos
los stores del cliente; un EMPLOYEE puede trabajar en varios. La cuenta
genérica del kiosko queda ligada a un solo establecimiento.

## Acceso a módulos por rol

Cada tipo de usuario solo ve y opera los módulos/pantallas que le corresponden.
El frontend oculta las rutas no autorizadas y el backend las refuerza con
`@PreAuthorize` (doble barrera: UX + seguridad real).

> **Modelo modular:** los módulos se venden por cliente y el admin del
> cliente asigna subconjuntos a sus usuarios (`user_modules ⊆
> client_modules`). La matriz de abajo es el **preset del giro restaurante**;
> ver [13-modules-and-permissions](13-modules-and-permissions.md).

| Módulo / Pantalla      | ADMIN | MANAGER | EMPLOYEE | CUSTOMER | KITCHEN |
| ---------------------- | :---: | :-----: | :------: | :------: | :-----: |
| Login                  |  ✓    |    ✓    |    ✓     |    ✓     |    ✓    |
| Panel Admin (catálogo) |  ✓    |    ✓    |    —     |    —     |    —    |
| Reportes (ventas)      |  ✓    |    ✓    |    —     |    —     |    —    |
| Caja (POS)             |  ✓    |    ✓    |    ✓     |    —     |    —    |
| Kiosko (pedidos)       |  —    |    —    |    —     |    ✓     |    —    |
| Cocina (KDS)           |  —    |    ✓    |    ✓     |    —     |    ✓    |
| Barra                  |  —    |    ✓    |    ✓     |    —     |    —    |
| Reservaciones          |  ✓    |    ✓    |    ✓     |    —     |    —    |
| Fichas (compra/canje)  |  ✓    |    ✓    |    ✓     |    —     |    —    |

Ejemplos de la regla:

- El `ADMIN` solo ve pantallas de administrador (catálogo, establecimientos,
  inventario) — además puede operar la caja.
- El personal de **cocina** solo ve el KDS (`GET /api/orders` +
  `PATCH .../status`).
- **Caja** y **kiosko** solo ven el módulo de pedidos; el kiosko además está
  limitado a pago con tarjeta.
- Un `CUSTOMER` no puede acceder al panel admin ni a la caja de otro canal.

## Sesión de kiosko

El kiosko es un dispositivo físico **ligado a un establecimiento**, por lo que
necesita una sesión autenticada:

1. El staff activa el kiosko haciendo login (cuenta de kiosko o credenciales
   de `EMPLOYEE`/`MANAGER` del establecimiento).
2. El JWT queda ligado al `storeId` del establecimiento.
3. Todas las órdenes creadas desde ese kiosko se enlazan automáticamente a
   ese establecimiento — el `storeId` se deriva del token, **no del body**
   del request (evita que un kiosko cree órdenes en establecimientos ajenos).
4. El cliente final que usa el kiosko no necesita cuenta propia — la orden
   queda asociada a la sesión del kiosko.

Si un usuario opera varios establecimientos (`user_stores`), el `storeId`
activo se elige al hacer login y viaja como claim en el JWT; el backend
valida que el store pedido pertenezca a su `user_stores`.

Decisión: **cuenta `CUSTOMER` genérica por establecimiento** — el staff activa
el kiosko con ese login (ej. `kiosk-sucursal1@cosmo.com`) y la sesión queda
ligada al `storeId`. Iteración futura si hace falta: entidad `Kiosk` con
device registration y token propio.

## Endpoints

```text
POST   /api/auth/register       # SUPERADMIN — primer ADMIN de un tenant sin admin
POST   /api/auth/login          # tenants — rechaza SUPERADMIN
POST   /api/auth/superadmin     # solo cuentas de plataforma
```
