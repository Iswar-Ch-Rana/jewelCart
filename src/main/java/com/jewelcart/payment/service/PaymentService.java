package com.jewelcart.payment.service;

import com.jewelcart.common.enums.CurrencyCode;
import com.jewelcart.common.enums.OrderStatus;
import com.jewelcart.common.enums.PaymentGatewayType;
import com.jewelcart.common.enums.PaymentStatus;
import com.jewelcart.common.exception.PaymentGatewayException;
import com.jewelcart.common.exception.ResourceNotFoundException;
import com.jewelcart.order.dto.UpdateOrderStatusRequest;
import com.jewelcart.order.entity.Order;
import com.jewelcart.order.repository.OrderRepository;
import com.jewelcart.order.service.OrderService;
import com.jewelcart.payment.dto.InitiatePaymentRequest;
import com.jewelcart.payment.dto.PaymentResponse;
import com.jewelcart.payment.dto.VerifyPaymentRequest;
import com.jewelcart.payment.entity.Payment;
import com.jewelcart.payment.entity.PaymentEvent;
import com.jewelcart.payment.gateway.PaymentGateway;
import com.jewelcart.payment.gateway.dto.GatewayOrderResponse;
import com.jewelcart.payment.gateway.dto.GatewayVerifyResponse;
import com.jewelcart.payment.repository.PaymentEventRepository;
import com.jewelcart.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * PaymentService — handles full payment lifecycle
 * <p>
 * Key patterns:
 * - Idempotency keys → prevent double charging
 * - Payment events   → immutable audit trail (never update, only insert)
 * - Strategy pattern → PaymentGateway interface, swap Razorpay/PayU via yaml
 * - Webhook first    → always verify signature before processing
 * <p>
 * Payment flow:
 * initiatePayment → verifyAndCapture (or handleWebhook) → refundPayment
 * <p>
 * See: docs/06-order-deep-dive.md for payment flow diagram
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // ─── INITIATE PAYMENT ────────────────────────────────────────────────────

    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {

        // ── Step 1: Check attempt count ───────────────────────────────────────
        // max 3 attempts per order — prevents infinite retry loops
        // each attempt gets its own idempotency key
        int attemptNumber = paymentRepository.countByOrder_Id(request.orderId()) + 1;
        if (attemptNumber > 3) {
            // cancel order — restore stock via state machine PENDING → CANCELLED
            // customer must create a new order
            orderService.cancelOrder(request.orderId());
            throw new PaymentGatewayException(
                    "Maximum payment attempts (3) exceeded for order: " + request.orderId() +
                            ". Order has been cancelled. Please create a new order.");
        }

        // ── Step 2: Idempotency check ─────────────────────────────────────────
        // same key = same request = return existing payment (no new charge)
        // formula: ORDER_{id}_ATTEMPT_{n} — attempt number allows retries after failure
        // without attempt number: failed payment blocks all future attempts
        String idempotencyKey = "ORDER_" + request.orderId() + "_ATTEMPT_" + attemptNumber;
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            // duplicate request — return existing, don't charge again
            return toResponse(existing.get());
        }

        // ── Step 3: Load order ────────────────────────────────────────────────
        // fail fast before calling Razorpay API — no external call if order invalid
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found with id: " + request.orderId()));

        // ── Step 4: Create Razorpay order ─────────────────────────────────────
        // Razorpay creates a payment session → returns gatewayOrderId
        // frontend uses gatewayOrderId to show Razorpay checkout popup
        // amount from order — NEVER trust amount from frontend (security!)
        GatewayOrderResponse gatewayOrder = paymentGateway.createOrder(
                order.getTotalAmount(),
                CurrencyCode.INR.name(),
                order.getOrderNumber()  // receipt = your reference in Razorpay dashboard
        );

        // ── Step 5: Save payment record ───────────────────────────────────────
        // paymentId = placeholder until Razorpay actually captures the payment
        // gatewayReference = Razorpay's order ID ("order_ABC123")
        //   → used later to find this payment when webhook/verify arrives
        Payment payment = Payment.builder()
                .paymentId("PENDING_" + idempotencyKey)   // replaced in verifyAndCapture
                .order(order)
                .amount(order.getTotalAmount())
                .currency(CurrencyCode.INR)
                .status(PaymentStatus.INITIATED)
                .gateway(PaymentGatewayType.RAZORPAY)
                .gatewayReference(gatewayOrder.gatewayOrderId())  // Razorpay order ID
                .idempotencyKey(idempotencyKey)
                .build();

        payment = paymentRepository.save(payment);

        // ── Step 6: Record audit event ────────────────────────────────────────
        // fromStatus = null (first event, no previous status)
        // payment_events is IMMUTABLE — only insert, never update or delete
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .fromStatus(null)                       // no previous status
                .toStatus(PaymentStatus.INITIATED)
                .reason("Payment initiated")
                .createdAt(Instant.now())
                .build());

        return toResponse(payment);
    }

    // ─── VERIFY AND CAPTURE ───────────────────────────────────────────────────

    @Transactional
    public PaymentResponse verifyAndCapture(VerifyPaymentRequest request) {

        // find payment using Razorpay order ID stored as gatewayReference in Step 4
        Payment payment = paymentRepository
                .findByGatewayReference(request.razorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for Razorpay order: " + request.razorpayOrderId()));

        // ── Verify signature ──────────────────────────────────────────────────
        // HMAC_SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret)
        // if signature invalid → payment tampered → reject immediately
        GatewayVerifyResponse verifyResponse = paymentGateway.verifyPayment(
                request.razorpayPaymentId(),
                request.razorpayOrderId(),
                request.razorpaySignature()
        );

        if (!verifyResponse.valid()) {
            // record failed event — audit trail shows tampered payment attempt
            paymentEventRepository.save(PaymentEvent.builder()
                    .payment(payment)
                    .fromStatus(payment.getStatus())
                    .toStatus(PaymentStatus.FAILED)
                    .reason("Payment verification failed — invalid signature")
                    .createdAt(Instant.now())
                    .build());
            payment.setStatus(PaymentStatus.FAILED);
            // dirty checking saves the FAILED status automatically
            throw new PaymentGatewayException(
                    "Payment verification failed — invalid signature");
        }

        // ── Update payment ────────────────────────────────────────────────────
        PaymentStatus oldStatus = payment.getStatus();
        payment.setPaymentId(request.razorpayPaymentId()); // replace placeholder with real ID
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayReference(request.razorpayOrderId());
        // dirty checking saves automatically on transaction commit

        // record success event
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .fromStatus(oldStatus)
                .toStatus(PaymentStatus.SUCCESS)
                .reason("Payment verified and captured successfully")
                .createdAt(Instant.now())
                .build());

        // ── Confirm order ─────────────────────────────────────────────────────
        // state machine: PENDING → CONFIRMED
        // only after payment is verified — not before
        orderService.updateOrderStatus(
                payment.getOrder().getId(),
                new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)
        );

        return toResponse(payment);
    }

    // ─── HANDLE WEBHOOK ───────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(String payload, String signature) {

        // ── Step 1: Verify signature FIRST ────────────────────────────────────
        // CRITICAL: verify before ANY processing
        // attacker could POST fake webhook to mark failed payments as SUCCESS
        // HMAC_SHA256(payload, webhookSecret) must match X-Razorpay-Signature header
        if (!paymentGateway.verifyWebhookSignature(payload, signature)) {
            throw new PaymentGatewayException("Invalid webhook signature — rejected");
        }

        // ── Step 2: Parse event ───────────────────────────────────────────────
        JSONObject json = new JSONObject(payload);
        String event = json.getString("event");
        JSONObject paymentData = json.getJSONObject("payload")
                .getJSONObject("payment")
                .getJSONObject("entity");

        String razorpayOrderId = paymentData.getString("order_id");
        String razorpayPaymentId = paymentData.getString("id");

        // ── Step 3: Find payment ──────────────────────────────────────────────
        // use gatewayReference (Razorpay order ID) set in initiatePayment
        Payment payment = paymentRepository.findByGatewayReference(razorpayOrderId)
                .orElse(null);
        if (payment == null) return;  // unknown payment — ignore silently

        // ── Step 4: Handle event ──────────────────────────────────────────────
        // WHY NOT verify here: webhook is server-to-server — no signature from customer
        // signature already verified above using webhookSecret (different from keySecret)
        PaymentStatus oldStatus = payment.getStatus();
        String reason;

        switch (event) {
            case "payment.captured" -> {
                payment.setPaymentId(razorpayPaymentId);
                payment.setStatus(PaymentStatus.SUCCESS);
                reason = "Payment captured via webhook";
                // confirm order — handles case where verifyAndCapture was not called
                // (e.g. user closed browser before calling verify endpoint)
                orderService.updateOrderStatus(
                        payment.getOrder().getId(),
                        new UpdateOrderStatusRequest(OrderStatus.CONFIRMED)
                );
            }
            case "payment.failed" -> {
                payment.setStatus(PaymentStatus.FAILED);
                reason = "Payment failed via webhook: " +
                        paymentData.optString("error_description", "Unknown error");
            }
            default -> {
                return;  // unhandled event — ignore
            }
        }

        // ── Step 5: Record event ──────────────────────────────────────────────
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .fromStatus(oldStatus)
                .toStatus(payment.getStatus())
                .reason(reason)
                .createdAt(Instant.now())
                .build());
        // dirty checking saves payment status automatically
    }

    // ─── REFUND PAYMENT ───────────────────────────────────────────────────────

    @Transactional
    public void refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found with id: " + paymentId));

        // only SUCCESS payments can be refunded
        // INITIATED or FAILED payments were never charged — nothing to refund
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Can only refund successful payments. Current status: " +
                            payment.getStatus());
        }

        // call Razorpay refund API — uses actual payment ID (not placeholder)
        paymentGateway.refundPayment(payment.getPaymentId(), payment.getAmount());

        // update status — dirty checking saves automatically
        payment.setStatus(PaymentStatus.REFUNDED);

        // record refund event
        paymentEventRepository.save(PaymentEvent.builder()
                .payment(payment)
                .fromStatus(PaymentStatus.SUCCESS)
                .toStatus(PaymentStatus.REFUNDED)
                .reason("Full refund initiated")
                .createdAt(Instant.now())
                .build());
    }

    // ─── GET PAYMENT BY ORDER ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrder(Long orderId) {
        return paymentRepository.findByOrder_Id(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for order: " + orderId));
    }

    // ─── PRIVATE HELPERS ─────────────────────────────────────────────────────

    // convert entity → response DTO
    // Phase 2: replace it with MapStruct
    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getPaymentId(),
                payment.getOrder().getId(),
                payment.getOrder().getOrderNumber(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getGateway(),
                payment.getGatewayReference(),
                payment.getIdempotencyKey(),
                payment.getCreatedAt()
        );
    }

}
