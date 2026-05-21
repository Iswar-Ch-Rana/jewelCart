CREATE TABLE vendors
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    brand_name VARCHAR(100) NOT NULL,
    gstin      VARCHAR(15),
    email      VARCHAR(255),
    phone      VARCHAR(15),
    address    TEXT,
    is_active  BOOLEAN      NOT NULL DEFAULT true,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100)
);

CREATE INDEX idx_vendors_active ON vendors (is_active);
