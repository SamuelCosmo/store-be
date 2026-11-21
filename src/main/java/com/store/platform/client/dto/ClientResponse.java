package com.store.platform.client.dto;

import com.store.platform.client.Client;
import com.store.platform.module.ModuleService;

import java.time.Instant;
import java.util.List;

public record ClientResponse(
        Long id,
        String name,
        Integer maxStores,
        Integer maxPosPerStore,
        Integer maxKiosksPerStore,
        boolean active,
        Instant suspendedAt,
        /** Módulos contratados del tenant — la plataforma los edita. */
        List<String> modules,
        Instant createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getMaxStores(),
                client.getMaxPosPerStore(),
                client.getMaxKiosksPerStore(),
                client.isActive(),
                client.getSuspendedAt(),
                ModuleService.names(ModuleService.purchasedModules(client)),
                client.getCreatedAt());
    }
}
