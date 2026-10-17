package com.store.platform.store;

import com.store.platform.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String name;

    private String description;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusinessType businessType;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Logo propio del establecimiento (terminales + ticket); null = el del tenant. */
    private String logoUrl;

    /** Paleta de las terminales — #rrggbb, null = tema base de la plataforma. */
    private String primaryColor;

    private String accentColor;

    private String canvasColor;

    // Per-establishment settings (null = use defaults in StoreSettingsResponse)
    private String menuName;

    private Integer kitchenOkMin;

    private Integer kitchenWarnMin;

    private Boolean kitchenSound;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft delete: row stays for order history; hidden from all listings. */
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum BusinessType {
        RESTAURANT, RETAIL, BAR, CAFE
    }
}
