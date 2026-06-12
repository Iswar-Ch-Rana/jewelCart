// PaymentResponse.java
package com.jewelcart.payment.dto;

import com.jewelcart.common.enums.CurrencyCode;
import com.jewelcart.common.enums.PaymentGatewayType;
import com.jewelcart.common.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String paymentId,           // Razorpay payment ID
        Long orderId,
        String orderNumber,         // for display
        BigDecimal amount,
        CurrencyCode currency,
        PaymentStatus status,
        PaymentGatewayType gateway,
        String gatewayReference,    // Razorpay's reference after capture
        String idempotencyKey,
        Instant createdAt
) {}