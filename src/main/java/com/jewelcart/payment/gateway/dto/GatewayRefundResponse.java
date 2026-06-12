package com.jewelcart.payment.gateway.dto;

import java.math.BigDecimal;

public record GatewayRefundResponse(
        String refundId,
        BigDecimal amount,
        String status
) {
}
