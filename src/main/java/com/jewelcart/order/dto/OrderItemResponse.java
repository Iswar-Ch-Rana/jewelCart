package com.jewelcart.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String productName,
        String primaryImageUrl,  // null if no primary image
        Long variantId,          // null if no variant
        String variantName,      // null if no variant
        Integer quantity,
        BigDecimal unitPrice,    // price per item
        BigDecimal gstRate,      // GST percentage e.g. 3.00
        BigDecimal gstAmount,    // calculated GST amount
        BigDecimal totalPrice    // unitPrice × quantity + gstAmount
) {
}
