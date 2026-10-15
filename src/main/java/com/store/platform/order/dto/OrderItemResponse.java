package com.store.platform.order.dto;

import com.store.platform.order.ItemExtraSnapshot;
import com.store.platform.order.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderItemResponse(
        Long productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        String sizeName,
        List<ItemExtraSnapshot> extras,
        String notes,
        /** Fichas por unidad (snapshot) — 0 en items viejos o no canjeables. */
        int tokenCost,
        BigDecimal subtotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSizeName(),
                // null en items creados antes de la columna extras jsonb
                item.getExtras() == null ? List.of() : item.getExtras(),
                item.getNotes(),
                item.getTokenCost() == null ? 0 : item.getTokenCost(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
