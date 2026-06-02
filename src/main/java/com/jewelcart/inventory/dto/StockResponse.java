package com.jewelcart.inventory.dto;

import java.time.Instant;

public record StockResponse(
        Long id,
        Long productId,
        String productName,     // avoid extra API call from frontend
        Long variantId,
        Integer quantity,
        Integer lowStockThreshold,
        boolean isLowStock,     // computed: quantity <= lowStockThreshold
        Instant updatedAt       // last stock change time
) {
}
