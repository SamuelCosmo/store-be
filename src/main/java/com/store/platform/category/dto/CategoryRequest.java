package com.store.platform.category.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record CategoryRequest(
        @NotBlank String name,
        String description,
        String icon,
        Boolean active,
        Set<Long> storeIds) {
}
