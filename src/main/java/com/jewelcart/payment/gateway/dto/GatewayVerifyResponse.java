package com.jewelcart.payment.gateway.dto;

public record GatewayVerifyResponse(
        boolean valid,           // signature valid or not
        String paymentId,
        String orderId
) {
}
