package com.store.platform.order.dto;

import com.store.platform.order.Order;
import com.store.platform.store.Store;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Payload autocontenido para imprimir el ticket — igual que OrderResponse
 *  más el nombre/dirección del establecimiento (menuName gana sobre name). */
public record TicketResponse(
        Long id,
        Long storeId,
        String storeName,
        String storeAddress,
        Long userId,
        Order.Channel channel,
        Order.OrderType orderType,
        Order.PaymentMethod paymentMethod,
        Order.Status status,
        Order.RefundStatus refundStatus,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal total,
        Instant createdAt,
        List<OrderItemResponse> items) {

    public static TicketResponse from(Order order) {
        Store store = order.getStore();
        OrderResponse o = OrderResponse.from(order);
        String name = store.getMenuName() != null && !store.getMenuName().isBlank()
                ? store.getMenuName()
                : store.getName();
        return new TicketResponse(
                o.id(), store.getId(), name, store.getAddress(),
                o.userId(), o.channel(), o.orderType(), o.paymentMethod(),
                o.status(), o.refundStatus(), o.subtotal(), o.taxRate(),
                o.taxAmount(), o.total(), o.createdAt(), o.items());
    }
}
