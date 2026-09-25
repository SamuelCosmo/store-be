package com.store.platform.store.dto;

import com.store.platform.store.Store;

import java.time.Instant;

public record StoreResponse(
        Long id,
        Long clientId,
        String name,
        String description,
        Store.BusinessType businessType,
        Instant createdAt) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getClient().getId(),
                store.getName(),
                store.getDescription(),
                store.getBusinessType(),
                store.getCreatedAt());
    }
}
