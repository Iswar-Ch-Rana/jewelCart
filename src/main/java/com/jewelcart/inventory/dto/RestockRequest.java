package com.jewelcart.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RestockRequest(
        @NotNull(message = "Product ID is required")
        Long productId,

        Long variantId,

        @Min(value = 1, message = "Restock quantity must be at least 1")
        Integer quantity
) {
}
