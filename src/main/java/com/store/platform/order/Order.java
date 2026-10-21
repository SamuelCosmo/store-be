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
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_store_idempotency", columnNames = { "store_id", "idempotency_key" }))
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

    /** Efectivo entregado por el cliente — solo pagos CASH. */
    private BigDecimal cashReceived;

    /** Cambio devuelto = cashReceived - total — solo pagos CASH. */
    private BigDecimal changeGiven;

    /** Fichas cobradas — solo pagos TOKEN (el dinero se registró al venderlas). */
    private Integer tokenAmount;

    /** Header Idempotency-Key del cobro — un reintento con el mismo key
     *  devuelve esta orden en vez de crear otra. Null en órdenes viejas. */
    @Column(length = 64)
    private String idempotencyKey;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Bloqueo optimista: dos cambios de estado simultáneos (ej. dos
     *  pantallas cancelando la misma orden) → el segundo falla con 409 en
     *  vez de reponer inventario dos veces. Filas viejas arrancan en 0. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint not null default 0")
    private long version;

    /** Staff que canceló — null en órdenes viejas o no canceladas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    private Instant cancelledAt;

    /** Cuándo pasó a READY — nullable en órdenes viejas o canceladas antes. */
    private Instant readyAt;

    /** Cuándo se entregó (COMPLETED) — base de la métrica a tiempo/atrasado. */
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public enum Channel { POS, KIOSK }

    public enum OrderType { DINE_IN, TAKEAWAY }

    public enum PaymentMethod { CASH, CARD, TOKEN }

    public enum Status { PENDING, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED }

    public enum RefundStatus { NONE, REFUNDED }
}
