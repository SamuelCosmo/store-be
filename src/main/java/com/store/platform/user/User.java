package com.store.platform.user;

import com.store.platform.client.Client;
import com.store.platform.store.Store;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** null solo para SUPERADMIN (cuenta de plataforma, sin tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Stores of the same client this user may operate (ADMIN: all of them). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_stores",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "store_id"))
    @Builder.Default
    private Set<Store> stores = new HashSet<>();

    /** Existing rows get true via column default (ddl-auto update). */
    @Column(nullable = false, columnDefinition = "boolean not null default true")
    @Builder.Default
    private boolean active = true;

    /** Soft delete: row stays for order history; hidden from all listings. */
    private Instant deletedAt;

    /** Viaja en el JWT (claim `ver`). Subirla invalida todos los tokens
     *  emitidos antes — desactivar, borrar, cambiar rol/contraseña/tiendas. */
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    @Builder.Default
    private int tokenVersion = 0;

    public void revokeTokens() {
        tokenVersion++;
    }

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum Role {
        ADMIN, MANAGER, EMPLOYEE, CUSTOMER, KITCHEN, SUPERADMIN
    }
}
