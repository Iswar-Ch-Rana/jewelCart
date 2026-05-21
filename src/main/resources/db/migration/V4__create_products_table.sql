CREATE TABLE products
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255)   NOT NULL,
    description   TEXT,
    sku           VARCHAR(50)    NOT NULL,
    vendor_id     BIGINT         NOT NULL REFERENCES vendors (id) ON DELETE RESTRICT,
    category_id   BIGINT         REFERENCES categories (id) ON DELETE SET NULL,
    base_price    DECIMAL(14, 2) NOT NULL,
    selling_price DECIMAL(14, 2) NOT NULL,
    gst_rate      DECIMAL(5, 2)  NOT NULL DEFAULT 3.00,
    metal_type    metal_type,
    weight_grams  DECIMAL(10, 3),
    purity        VARCHAR(20),
    stone_type    VARCHAR(50),
    occasion      occasion_type,
    gender        gender_type,
    is_active     BOOLEAN        NOT NULL DEFAULT true,
    is_featured   BOOLEAN        NOT NULL DEFAULT false,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),

    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT chk_selling_price CHECK (selling_price >= 0),
    CONSTRAINT chk_base_price CHECK (base_price >= 0)
);

CREATE INDEX idx_products_vendor ON products (vendor_id, is_active);
CREATE INDEX idx_products_category ON products (category_id, is_active);
CREATE INDEX idx_products_metal ON products (metal_type, is_active);
CREATE INDEX idx_products_price ON products (selling_price);
CREATE INDEX idx_products_featured ON products (is_featured) WHERE is_featured = true;
