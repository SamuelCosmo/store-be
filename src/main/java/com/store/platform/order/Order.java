package com.store.platform.order;

import com.store.platform.store.Store;
import com.store.platform.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** Staff or the store's generic kiosk account that created the order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus refundStatus;

    @Column(nullable = false)
    private BigDecimal total;

    /** Net before tax — sum of item lines; catalog prices don't include tax. */
    private BigDecimal subtotal;

    /** Client's taxRate % snapshot at order time. */
    private BigDecimal taxRate;

    /** Tax added on top of subtotal (total = subtotal + taxAmount). */
    private BigDecimal taxAmount;

    /** Folio semanal consecutivo por tienda — se asigna al crear dentro de la
     *  transacción (lock pesimista sobre el store). Nullable: órdenes viejas. */
    private Integer ticketNumber;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum Channel { POS, KIOSK }

    public enum OrderType { DINE_IN, TAKEAWAY }

    public enum PaymentMethod { CASH, CARD }

    public enum Status { PENDING, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED }

    public enum RefundStatus { NONE, REFUNDED }
}
