CREATE TABLE orders
(
    id               BIGSERIAL PRIMARY KEY,
    order_number     VARCHAR(20) UNIQUE NOT NULL,
    user_id          BIGINT             NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status           order_status       NOT NULL DEFAULT 'PENDING',
    subtotal         DECIMAL(14, 2)     NOT NULL,
    gst_amount       DECIMAL(14, 2)     NOT NULL,
    total_amount     DECIMAL(14, 2)     NOT NULL,
    shipping_address TEXT,
    notes            TEXT,
    created_at       TIMESTAMP          NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP          NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
