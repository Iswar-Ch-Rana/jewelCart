package com.jewelcart.payment.gateway.dto;

import java.math.BigDecimal;

public record GatewayOrderResponse(
        String gatewayOrderId,   // Razorpay's order ID
        BigDecimal amount,
        String currency,
        String status
) {
}
