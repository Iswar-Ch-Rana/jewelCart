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

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "size")
    private String size;

    @Column(name = "color")
    private String color;

    @Builder.Default
    @Column(name = "additional_price", nullable = false)
    private BigDecimal additionalPrice = BigDecimal.valueOf(0);

    @Column(name = "sku_suffix")
    private String skuSuffix;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
