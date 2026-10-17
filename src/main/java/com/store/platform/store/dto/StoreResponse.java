package com.store.platform.store.dto;

import com.store.platform.store.Store;

import java.time.Instant;

public record StoreResponse(
        Long id,
        Long clientId,
        String name,
        String description,
        String address,
        Store.BusinessType businessType,
        boolean active,
        /** Logo y paleta propios del establecimiento — null = hereda del tenant/tema base. */
        String logoUrl,
        String primaryColor,
        String accentColor,
        String canvasColor,
        Instant createdAt) {

    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getClient().getId(),
                store.getName(),
                store.getDescription(),
                store.getAddress(),
                store.getBusinessType(),
                store.isActive(),
                store.getLogoUrl(),
                store.getPrimaryColor(),
                store.getAccentColor(),
                store.getCanvasColor(),
                store.getCreatedAt());
    }
}
