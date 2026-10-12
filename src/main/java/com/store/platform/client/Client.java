package com.store.platform.client;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

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

    // Client-level settings (null = use defaults in ClientSettingsResponse)
    @Column(length = 3)
    private String currency;

    private BigDecimal taxRate;

    private Integer kioskReceiptSeconds;

    /** Session duration per role, in hours (0 = never close). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Integer> sessionHours;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
