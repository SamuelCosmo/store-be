# 16. Módulos y permisos por cliente

El producto es **modular**: cada cliente compra solo los módulos que necesita
según su giro (restaurante, ferretería, café, etc.). Los extras como barra,
fichas o reservaciones son módulos opcionales, no parte del core.

## Dos niveles de permisos

```text
NIVEL 1 — Plataforma → Cliente (tenant)
    Nosotros (vendedores del producto) activamos en `clients` los
    módulos que el cliente compró.
              ↓
NIVEL 2 — Admin del cliente → sus usuarios
    El ADMIN del cliente crea usuarios y les asigna un SUBCONJUNTO
    de los módulos que el cliente tiene contratados.
              ↓
    Regla: user_modules ⊆ client_modules
    (un usuario nunca puede tener un módulo que el cliente no compró)
```

Ejemplo:

```text
Cliente: restaurante "Cosmo"          Cliente: ferretería "El Tornillo"
Módulos comprados:                    Módulos comprados:
  POS, KIOSK, KDS, BAR,                 POS, REPORTS, TICKETS,
  TOKENS, REPORTS, RESERVATIONS         INVENTORY (no KDS/BAR/fichas)
    │                                     │
    ├─ admin (todos)                      ├─ admin (todos los comprados)
    ├─ cajero: POS + TOKENS               ├─ cajero: POS + TICKETS
    ├─ cocinero: KDS                      └─ almacenista: INVENTORY
    └─ bartender: BAR + TOKENS
```

## Catálogo de módulos

| Módulo        | Qué habilita                                        |
| ------------- | --------------------------------------------------- |
| `CATALOG`     | Categorías, productos, precios, activar/desactivar  |
| `INVENTORY`   | Control de stock                                    |
| `POS`         | Caja: tomar órdenes, cobrar                         |
| `KIOSK`       | Autoservicio del cliente final                      |
| `KDS`         | Pantalla de cocina / surtido                        |
| `BAR`         | Pantalla de barra, bebidas pendientes               |
| `TOKENS`      | Venta y canje de fichas para bebidas                |
| `REPORTS`     | Ventas del día, top productos, ventas por empleado  |
| `TICKETS`     | Impresión de tickets                                |
| `RESERVATIONS`| Mesas, sillas de barra y reservaciones              |

## Tipo de establecimiento (preset)

`stores.business_type` (`RESTAURANT`, `RETAIL`, `BAR`, `CAFE`, ...) define el
**preset sugerido** de módulos al dar de alta un cliente — es solo un
default; la plataforma puede activar/desactivar módulos individualmente.
También puede condicionar defaults menores (ej. `orderType` disponibles:
`DINE_IN`/`TAKEAWAY` en restaurante vs. `PICKUP`/`DELIVERY` en retail).

## Modelo de datos

```text
clients ──< client_modules >── (catálogo de módulos)
users   ──< user_modules  >── (módulos asignados, ⊆ client_modules)
stores.business_type          — preset del giro
```

- `client_modules` — FK → `clients`, `module` (enum del catálogo).
- `user_modules` — FK → `users`, `module`; validado contra `client_modules`
  del cliente al que pertenece el usuario.
- `stores` — `business_type` para el preset.

## Enforcement

- **Backend:** los módulos viajan como claims en el JWT (o se resuelven por
  query al login). Los endpoints se protegen por módulo además de rol —
  ej. `@PreAuthorize("hasAuthority('MODULE_BAR')")`. Un módulo no comprado
  por el cliente responde 403 aunque el usuario lo tenga asignado.
- **Frontend:** tras el login enruta por rol + módulos efectivos
  (`user_modules ∩ client_modules`); oculta las pantallas sin permiso.
- La matriz de [02-authentication](02-authentication.md#acceso-a-módulos-por-rol)
  pasa a ser el **preset por defecto del giro restaurante**, no una regla
  fija: el admin del cliente puede quitar módulos a cualquier usuario.

## Relación con roles

- `Role` (ADMIN/MANAGER/EMPLOYEE/CUSTOMER) sigue existiendo: define la
  **jerarquía operativa** (quién puede crear usuarios, editar catálogo, etc.).
- Los **módulos** definen **qué pantallas ve** cada usuario.
- Ejemplo: un `EMPLOYEE` puede tener `POS + TOKENS` o solo `BAR`; el módulo
  decide, no el rol.

## Endpoints (gestión de módulos)

```text
# Plataforma (nivel 1)
PUT    /api/clients/{id}/modules      # módulos contratados del cliente

# Admin del cliente (nivel 2)
PUT    /api/users/{id}/modules        # asignar módulos a un usuario
GET    /api/modules/available         # módulos que el cliente compró
```
