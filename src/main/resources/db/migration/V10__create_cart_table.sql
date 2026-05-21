-- V10__create_cart_table.sql

CREATE TABLE carts
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_user UNIQUE (user_id)
);

CREATE TABLE cart_items
(
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT    NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id BIGINT    NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    variant_id BIGINT    REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity   INT       NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_item UNIQUE NULLS NOT DISTINCT (cart_id, product_id, variant_id)
);

CREATE INDEX idx_cart_items_cart    ON cart_items (cart_id);
CREATE INDEX idx_cart_items_product ON cart_items (product_id);