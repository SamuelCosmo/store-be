package com.store.platform.token;

import com.store.platform.order.Order;
import com.store.platform.store.Store;
import com.store.platform.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Venta de fichas físicas en caja — el canje queda en la orden
 *  (paymentMethod=TOKEN + tokenAmount), aquí solo vive el lado del dinero. */
@Entity
@Table(name = "token_sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Cajero que cobró la venta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int quantity;

    /** Snapshot del `tokenPrice` del cliente al momento de la venta. */
    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private BigDecimal totalPrice;

    /** CASH o CARD — cómo pagó el cliente las fichas. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Order.PaymentMethod paymentMethod;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
