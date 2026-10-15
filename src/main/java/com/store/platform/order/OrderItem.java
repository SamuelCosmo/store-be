package com.store.platform.order;

import com.store.platform.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /** unitPrice charged = product.price + size.price + Σ(extra.price × qty), snapshotted at order time. */
    @Column(nullable = false)
    private BigDecimal unitPrice;

    /** Size name snapshot — nullable; survives catalog edits/deletes. */
    private String sizeName;

    /** Extras charged on this item, snapshot as {name, price, quantity}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<ItemExtraSnapshot> extras = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String notes;

    /** Snapshot del tokenCost del producto — fichas por unidad (0/null = no canjeable). */
    private Integer tokenCost;
}
