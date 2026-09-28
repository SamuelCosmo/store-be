package com.store.platform.category.dto;

import com.store.platform.category.Category;
import com.store.platform.store.Store;

import java.util.List;

public record CategoryResponse(
        Long id,
        List<Long> storeIds,
        String name,
        String description,
        String icon,
        boolean active) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getStores().stream().map(Store::getId).sorted().toList(),
                category.getName(),
                category.getDescription(),
                category.getIcon(),
                category.isActive());
    }
}
