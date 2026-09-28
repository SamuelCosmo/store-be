package com.store.platform.store.dto;

import com.store.platform.store.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StoreRequest(
        @NotBlank String name,
        String description,
        String address,
        @NotNull Store.BusinessType businessType,
        Boolean active) {
}
