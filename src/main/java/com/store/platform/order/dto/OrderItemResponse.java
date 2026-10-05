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
        BigDecimal subtotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSizeName(),
                item.getExtras(),
                item.getNotes(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
