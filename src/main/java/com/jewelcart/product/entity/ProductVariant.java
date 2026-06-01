package com.jewelcart.product.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "variant_name", length = 100)
    private String variantName;

    @Column(name = "size", length = 20)
    private String size;

    @Column(name = "color", length = 50)
    private String color;

    @Builder.Default
    @Column(name = "additional_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal additionalPrice = BigDecimal.valueOf(0);

    @Column(name = "sku_suffix", length = 20)
    private String skuSuffix;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
