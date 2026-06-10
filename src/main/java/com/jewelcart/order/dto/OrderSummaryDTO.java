package com.jewelcart.order.dto;

import com.jewelcart.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryDTO(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal totalAmount,   // how much customer paid
        Integer itemCount,        // how many items
        Instant createdAt         // when placed
) {
}
