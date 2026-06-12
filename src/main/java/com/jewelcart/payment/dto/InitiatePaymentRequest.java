package com.jewelcart.payment.dto;

import jakarta.validation.constraints.NotNull;

public record InitiatePaymentRequest(
        @NotNull(message = "Order ID is required")
        Long orderId
) {
}
