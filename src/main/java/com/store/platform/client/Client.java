package com.store.platform.client;

import com.store.platform.module.Module;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Logo de la empresa del tenant (URL pública del bucket). */
    private String logoUrl;

    /** Plan limits — set by the platform when onboarding the client. */
    @Column(nullable = false)
    private Integer maxStores;

    @Column(nullable = false)
    private Integer maxPosPerStore;

    @Column(nullable = false)
    private Integer maxKiosksPerStore;

    /** Módulos contratados por el tenant (la plataforma los activa).
     *  user_modules ⊆ client_modules — ver docs/13-modules-and-permissions. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "client_modules",
            joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "module", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Module> modules = EnumSet.noneOf(Module.class);

    // Client-level settings (null = use defaults in ClientSettingsResponse)
    @Column(length = 3)
    private String currency;

    private BigDecimal taxRate;

    /** Precio de venta de una ficha (canjeable por productos con tokenCost>0).
     *  null/0 → el tenant no vende fichas. */
    private BigDecimal tokenPrice;

    private Integer kioskReceiptSeconds;

    /** Session duration per role, in hours (0 = never close). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> sessionHours;

    /** Zona IANA del negocio (ej. America/Mexico_City) — define el "hoy" de
     *  reportes/fichas y el lunes del folio semanal. El server corre en UTC. */
    @Column(length = 64)
    private String timezone;

    public static final String DEFAULT_TIMEZONE = "America/Mexico_City";

    public ZoneId zone() {
        return ZoneId.of(timezone != null ? timezone : DEFAULT_TIMEZONE);
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
