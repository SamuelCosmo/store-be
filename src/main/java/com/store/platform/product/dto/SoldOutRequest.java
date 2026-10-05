package com.store.platform.product.dto;

import jakarta.validation.constraints.NotNull;

public record SoldOutRequest(@NotNull Boolean soldOut) {
}
