package com.jewelcart.payment.controller;

import com.jewelcart.common.dto.ApiResponse;
import com.jewelcart.payment.dto.InitiatePaymentRequest;
import com.jewelcart.payment.dto.PaymentResponse;
import com.jewelcart.payment.dto.VerifyPaymentRequest;
import com.jewelcart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Payments", description = "Razorpay payment initiation, verification, and webhook APIs")
public class PaymentController {

    private final PaymentService paymentService;

    // POST /v1/payments/initiate — authenticated user initiates payment
    @Operation(summary = "Initiate a payment", description = "Creates a Razorpay order and returns the order ID for the frontend checkout. Idempotent — same request returns the same payment if already initiated.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment initiated — Razorpay order created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or order already paid"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found")
    })
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(@RequestBody @Valid InitiatePaymentRequest request) {
        return ResponseEntity.ok(success("Payment initiated successfully", paymentService.initiatePayment(request)));
    }

    // POST /v1/payments/verify — verify Razorpay signature after checkout
    @Operation(summary = "Verify and capture payment", description = "Called after user completes Razorpay checkout. Verifies HMAC-SHA256 signature using Razorpay secret. Marks order as CONFIRMED on success.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment verified and captured"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or tampered signature"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<PaymentResponse>> verifyAndCapture(@RequestBody @Valid VerifyPaymentRequest request) {
        return ResponseEntity.ok(success("Payment verified successfully", paymentService.verifyAndCapture(request)));
    }

    // POST /v1/payments/webhook — PUBLIC, called by Razorpay server
    // raw String body — never parse before verifying signature
    @Operation(summary = "Razorpay webhook handler", description = "PUBLIC endpoint — called by Razorpay servers on payment events. Verifies X-Razorpay-Signature before processing. Raw body must not be parsed before verification.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Webhook processed — Razorpay requires 200, not 204"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid webhook signature")
    })
    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(@RequestBody String payload, @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();  // 200 — Razorpay expects 200, not 204
    }

    // GET /v1/payments/order/{orderId} — get payment status for an order
    @Operation(summary = "Get payment by order ID", description = "Returns the payment record for a given order. Customer can only view their own order's payment.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied — not your order"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment or order not found")
    })
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(success("Payment retrieved successfully", paymentService.getPaymentByOrder(orderId)));
    }

    // POST /v1/payments/{id}/refund — ADMIN only
    @Operation(summary = "Refund a payment", description = "Admin-only. Initiates a full refund via Razorpay. Only SUCCESS payments can be refunded.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Refund initiated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Payment not in a refundable state"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment not found")
    })
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> refundPayment(@PathVariable Long id) {
        paymentService.refundPayment(id);
        return ResponseEntity.noContent().build();
    }
}