package com.store.platform.store.dto;

import com.store.platform.store.Store;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record StoreRequest(
        @NotBlank String name,
        String description,
        String address,
        @NotNull Store.BusinessType businessType,
        Boolean active,
        /** URL del logo (subida via /api/uploads/brand); "" lo quita, null lo conserva. */
        String logoUrl,
        /** Paleta de la terminal — #rrggbb; "" quita el color, null lo conserva. */
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$") String primaryColor,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$") String accentColor,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$") String canvasColor) {
}
