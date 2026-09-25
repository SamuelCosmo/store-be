# 6. Backend Architecture

For the first version, use a **Modular Monolith**.

```text
┌─────────────────────────────────────────────────┐
│ Frontend surfaces                               │
│                                                 │
│  Login   Admin Panel   Caja (POS)   Kiosko      │
│                          │            │         │
│                   Pantalla de cocina (KDS)      │
└──────────────────────────┬──────────────────────┘
                           ↓
                        REST API
                           ↓
                       Controller
                           ↓
                        Service
                           ↓
                       Repository
                           ↓
                       PostgreSQL
```

## Example

```text
OrderController
      ↓
OrderService
      ↓
OrderRepository
      ↓
PostgreSQL
```

## Module layout (paquetes)

```text
com.store.platform
├── common/        # exceptions, DTOs compartidos
├── security/      # JWT, SecurityConfig
├── auth/          # register/login
├── user/          # User entity, roles
├── client/        # Client (tenant) module — pendiente
├── store/         # Store/Establishment module
├── category/      # Category module
├── product/       # Product module
├── inventory/     # Inventory module
└── order/         # Order + OrderItem module

# Extras del cliente (pendientes — misma estructura cuando se construyan):
# client/, token/, bar/, report/, table/, ticket/
```

Cada módulo sigue la misma estructura interna:

```text
<module>/
├── <Entity>.java
├── <Module>Repository.java
├── <Module>Service.java
├── <Module>Controller.java
└── dto/
    ├── <X>Request.java
    └── <X>Response.java
```

## Request flow

Example: creating an order.

```text
Frontend
   │
   │ POST /api/orders
   ▼
OrderController
   │
   │ OrderRequest DTO
   ▼
OrderService
   │
   ├── Validate products
   ├── Check inventory
   ├── Calculate total
   ├── Create order
   └── Update inventory
   │
   ▼
OrderRepository
   │
   ▼
PostgreSQL
   │
   ▼
OrderResponse DTO
   │
   ▼
Frontend
```
