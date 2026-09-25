package com.store.platform.product.dto;

import com.store.platform.product.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        Long categoryId,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        int tokenCost) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.isActive(),
                product.getTokenCost());
    }
}
