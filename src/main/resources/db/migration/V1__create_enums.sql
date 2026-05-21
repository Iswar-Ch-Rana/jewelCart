CREATE TYPE order_status AS ENUM (
    'PENDING',
    'CONFIRMED',
    'PROCESSING',
    'SHIPPED',
    'DELIVERED',
    'CANCELLED',
    'REFUNDED'
);

CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'PENDING',
    'SUCCESS',
    'FAILED',
    'REFUNDED'
);

CREATE TYPE payment_gateway AS ENUM (
    'RAZORPAY'
);

CREATE TYPE metal_type AS ENUM (
    'GOLD_PLATED',
    'SILVER',
    'REAL_GOLD',
    'DIAMOND',
    'GEMSTONE'
);

CREATE TYPE occasion_type AS ENUM (
    'WEDDING',
    'FESTIVAL',
    'DAILY_WEAR',
    'PARTY',
    'OFFICE'
);

CREATE TYPE gender_type AS ENUM (
    'WOMEN',
    'MEN',
    'KIDS',
    'UNISEX'
);

CREATE TYPE user_role AS ENUM (
    'ADMIN',
    'VENDOR',
    'CUSTOMER'
);

CREATE TYPE currency_code AS ENUM (
    'INR'
);
