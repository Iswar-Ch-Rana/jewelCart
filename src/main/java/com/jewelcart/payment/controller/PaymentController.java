package com.jewelcart.payment.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.payment.dto.InitiatePaymentRequest;
import com.jewelcart.payment.dto.PaymentResponse;
import com.jewelcart.payment.dto.VerifyPaymentRequest;
import com.jewelcart.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.jewelcart.common.dto.ApiResponse.success;

@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    // POST /v1/payments/initiate — authenticated user initiates payment
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(@RequestBody @Valid InitiatePaymentRequest request) {
        return ResponseEntity.ok(success("Payment initiated successfully", paymentService.initiatePayment(request)));
    }

    // POST /v1/payments/verify — verify Razorpay signature after checkout
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyAndCapture(@RequestBody @Valid VerifyPaymentRequest request) {
        return ResponseEntity.ok(success("Payment verified successfully", paymentService.verifyAndCapture(request)));
    }

    // POST /v1/payments/webhook — PUBLIC, called by Razorpay server
    // raw String body — never parse before verifying signature
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();  // 200 — Razorpay expects 200, not 204
    }

    // GET /v1/payments/order/{orderId} — get payment status for an order
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(success("Payment retrieved successfully", paymentService.getPaymentByOrder(orderId)));
    }

    // POST /v1/payments/{id}/refund — ADMIN only
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refundPayment(@PathVariable Long id) {
        paymentService.refundPayment(id);
        return ResponseEntity.noContent().build();
    }
}