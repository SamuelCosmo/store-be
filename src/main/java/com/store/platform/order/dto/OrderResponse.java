package com.store.platform.order.dto;

import com.store.platform.order.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long storeId,
        Long userId,
        Order.Channel channel,
        Order.OrderType orderType,
        Order.PaymentMethod paymentMethod,
        Order.Status status,
        Order.RefundStatus refundStatus,
        BigDecimal total,
        Instant createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStore().getId(),
                order.getUser().getId(),
                order.getChannel(),
                order.getOrderType(),
                order.getPaymentMethod(),
                order.getStatus(),
                order.getRefundStatus(),
                order.getTotal(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
