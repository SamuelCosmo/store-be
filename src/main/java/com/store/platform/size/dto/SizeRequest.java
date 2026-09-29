package com.store.platform.size.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SizeRequest(
        @NotBlank String name,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        Boolean active,
        List<Long> productIds) {
}
