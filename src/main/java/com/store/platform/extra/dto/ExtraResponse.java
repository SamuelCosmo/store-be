package com.store.platform.extra.dto;

import com.store.platform.extra.Extra;

import java.math.BigDecimal;
import java.util.List;

public record ExtraResponse(
        Long id,
        String name,
        BigDecimal price,
        boolean active,
        List<Long> productIds) {

    public static ExtraResponse from(Extra extra) {
        return from(extra, List.of());
    }

    public static ExtraResponse from(Extra extra, List<Long> productIds) {
        return new ExtraResponse(
                extra.getId(),
                extra.getName(),
                extra.getPrice(),
                extra.isActive(),
                productIds);
    }
}
