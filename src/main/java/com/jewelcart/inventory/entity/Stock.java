package com.jewelcart.inventory.entity;

import com.jewelcart.product.entity.Product;
import com.jewelcart.product.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // product this stock belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // null = no variant, stock tracked at product level
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold;

    // manual timestamp — stock table has no full audit trail
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
