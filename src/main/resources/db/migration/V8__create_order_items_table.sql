CREATE TABLE order_items
(
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    variant_id  BIGINT REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_price  DECIMAL(14, 2) NOT NULL CHECK (unit_price >= 0),
    gst_rate    DECIMAL(5, 2)  NOT NULL CHECK (gst_rate >= 0),
    gst_amount  DECIMAL(14, 2) NOT NULL CHECK (gst_amount >= 0),
    total_price DECIMAL(14, 2) NOT NULL CHECK (total_price >= 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_product_id ON order_items (product_id);
