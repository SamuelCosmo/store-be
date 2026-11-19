package com.store.platform.order;

import com.store.platform.order.dto.OrderRequest;
import com.store.platform.order.dto.OrderResponse;
import com.store.platform.order.dto.StatusRequest;
import com.store.platform.order.dto.TicketResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** Caja (staff → MODULE_POS) o kiosco (CUSTOMER → MODULE_KIOSK) — cocina
     *  no vende. `Idempotency-Key` (un id por cobro): reintentar con el mismo
     *  key devuelve la orden ya creada en vez de duplicarla. */
    @PostMapping
    @PreAuthorize("(hasAnyRole('ADMIN','MANAGER','EMPLOYEE') and hasAuthority('MODULE_POS'))"
            + " or (hasRole('CUSTOMER') and hasAuthority('MODULE_KIOSK'))")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(request, idempotencyKey));
    }

    /** Sin page/size → array completo (KDS → MODULE_KDS); con ellos → Page
     *  (historial/admin → cualquier módulo de venta). `status` admite varios
     *  (?status=PENDING,READY) — un solo request del KDS. */
    @GetMapping
    @PreAuthorize("(hasRole('KITCHEN') and hasAuthority('MODULE_KDS'))"
            + " or (hasAnyRole('ADMIN','MANAGER','EMPLOYEE')"
            + " and hasAnyAuthority('MODULE_POS','MODULE_KDS','MODULE_REPORTS'))")
    public Object list(@RequestParam(required = false) Long storeId,
                       @RequestParam(required = false) List<Order.Status> status,
                       @RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size,
                       Pageable pageable) {
        return page != null || size != null
                ? orderService.listPage(storeId, status, pageable)
                : orderService.list(storeId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("(hasRole('KITCHEN') and hasAuthority('MODULE_KDS'))"
            + " or (hasAnyRole('ADMIN','MANAGER','EMPLOYEE')"
            + " and hasAnyAuthority('MODULE_POS','MODULE_KDS','MODULE_REPORTS'))")
    public OrderResponse getById(@PathVariable Long id) {
        return orderService.getById(id);
    }

    /** Ticket autocontenido para reimprimir — staff con módulo de venta;
     *  scoping por store de sesión. */
    @GetMapping("/{id}/ticket")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE','KITCHEN')"
            + " and hasAnyAuthority('MODULE_POS','MODULE_KDS','MODULE_TICKETS','MODULE_REPORTS')")
    public TicketResponse getTicket(@PathVariable Long id) {
        return orderService.getTicket(id);
    }

    /** KITCHEN avanza estados del KDS; staff con POS/KDS también (entrega,
     *  cancelación, mark paid). */
    @PatchMapping("/{id}/status")
    @PreAuthorize("(hasRole('KITCHEN') and hasAuthority('MODULE_KDS'))"
            + " or (hasAnyRole('ADMIN','MANAGER','EMPLOYEE')"
            + " and hasAnyAuthority('MODULE_POS','MODULE_KDS'))")
    public OrderResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.refunded());
    }
}
