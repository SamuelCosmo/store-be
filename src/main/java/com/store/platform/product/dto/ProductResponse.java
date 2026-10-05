package com.store.platform.product.dto;

import com.store.platform.extra.Extra;
import com.store.platform.extra.dto.ExtraResponse;
import com.store.platform.product.Product;
import com.store.platform.size.Size;
import com.store.platform.size.dto.SizeResponse;

import java.math.BigDecimal;
import java.util.Comparator;
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
        boolean soldOut,
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
                product.isSoldOut(),
                product.getTokenCost(),
                // extras alfabéticos; tamaños en el orden global del catálogo
                product.getExtras().stream()
                        .sorted(Comparator.comparing(Extra::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(ExtraResponse::from).toList(),
                product.getSizes().stream()
                        .sorted(Comparator
                                .comparing((Size s) -> s.getPosition() == null ? Integer.MAX_VALUE : s.getPosition())
                                .thenComparing(Size::getName))
                        .map(SizeResponse::from).toList());
    }
}
