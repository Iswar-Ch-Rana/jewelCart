package com.jewelcart.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record DeductStockRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        Long variantId,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity cannot be negative")
        Integer quantity
) {
}
