CREATE TABLE payments
(
    id                BIGSERIAL PRIMARY KEY,
    payment_id        VARCHAR(50) UNIQUE NOT NULL,
    order_id          BIGINT             NOT NULL REFERENCES orders (id),
    amount            DECIMAL(14, 2)     NOT NULL,
    currency          currency_code      NOT NULL DEFAULT 'INR',
    status            payment_status     NOT NULL DEFAULT 'INITIATED',
    gateway           payment_gateway    NOT NULL DEFAULT 'RAZORPAY',
    gateway_reference VARCHAR(100),
    idempotency_key   VARCHAR(100) UNIQUE,
    created_at        TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP          NOT NULL DEFAULT NOW()
);

CREATE TABLE payment_events
(
    id          BIGSERIAL PRIMARY KEY,
    payment_id  BIGINT    NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    from_status payment_status,
    to_status   payment_status,
    reason      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payment_events_payment_id ON payment_events (payment_id);
