package com.store.platform.order.dto;

import com.store.platform.order.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// TODO: cashReceived for CASH payments (compute changeGiven on the response)
public record OrderRequest(
        @NotNull Order.Channel channel,
        @NotNull Order.OrderType orderType,
        @NotNull Order.PaymentMethod paymentMethod,
        @NotEmpty List<@Valid OrderItemRequest> items) {
}
