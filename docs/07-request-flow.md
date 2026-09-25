# 7. Request Flow

Example: Creating an order.

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
