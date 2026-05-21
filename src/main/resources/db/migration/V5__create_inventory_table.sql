CREATE TABLE product_images
(
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT          NOT NULL DEFAULT 0,
    is_primary    BOOLEAN      NOT NULL DEFAULT false
);

CREATE TABLE product_variants
(
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT         NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    variant_name     VARCHAR(100),
    size             VARCHAR(20),
    color            VARCHAR(50),
    additional_price DECIMAL(14, 2) NOT NULL DEFAULT 0,
    sku_suffix       VARCHAR(20),
    is_active        BOOLEAN        NOT NULL DEFAULT true
);

CREATE TABLE stock
(
    id                  BIGSERIAL PRIMARY KEY,
    product_id          BIGINT    NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    variant_id          BIGINT REFERENCES product_variants (id) ON DELETE RESTRICT,
    quantity            INT       NOT NULL DEFAULT 0,
    low_stock_threshold INT       NOT NULL DEFAULT 5,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_quantity CHECK (quantity >= 0),
    CONSTRAINT uq_stock_product_variant UNIQUE (product_id, variant_id)
);

CREATE INDEX idx_stock_product ON stock (product_id);
CREATE INDEX idx_stock_low ON stock (quantity) WHERE quantity <= low_stock_threshold;
