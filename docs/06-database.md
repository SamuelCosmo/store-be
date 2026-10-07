# 8. Database Flow

## Modelo multi-tenant

```text
Client (tenant: restaurante, café, etc.)
 │
 ├── Extra / Size          ← catálogos a nivel CLIENTE (reutilizables)
 │
 └── Store / Establishment  ← un cliente puede tener varios
        │
        ├── User            ← ligado al client; opera 1..N stores (user_stores)
        │
        ├── Category        ← N:M con stores (category_stores)
        │      │
        │      └── Product  ← flag `active` controla visibilidad en caja/kiosko
        │              │
        │              ├── N:M → Extra  (product_extras)
        │              └── N:M → Size   (product_sizes)
        │
        ├── Inventory       ← 1:1 con Product
        │
        └── Order           ← channel (POS/KIOSK), orderType, paymentMethod
               │              subtotal/taxRate/taxAmount persistidos
               │
               └── OrderItem    ← snapshot: unitPrice, sizeId+sizeName,
                       │          extras (jsonb), notes
                       └── Product
```

## Tablas

- `clients` *(nuevo — tenant/negocio que compra el producto)*
- `stores` *(establecimientos; FK → `clients`)*
- `users` *(FK → `clients` — **nullable** para `SUPERADMIN`, que no tiene
  tenant; staff/admin/kiosko. Roles: `ADMIN`/`MANAGER`/`EMPLOYEE`/
  `KITCHEN`/`CUSTOMER`/`SUPERADMIN`)*
- `user_stores` *(N:M — en qué establecimientos del cliente opera el usuario;
  el ADMIN puede tener varios)*
- `categories` *(N:M con stores via `category_stores`)*
- `products`
- `extras` *(catálogo del cliente; `price` = cargo adicional)*
- `sizes` *(catálogo del cliente; `price` = cargo adicional sobre el base)*
- `product_extras` *(join N:M products↔extras)*
- `product_sizes` *(join N:M products↔sizes)*
- `inventory`
- `orders`
- `order_items`
- `token_transactions` *(extras del cliente — movimientos de fichas físicas:
  PURCHASE/REDEMPTION/ADJUSTMENT; ver [12](12-client-extras.md))*
- `tables` *(mesas y sillas de barra; `zone`: `DINING`/`BAR`)*
- `reservations` *(reservación de mesas y sillas de barra)*
- `tickets` *(folio impreso por orden/transacción — opcional)*
- `client_modules` *(módulos contratados por el cliente; ver [13](13-modules-and-permissions.md))*
- `user_modules` *(módulos asignados a cada usuario, ⊆ `client_modules`)*

> **Nota de implementación:** `customers` se modela como `users` con rol
> `CUSTOMER`. Si más adelante se separan, se crea la tabla `customers` propia.

> **CHECKs de enums:** Hibernate (`ddl-auto: update`) genera constraints
> `CHECK` con los valores del enum (ej. `users_role_check`). Al agregar un
> valor nuevo al enum hay que actualizar el constraint a mano — `update`
> no altera constraints existentes:
> `ALTER TABLE users DROP CONSTRAINT users_role_check;` + `ADD ... CHECK
> (role = ANY (...))` incluyendo el valor nuevo.
>
> **Lo mismo para `NOT NULL`:** `update` tampoco quita nullability — al
> hacer `users.client_id` nullable (rol `SUPERADMIN`) hay que correr en
> DBs existentes (incluida producción/Railway):
> `ALTER TABLE users ALTER COLUMN client_id DROP NOT NULL;`
> En una DB nueva ambos constraints ya nacen correctos.

## Relaciones clave

| Tabla         | Relación / columnas extra                                     |
| ------------- | ------------------------------------------------------------- |
| `clients`     | 1:N → `stores` (el cliente tiene varios establecimientos)     |
| `stores`      | FK → `clients`, `business_type` (RESTAURANT/RETAIL/BAR/CAFE)  |
|               | — preset de módulos sugerido por giro                         |
| `users`       | FK → `clients` (tenant); `user_stores` acota a qué            |
|               | establecimientos opera (ADMIN: varios; kiosko: su store)      |
| `user_stores` | FK → `users`, FK → `stores` — stores del mismo `client_id`    |
| `client_modules` | FK → `clients`, `module` — módulos comprados por el cliente |
| `user_modules`   | FK → `users`, `module` — ⊆ `client_modules` del cliente    |
| `categories`  | N:M → `stores` via `category_stores` (una categoría puede      |
|               | existir en varios establecimientos), `icon`, `active`        |
| `extras`      | FK → `clients` (catálogo del tenant), `name`, `price`, `active` |
| `sizes`       | FK → `clients` (catálogo del tenant), `name`, `price`, `active` |
| `products`    | FK → `categories`, `active` (visible en caja/kiosko),         |
|               | `sold_out` (86'd — oculto en kiosco, rechazado al ordenar),   |
|               | `sku`, `image` (URL pública — upload vía `POST /api/uploads`  |
|               | al bucket S3, keys `clients/{id}/stores/{id}/products/`),     |
|               | `token_cost` — si > 0, canjeable con fichas (bebidas);        |
|               | N:M → `extras`/`sizes` via `product_extras`/`product_sizes` |
| `inventory`   | FK único → `products` (1:1)                                   |
| `orders`      | FK → `stores`, FK → `users` (staff, o cuenta genérica del     |
|               | kiosko),                                                      |
|               | `status` (PENDING/PREPARING/READY/COMPLETED/CANCELLED),       |
|               | `channel` (POS/KIOSK), `order_type` (DINE_IN/TAKEAWAY),       |
|               | `payment_method` (CASH/CARD), `refund_status`                 |
|               | (NONE/REFUNDED — se marca al cancelar una orden pagada),      |
|               | `subtotal`, `tax_rate`, `tax_amount`, `total` — snapshot del  |
|               | cobro al momento de crearla (no recalcula con settings)       |
| `order_items` | FK → `orders`, FK → `products` — snapshot por línea:          |
|               | `unit_price` (base + size al cobrar), `quantity`, `size_id` + |
|               | `size_name`, `extras` (jsonb: nombre+precio por línea),       |
|               | `notes` — sobrevive cambios de catálogo/precios               |
| `token_transactions`| FK → `stores`, `type` (PURCHASE/REDEMPTION/ADJUSTMENT),       |
|                     | `quantity`, FK → `users` (staff que vendió/canjeó — las       |
|                     | fichas son físicas, sin wallet), FK opcional → `products`     |
| `tables`      | FK → `stores`, `zone` (DINING/BAR), `seats`, `number` —       |
|               | la barra son 6 registros con `zone=BAR`, `seats=1`            |
| `reservations`| FK → `tables`, `date`, `time_slot`, `party_size`,             |
|               | `customer_name`, `phone`, `status` (PENDING/CONFIRMED/        |
|               | SEATED/CANCELLED/NO_SHOW) — sin solapamientos por mesa        |
| `tickets`     | **pendiente de implementar** — diseño: FK → `orders` (o       |
|               | `token_transactions`), `ticket_number` consecutivo por        |
|               | `stores`, `printed_at`. Hoy el ticket se arma al vuelo desde  |
|               | `orders` (`GET /api/orders/{id}/ticket`) y el folio mostrado  |
|               | es `#id` — falta persistir el consecutivo semanal por store   |

## Integridad multi-tenant

- Toda consulta de catálogo/órdenes está acotada por `store_id`, que a su vez
  pertenece a un `client_id`.
- Un usuario solo opera sobre establecimientos de su cliente (`client_id` en
  `users`) y, salvo ADMIN, solo sobre los listados en `user_stores`.
