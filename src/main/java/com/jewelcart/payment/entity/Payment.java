package com.jewelcart.payment.entity;

import com.jewelcart.common.entity.BaseEntity;
import com.jewelcart.common.enums.CurrencyCode;
import com.jewelcart.common.enums.PaymentGatewayType;
import com.jewelcart.common.enums.PaymentStatus;
import com.jewelcart.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId;

    // ManyToOne — one order can have multiple payment attempts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    @Column(name = "currency", nullable = false, columnDefinition = "currency_code")
    private CurrencyCode currency = CurrencyCode.INR;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    @Column(name = "status", nullable = false, columnDefinition = "payment_status")
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Builder.Default
    @Column(name = "gateway", nullable = false, columnDefinition = "payment_gateway")
    private PaymentGatewayType gateway = PaymentGatewayType.RAZORPAY;

    @Column(name = "gateway_reference")
    private String gatewayReference;      // Razorpay's payment ID after success

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;        // prevents double charging
}
