package com.store.platform.product.dto;

import com.store.platform.extra.dto.ExtraResponse;
import com.store.platform.product.Product;
import com.store.platform.size.dto.SizeResponse;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        Long categoryId,
        String name,
        String description,
        String sku,
        String image,
        BigDecimal price,
        boolean active,
        int tokenCost,
        List<ExtraResponse> extras,
        List<SizeResponse> sizes) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getImage(),
                product.getPrice(),
                product.isActive(),
                product.getTokenCost(),
                product.getExtras().stream().map(ExtraResponse::from).toList(),
                product.getSizes().stream().map(SizeResponse::from).toList());
    }
}
