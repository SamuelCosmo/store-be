package com.store.platform.audit;

import com.store.platform.client.Client;
import com.store.platform.store.Store;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** Bitácora de cambios de negocio — quién hizo qué y cuándo. A diferencia de
 *  app_logs (soporte/debug), esto es el audit trail: sobrevive retenciones
 *  cortas y alimenta la página /audit del admin. Los datos del actor se
 *  guardan como snapshot (nombre/email/rol) para que el historial no cambie
 *  si el usuario se renombra o se borra. */
@Entity
@Table(name = "audit_events", indexes = {
        @Index(columnList = "client_id, created_at"),
        @Index(columnList = "entity_type"),
        @Index(columnList = "actor_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tenant afectado — null solo en eventos de plataforma sin tenant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    // Snapshot del actor — no FK: el historial no debe depender del usuario.
    private Long actorId;

    private String actorName;

    private String actorEmail;

    private String actorRole;

    /** "product", "category", "store", "user", "client", "order", "device"… */
    @Column(nullable = false, length = 40)
    private String entityType;

    /** "create", "update", "delete", "restore", "cancel", "settings"… */
    @Column(nullable = false, length = 40)
    private String action;

    private Long entityId;

    /** Nombre legible de la entidad al momento del evento (producto, usuario…). */
    private String entityLabel;

    /** Detalle del cambio: {campo: "antes → después"} o datos relevantes.
     *  NUNCA passwords ni secretos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
