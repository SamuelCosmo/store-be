package com.store.platform.size.dto;

import com.store.platform.size.Size;

import java.math.BigDecimal;
import java.util.List;

public record SizeResponse(
        Long id,
        String name,
        BigDecimal price,
        boolean active,
        List<Long> productIds) {

    public static SizeResponse from(Size size) {
        return from(size, List.of());
    }

    public static SizeResponse from(Size size, List<Long> productIds) {
        return new SizeResponse(
                size.getId(),
                size.getName(),
                size.getPrice(),
                size.isActive(),
                productIds);
    }
}
