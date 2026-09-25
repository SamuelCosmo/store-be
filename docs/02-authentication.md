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

Tras el login, el frontend redirige según el rol del usuario:

- `ADMIN` / `MANAGER` → Panel Admin
- `EMPLOYEE` → Caja (POS) o Pantalla de cocina (KDS)
- `CUSTOMER` → Kiosko

## Roles

- `ADMIN`
- `MANAGER`
- `EMPLOYEE`
- `CUSTOMER`

## Acceso a módulos por rol

Cada tipo de usuario solo ve y opera los módulos/pantallas que le corresponden.
El frontend oculta las rutas no autorizadas y el backend las refuerza con
`@PreAuthorize` (doble barrera: UX + seguridad real).

| Módulo / Pantalla      | ADMIN | MANAGER | EMPLOYEE | CUSTOMER |
| ---------------------- | :---: | :-----: | :------: | :------: |
| Login                  |  ✓    |    ✓    |    ✓     |    ✓     |
| Panel Admin (catálogo) |  ✓    |    ✓    |    —     |    —     |
| Caja (POS)             |  ✓    |    ✓    |    ✓     |    —     |
| Kiosko (pedidos)       |  —    |    —    |    —     |    ✓     |
| Cocina (KDS)           |  —    |    ✓    |    ✓     |    —     |

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

Opciones de implementación (decidir al construirlo):

- **Cuenta de kiosko** — un `User` por kiosko con `store` asignado
  (ej. `kiosk-sucursal1@cosmo.com`).
- **Device registration** — entidad `Kiosk` con token propio enlazado al
  establecimiento (más robusto, siguiente iteración).

## Endpoints

```text
POST   /api/auth/register
POST   /api/auth/login
```
