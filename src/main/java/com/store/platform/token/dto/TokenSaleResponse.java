package com.store.platform.token.dto;

import com.store.platform.order.Order;
import com.store.platform.token.TokenSale;

import java.math.BigDecimal;
import java.time.Instant;

public record TokenSaleResponse(
        Long id,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Order.PaymentMethod paymentMethod,
        String userName,
        Instant createdAt) {

    public static TokenSaleResponse from(TokenSale sale) {
        return new TokenSaleResponse(
                sale.getId(),
                sale.getQuantity(),
                sale.getUnitPrice(),
                sale.getTotalPrice(),
                sale.getPaymentMethod(),
                sale.getUser().getName(),
                sale.getCreatedAt());
    }
}
