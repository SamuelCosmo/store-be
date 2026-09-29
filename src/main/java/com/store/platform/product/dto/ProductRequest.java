package com.store.platform.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotNull Long categoryId,
        @NotBlank String name,
        String description,
        String sku,
        String image,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(0) Integer tokenCost,
        List<Long> extraIds,
        List<Long> sizeIds) {
}
