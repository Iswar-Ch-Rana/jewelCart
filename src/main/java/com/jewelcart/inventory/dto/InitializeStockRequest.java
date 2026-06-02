package com.jewelcart.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record InitializeStockRequest(

        @NotNull(message = "Product ID is required")
        Long productId,

        Long variantId,         // nullable — null means no variant

        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        Integer quantity,

        @NotNull(message = "Low stock threshold is required")
        @Min(value = 1, message = "Threshold must be at least 1")
        Integer lowStockThreshold
) {
}
