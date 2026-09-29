package com.store.platform.product;

import com.store.platform.category.Category;
import com.store.platform.extra.Extra;
import com.store.platform.size.Size;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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

    /** Optional SKU / internal code shown next to the name. */
    private String sku;

    /** Optional photo URL shown in the admin and kiosk menus. */
    private String image;

    @Column(nullable = false)
    private BigDecimal price;

    /** Visible in POS/kiosk. */
    @Column(nullable = false)
    private boolean active;

    /** If > 0, the product can be redeemed with physical tokens (drinks). */
    @Column(nullable = false)
    private int tokenCost;

    /** Extras offered for this product — shared catalog at client level. */
    @ManyToMany
    @JoinTable(
            name = "product_extras",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "extra_id"))
    @Builder.Default
    private Set<Extra> extras = new HashSet<>();

    /** Sizes offered for this product — shared catalog at client level. */
    @ManyToMany
    @JoinTable(
            name = "product_sizes",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "size_id"))
    @Builder.Default
    private Set<Size> sizes = new HashSet<>();
}
