package com.store.platform.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// TODO: add sizeId/extraIds — items don't capture size or extras yet
public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity) {
}
