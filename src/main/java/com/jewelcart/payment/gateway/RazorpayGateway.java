package com.jewelcart.payment.gateway;

import com.jewelcart.common.exception.PaymentGatewayException;
import com.jewelcart.payment.gateway.dto.GatewayOrderResponse;
import com.jewelcart.payment.gateway.dto.GatewayRefundResponse;
import com.jewelcart.payment.gateway.dto.GatewayVerifyResponse;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("razorpay")
public class RazorpayGateway implements PaymentGateway {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    @Value("${razorpay.webhook-secret}")
    private String webhookSecret;

    private RazorpayClient razorpayClient;

    // create client once after @Value fields are injected
    @PostConstruct
    public void init() {
        try {
            this.razorpayClient = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Failed to initialize Razorpay client: " + e.getMessage());
        }
    }

    @Override
    public GatewayOrderResponse createOrder(BigDecimal amount, String currency, String receipt) {
        try {
            JSONObject options = new JSONObject();
            // Razorpay requires amount in paise (₹1 = 100 paise)
            options.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());
            options.put("currency", currency);
            options.put("receipt", receipt);
            options.put("notes", new JSONObject().put("integration", "JewelCart"));

            com.razorpay.Order order = razorpayClient.orders.create(options);

            return new GatewayOrderResponse(
                    order.get("id").toString(),
                    amount,
                    currency,
                    order.get("status").toString()
            );
        } catch (RazorpayException e) {
            throw new PaymentGatewayException(
                    "Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @Override
    public GatewayVerifyResponse verifyPayment(String paymentId, String orderId, String signature) {
        try {
            // verify: HMAC_SHA256(orderId|paymentId, keySecret) == signature
            boolean valid = Utils.verifyPaymentSignature(
                    new JSONObject()
                            .put("razorpay_order_id", orderId)
                            .put("razorpay_payment_id", paymentId)
                            .put("razorpay_signature", signature),
                    keySecret
            );
            return new GatewayVerifyResponse(valid, paymentId, orderId);
        } catch (RazorpayException e) {
            // signature mismatch throws exception — treat as invalid
            return new GatewayVerifyResponse(false, paymentId, orderId);
        }
    }

    @Override
    public GatewayRefundResponse refundPayment(String paymentId, BigDecimal amount) {
        try {
            JSONObject options = new JSONObject();
            options.put("amount", amount.multiply(BigDecimal.valueOf(100)).intValue());

            com.razorpay.Refund refund = razorpayClient.payments.refund(paymentId, options);

            return new GatewayRefundResponse(
                    refund.get("id").toString(),
                    amount,
                    refund.get("status").toString()
            );
        } catch (RazorpayException e) {
            throw new PaymentGatewayException("Refund failed: " + e.getMessage());
        }
    }

    // used for webhook signature verification (different from payment verification)
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            return true;
        } catch (RazorpayException e) {
            return false;
        }
    }

}
