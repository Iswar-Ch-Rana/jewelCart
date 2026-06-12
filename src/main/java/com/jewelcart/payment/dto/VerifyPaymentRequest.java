package com.jewelcart.payment.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPaymentRequest(
        @NotBlank(message = "Payment ID is required")
        String razorpayPaymentId,

        @NotBlank(message = "Order ID is required")
        String razorpayOrderId,

        @NotBlank(message = "Signature is required")
        String razorpaySignature
) {
}
