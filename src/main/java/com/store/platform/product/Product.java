package com.store.platform.product;

import com.store.platform.category.Category;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    /** Visible in POS/kiosk. */
    @Column(nullable = false)
    private boolean active;

    /** If > 0, the product can be redeemed with physical tokens (drinks). */
    @Column(nullable = false)
    private int tokenCost;
}
