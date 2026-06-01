package com.jewelcart.product.dto;

import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateProductRequest(

        @NotBlank(message = "Product name is required")
        String name,

        String description,
        // no sku — not updatable
        // no vendorId — not updatable

        Long categoryId,        // category can change

        @Positive(message = "Base price must be greater than 0")
        BigDecimal basePrice,

        @Positive(message = "Selling price must be greater than 0")
        BigDecimal sellingPrice,

        BigDecimal gstRate,
        MetalType metalType,
        BigDecimal weightGrams,
        String purity,
        String stoneType,
        OccasionType occasion,
        GenderType gender,
        Boolean isFeatured      // admin can feature/unfeature
) {
}
