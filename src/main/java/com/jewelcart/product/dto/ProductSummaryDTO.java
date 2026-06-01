package com.jewelcart.product.dto;

import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductSummaryDTO(
        Long id,
        String name,
        BigDecimal sellingPrice,
        BigDecimal gstRate,
        MetalType metalType,
        OccasionType occasion,
        GenderType gender,
        String primaryImageUrl,   // one image only — list page
        String vendorName,
        String categoryName,
        Boolean isActive,
        Boolean isFeatured,
        Instant createdAt         // for "newest first" sorting
) {
}
