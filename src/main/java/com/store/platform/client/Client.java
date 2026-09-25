package com.store.platform.client;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

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

    /** Plan limits — set by the platform when onboarding the client. */
    @Column(nullable = false)
    private Integer maxStores;

    @Column(nullable = false)
    private Integer maxPosPerStore;

    @Column(nullable = false)
    private Integer maxKiosksPerStore;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
