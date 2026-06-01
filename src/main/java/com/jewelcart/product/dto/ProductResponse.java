package com.jewelcart.product.dto;

import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String sku,

        Long vendorId,          // vendor info
        String vendorName,

        Long categoryId,        // category info
        String categoryName,

        BigDecimal basePrice,
        BigDecimal sellingPrice,
        BigDecimal gstRate,

        MetalType metalType,
        BigDecimal weightGrams,
        String purity,
        String stoneType,
        OccasionType occasion,
        GenderType gender,

        Boolean isActive,
        Boolean isFeatured,

        List<String> imageUrls, // all images for detail page

        Instant createdAt,
        Instant updatedAt
) {
}
