package com.store.platform.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemExtraRequest(
        @NotNull Long extraId,
        @NotNull @Min(1) Integer quantity) {
}
