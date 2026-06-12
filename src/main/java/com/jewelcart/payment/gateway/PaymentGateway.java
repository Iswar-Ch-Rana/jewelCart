package com.jewelcart.payment.gateway;

import com.jewelcart.payment.gateway.dto.GatewayOrderResponse;
import com.jewelcart.payment.gateway.dto.GatewayRefundResponse;
import com.jewelcart.payment.gateway.dto.GatewayVerifyResponse;
import com.razorpay.RazorpayException;

import java.math.BigDecimal;

public interface PaymentGateway {
    GatewayOrderResponse createOrder(BigDecimal amount, String currency, String receipt);

    GatewayVerifyResponse verifyPayment(String paymentId, String orderId, String signature);

    GatewayRefundResponse refundPayment(String paymentId, BigDecimal amount);

    boolean verifyWebhookSignature(String payload, String signature);

}
