package com.jewelcart.order.dto;

import com.jewelcart.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        BigDecimal subtotal,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String shippingAddress,
        String notes,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
