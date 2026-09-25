package com.store.platform.client.dto;

import com.store.platform.client.Client;

import java.time.Instant;

public record ClientResponse(
        Long id,
        String name,
        Integer maxStores,
        Integer maxPosPerStore,
        Integer maxKiosksPerStore,
        Instant createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getMaxStores(),
                client.getMaxPosPerStore(),
                client.getMaxKiosksPerStore(),
                client.getCreatedAt());
    }
}
