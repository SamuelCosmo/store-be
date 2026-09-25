# 3. Store Setup

El **cliente** (tenant: restaurante, café, etc.) puede tener **varios
establecimientos**. Cada establecimiento configura su propio catálogo.

```text
Client (tenant)
 ↓
Create Establishment(s)
 ↓
Create Categories
 ↓
Create Products
 ↓
Set Product Prices
 ↓
Configure Inventory
 ↓
Activate / Deactivate products  ← flag `active`: controla qué ven los clientes
```

## Panel Admin (paquete básico)

Desde el panel de administración el `ADMIN` del cliente puede:

- Dar de alta y baja **categorías**
- Dar de alta y baja **productos**
- Definir precios
- **Activar/desactivar** productos → solo los productos activos se muestran
  en caja y kiosko para que el cliente seleccione el alimento
- Configurar inventario

## Jerarquía

```text
Client (tenant)
 │
 ├── Establishment A
 │      ├── Categories → Products → Inventory
 │      └── Orders
 │
 └── Establishment B
        ├── Categories → Products → Inventory
        └── Orders
```

## Example: Cosmo Coffee (un establecimiento del cliente)

```text
Cosmo Coffee
│
├── Coffee
│   ├── Espresso
│   ├── Americano
│   └── Cappuccino
│
├── Cold Drinks
│   ├── Iced Coffee
│   └── Iced Tea
│
└── Bakery
    ├── Croissant
    └── Muffin
```
