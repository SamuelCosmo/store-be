package com.store.platform.order.dto;

import com.store.platform.order.Order;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @NotNull Order.Status status,
        /** Solo al cancelar: ¿ya se devolvió el dinero? null/false = sin reembolso. */
        Boolean refunded) {
}
