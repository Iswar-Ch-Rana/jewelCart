package com.jewelcart.payment.repository;

import com.jewelcart.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // find by Razorpay's payment ID
    Optional<Payment> findByPaymentId(String paymentId);

    // find payment for a specific order
    Optional<Payment> findByOrder_Id(Long orderId);

    // check if payment already initiated — idempotency check
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // count how many payment attempts for an order (for blocking after 3 attempts)
    int countByOrder_Id(Long orderId);

    Optional<Payment> findByGatewayReference(String gatewayReference);

}
