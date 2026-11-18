package com.store.platform.audit.dto;

import com.store.platform.audit.AuditEvent;

import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(
        Long id,
        Instant createdAt,
        Long actorId,
        String actorName,
        String actorEmail,
        String actorRole,
        String entityType,
        String action,
        Long entityId,
        String entityLabel,
        Long storeId,
        String storeName,
        Long clientId,
        String clientName,
        Map<String, Object> details) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(),
                e.getCreatedAt(),
                e.getActorId(),
                e.getActorName(),
                e.getActorEmail(),
                e.getActorRole(),
                e.getEntityType(),
                e.getAction(),
                e.getEntityId(),
                e.getEntityLabel(),
                e.getStore() != null ? e.getStore().getId() : null,
                e.getStore() != null ? e.getStore().getName() : null,
                e.getClient() != null ? e.getClient().getId() : null,
                e.getClient() != null ? e.getClient().getName() : null,
                e.getDetails());
    }
}
