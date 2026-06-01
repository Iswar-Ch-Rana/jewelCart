package com.jewelcart.product.dto;

import com.jewelcart.common.enums.GenderType;
import com.jewelcart.common.enums.MetalType;
import com.jewelcart.common.enums.OccasionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProductRequest(

        @NotBlank(message = "Product name is required")
        String name,

        String description,

        @NotBlank(message = "SKU is required")
        String sku,

        @NotNull(message = "Vendor is required")
        Long vendorId,

        Long categoryId,        // optional — product may not have category yet

        @NotNull(message = "Base price is required")
        @Positive(message = "Base price must be greater than 0")
        BigDecimal basePrice,

        @NotNull(message = "Selling price is required")
        @Positive(message = "Selling price must be greater than 0")
        BigDecimal sellingPrice,

        BigDecimal gstRate,     // optional — defaults to 3.00 in entity

        MetalType metalType,    // optional enum
        BigDecimal weightGrams,
        String purity,
        String stoneType,
        OccasionType occasion,  // optional enum
        GenderType gender       // optional enum
) {
}
