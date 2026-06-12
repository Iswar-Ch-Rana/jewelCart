package com.jewelcart.payment.entity;

import com.jewelcart.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

@Getter                         // read only — no setters (immutable audit record)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // no cascade — PaymentEvent doesn't own Payment lifecycle
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "from_status", columnDefinition = "payment_status")
    private PaymentStatus fromStatus;   // null on first event (INITIATED)

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "to_status", columnDefinition = "payment_status")
    private PaymentStatus toStatus;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;          // set manually — no BaseEntity
}
