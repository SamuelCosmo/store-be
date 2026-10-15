package com.store.platform.order.dto;

import com.store.platform.order.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(
        @NotNull Order.Channel channel,
        @NotNull Order.OrderType orderType,
        @NotNull Order.PaymentMethod paymentMethod,
        /** Solo pagos en efectivo — el server calcula `changeGiven`. */
        @PositiveOrZero BigDecimal cashReceived,
        /** Override de tienda para el selector multi-tienda (admin/gerente);
         *  null = tienda de sesión como siempre. */
        Long storeId,
        @NotEmpty List<@Valid OrderItemRequest> items) {
}
