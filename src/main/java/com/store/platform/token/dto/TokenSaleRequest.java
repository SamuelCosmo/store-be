package com.store.platform.token.dto;

import com.store.platform.order.Order;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TokenSaleRequest(
        @Positive int quantity,
        /** Cómo paga el cliente las fichas — CASH o CARD (TOKEN no: sería circular). */
        @NotNull Order.PaymentMethod paymentMethod) {
}
