package com.store.platform.client.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClientRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer maxStores,
        @NotNull @Min(0) Integer maxPosPerStore,
        @NotNull @Min(0) Integer maxKiosksPerStore) {
}
