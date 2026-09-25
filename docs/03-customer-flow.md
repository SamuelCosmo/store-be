# 4. Customer Flow

Existen dos canales para crear una orden: **Caja (POS)** operada por staff y
**Kiosko** de autoservicio operado por el cliente final.

## Kiosko (self-service)

El kiosko está **ligado físicamente a un establecimiento**: el staff lo activa
con un login, y la sesión (JWT) queda enlazada a ese `storeId`. El cliente
final no se loguea — usa la sesión del kiosko.

```text
Staff activa el kiosko (login → sesión ligada al establecimiento)
 ↓
Customer
 ↓
Browse Categories (del establecimiento de la sesión)
 ↓
Browse Products (solo los activos)
 ↓
Select Product
 ↓
Add Product to Cart
 ↓
Review Cart
 ↓
Choose order type: Comer aquí / Para llevar
 ↓
Pay: solo tarjeta
 ↓
Create Order
```

## Caja (POS)

```text
Staff (EMPLOYEE)
 ↓
Login
 ↓
Browse Categories
 ↓
Browse Products (solo los activos)
 ↓
Add Products to the order
 ↓
Choose order type: Comer aquí / Para llevar
 ↓
Pay: efectivo o tarjeta
 ↓
Create Order
```

## Reglas por canal

| Regla                | Caja (POS)          | Kiosko            |
| -------------------- | ------------------- | ----------------- |
| Operador             | Staff (EMPLOYEE)    | Customer          |
| Comer aquí / llevar  | Sí                  | Sí                |
| Efectivo             | Sí                  | No                |
| Tarjeta              | Sí                  | Sí (obligatorio)  |

> **Nota:** el carrito vive en el frontend (caja/kiosko); el backend solo recibe
> el `POST /api/orders` con productos, cantidades, tipo de orden, método de pago
> y canal. El `storeId` se deriva de la sesión autenticada (kiosko ligado al
> establecimiento, staff ligado a su tienda) — no se confía en el body.
