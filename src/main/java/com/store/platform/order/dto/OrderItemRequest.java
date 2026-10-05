package com.store.platform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        Long sizeId,
        List<@Valid OrderItemExtraRequest> extras,
        @Size(max = 500) String notes) {
}
