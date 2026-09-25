# 8. Database Flow

## Modelo multi-tenant

```text
Client (tenant: restaurante, café, etc.)
 │
 └── Store / Establishment  ← un cliente puede tener varios
        │
        ├── User            ← staff/admin ligados al establecimiento
        │
        ├── Category
        │      │
        │      └── Product  ← flag `active` controla visibilidad en caja/kiosko
        │
        ├── Inventory       ← 1:1 con Product
        │
        └── Order           ← channel (POS/KIOSK), orderType, paymentMethod
               │
               └── OrderItem
                       │
                       └── Product
```

## Tablas

- `clients` *(nuevo — tenant/negocio que compra el producto)*
- `stores` *(establecimientos; FK → `clients`)*
- `users`
- `categories`
- `products`
- `inventory`
- `orders`
- `order_items`

> **Nota de implementación:** `customers` se modela como `users` con rol
> `CUSTOMER`. Si más adelante se separan, se crea la tabla `customers` propia.

## Relaciones clave

| Tabla         | Relación / columnas extra                                     |
| ------------- | ------------------------------------------------------------- |
| `clients`     | 1:N → `stores` (el cliente tiene varios establecimientos)     |
| `stores`      | FK → `clients`                                                |
| `users`       | FK opcional → `stores` (empleados/managers/admin)             |
| `categories`  | FK → `stores`                                                 |
| `products`    | FK → `categories`, columna `active` (visible en caja/kiosko)  |
| `inventory`   | FK único → `products` (1:1)                                   |
| `orders`      | FK → `stores`, FK → `users` (customer/staff que la creó),     |
|               | `channel` (POS/KIOSK), `order_type` (DINE_IN/TAKEAWAY),       |
|               | `payment_method` (CASH/CARD)                                  |
| `order_items` | FK → `orders`, FK → `products`                                |

## Integridad multi-tenant

- Toda consulta de catálogo/órdenes está acotada por `store_id`, que a su vez
  pertenece a un `client_id`.
- Un usuario staff solo opera sobre los establecimientos de su cliente.
