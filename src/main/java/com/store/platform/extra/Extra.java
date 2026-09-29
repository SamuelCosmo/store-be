package com.store.platform.extra;

import com.store.platform.client.Client;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "extras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Extra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String name;

    /** Added on top of the product price when picked. */
    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
