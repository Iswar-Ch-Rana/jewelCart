CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    password_hash VARCHAR(255) NOT NULL,
    phone         VARCHAR(15),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    role          user_role    NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT true
);

CREATE INDEX idx_users_email ON users (email);
