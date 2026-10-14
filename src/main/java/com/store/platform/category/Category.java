package com.store.platform.category;

import com.store.platform.store.Store;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "category_stores",
            joinColumns = @JoinColumn(name = "category_id"),
            inverseJoinColumns = @JoinColumn(name = "store_id"))
    @Builder.Default
    private Set<Store> stores = new HashSet<>();

    @Column(nullable = false)
    private String name;

    private String description;

    private String icon;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Soft delete: row stays for order history; hidden from all listings. */
    private Instant deletedAt;
}
