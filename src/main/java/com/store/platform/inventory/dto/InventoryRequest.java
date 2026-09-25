package com.store.platform.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InventoryRequest(@NotNull @Min(0) Integer quantity) {
}
