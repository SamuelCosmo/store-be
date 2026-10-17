package com.store.platform.order.dto;

import com.store.platform.order.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        /** Folio semanal por tienda; fallback a id para órdenes anteriores. */
        Long ticketNumber,
        Long storeId,
        Long userId,
        Order.Channel channel,
        Order.OrderType orderType,
        Order.PaymentMethod paymentMethod,
        Order.Status status,
        Order.RefundStatus refundStatus,
        BigDecimal total,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        /** Solo pagos en efectivo — null en CARD. */
        BigDecimal cashReceived,
        BigDecimal changeGiven,
        /** Fichas cobradas — solo pagos TOKEN, null en los demás. */
        Integer tokenAmount,
        /** Establecimiento de la orden (menuName gana sobre name) — encabezado del ticket. */
        String storeName,
        /** Nombre del negocio del tenant. */
        String clientName,
        /** Logo del establecimiento; si no tiene, el del tenant. */
        String logoUrl,
        Instant createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        // fallback para órdenes anteriores a la persistencia del desglose
        BigDecimal taxRate = order.getTaxRate() == null ? BigDecimal.ZERO : order.getTaxRate();
        return new OrderResponse(
                order.getId(),
                order.getTicketNumber() != null
                        ? order.getTicketNumber().longValue()
                        : order.getId(),
                order.getStore().getId(),
                order.getUser().getId(),
                order.getChannel(),
                order.getOrderType(),
                order.getPaymentMethod(),
                order.getStatus(),
                order.getRefundStatus(),
                order.getTotal(),
                order.getSubtotal() == null ? order.getTotal() : order.getSubtotal(),
                taxRate,
                order.getTaxAmount() == null ? BigDecimal.ZERO : order.getTaxAmount(),
                order.getCashReceived(),
                order.getChangeGiven(),
                order.getTokenAmount(),
                order.getStore().getMenuName() != null && !order.getStore().getMenuName().isBlank()
                        ? order.getStore().getMenuName()
                        : order.getStore().getName(),
                order.getStore().getClient() == null
                        ? null
                        : order.getStore().getClient().getName(),
                order.getStore().getLogoUrl() != null
                        ? order.getStore().getLogoUrl()
                        : order.getStore().getClient() == null
                                ? null
                                : order.getStore().getClient().getLogoUrl(),
                order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}
