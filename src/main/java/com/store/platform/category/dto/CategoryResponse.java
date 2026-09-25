package com.store.platform.category.dto;

import com.store.platform.category.Category;

public record CategoryResponse(
        Long id,
        Long storeId,
        String name,
        String description) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getStore().getId(),
                category.getName(),
                category.getDescription());
    }
}
