package com.jewelcart.payment.repository;

import com.jewelcart.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    // full audit trail for a payment — ordered chronologically
    List<PaymentEvent> findByPayment_IdOrderByCreatedAtAsc(Long paymentId);
}
