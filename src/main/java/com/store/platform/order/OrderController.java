package com.store.platform.order;

import com.store.platform.order.dto.OrderRequest;
import com.store.platform.order.dto.OrderResponse;
import com.store.platform.order.dto.StatusRequest;
import com.store.platform.order.dto.TicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Caja (staff) o kiosco (CUSTOMER) — cocina no vende. `Idempotency-Key`
     *  (un id por cobro): reintentar con el mismo key devuelve la orden ya
     *  creada en vez de duplicarla (ej. se cortó la red tras cobrar). */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','CUSTOMER')")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, idempotencyKey));
    }

    /** Sin page/size → array completo (KDS); con ellos → Page (historial/admin).
     *  Se decide por presencia explícita: el fallback de Boot pagina hasta sin params.
     *  Solo staff: el kiosco (CUSTOMER) arma su ticket con la respuesta del POST. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','KITCHEN')")
    public Object list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) Order.Status status,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Pageable pageable) {
        return page != null || size != null
                ? orderService.listPage(storeId, status, pageable)
                : orderService.list(storeId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','KITCHEN')")
    public OrderResponse getById(@PathVariable Long id) {
        return orderService.getById(id);
    }

    /** Ticket autocontenido para reimprimir — staff; scoping por store de sesión. */
    @GetMapping("/{id}/ticket")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','KITCHEN')")
    public TicketResponse getTicket(@PathVariable Long id) {
        return orderService.getTicket(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','KITCHEN')")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}
