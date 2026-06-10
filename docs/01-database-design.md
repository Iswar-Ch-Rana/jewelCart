# 01 — Database Design

## Flyway Migrations

### The Golden Rule
```
Never modify a migration that has already run.
Always create a new version to fix mistakes.
```

### Why Flyway Over Hibernate ddl-auto
```yaml
# NEVER use these in production
ddl-auto: create       # drops ALL tables on startup — data destroyed
ddl-auto: update       # tries to alter tables — dangerous, unpredictable
ddl-auto: create-drop  # drops tables on shutdown — data destroyed

# ALWAYS use this
ddl-auto: validate     # only checks entities match DB — never touches data
```

Flyway owns the schema. Hibernate only validates.

### Migration File Naming
```
V1__create_enums.sql
V2__create_vendors_table.sql
V3__create_categories_table.sql
...
V14__add_audit_columns_to_orders_and_items.sql
```
Format: `V{version}__{description}.sql`
Double underscore between version and description.

### Migration Order Matters
Enums first — tables reference them:
```
V1 → enums         (no dependencies)
V2 → vendors       (no FK dependencies)
V3 → categories    (self-referencing FK)
V4 → products      (FK to vendors, categories)
V5 → inventory     (FK to products)
V6 → users         (no FK dependencies)
V7 → orders        (FK to users)
V8 → order_items   (FK to orders, products)
V9 → payments      (FK to orders)
V10 → cart         (FK to users, products)
```

---

## PostgreSQL ENUMs

### Why ENUMs
```sql
-- Without ENUM: any string accepted
status VARCHAR(20)  -- "SHIPED" (typo) accepted silently

-- With ENUM: only valid values accepted
status order_status  -- "SHIPED" → ERROR at DB level
```

### Creating ENUMs
```sql
-- V1__create_enums.sql
CREATE TYPE order_status AS ENUM (
    'PENDING', 'CONFIRMED', 'PROCESSING',
    'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED'
);
```

### Adding New Values to ENUM
```sql
-- New migration file
ALTER TYPE order_status ADD VALUE 'ON_HOLD';
```

### ENUM in Java Entity
```java
@Enumerated(EnumType.STRING)
@JdbcType(PostgreSQLEnumJdbcType.class)          // required for PostgreSQL native ENUMs
@Column(name = "status", columnDefinition = "order_status")
private OrderStatus status;
```

**Why all three annotations:**
- `@Enumerated(STRING)` → store name not position
- `@JdbcType(PostgreSQLEnumJdbcType)` → cast to PostgreSQL native type
- `columnDefinition` → tells Hibernate the exact PostgreSQL type name

### EnumType.ORDINAL vs STRING
```
ORDINAL → stores position number (0, 1, 2...)
  DANGER: add value in middle → all existing data shifts position → silent corruption

STRING  → stores name ("PENDING", "CONFIRMED"...)
  SAFE: adding values anywhere never affects existing data

ALWAYS use STRING. Never use ORDINAL.
```

### Stable vs Frequently Changing ENUMs
```
Use PostgreSQL ENUM for stable values:
  order_status, payment_status, user_role — rarely change

Use VARCHAR for frequently changing values:
  metal_type, occasion — new values added often
  Adding to ENUM requires migration; VARCHAR doesn't
```

---

## Constraints

### NOT NULL
```sql
name VARCHAR(100) NOT NULL  -- required field
phone VARCHAR(15)           -- optional field
```

### UNIQUE
```sql
email VARCHAR(255) UNIQUE NOT NULL
sku   VARCHAR(50)  UNIQUE NOT NULL
```

### CHECK
```sql
CONSTRAINT chk_quantity    CHECK (quantity >= 0)
CONSTRAINT chk_base_price  CHECK (base_price >= 0)
CONSTRAINT chk_selling_price CHECK (selling_price >= 0)
```

### UNIQUE NULLS NOT DISTINCT (PostgreSQL 15+)
```sql
-- In cart_items: same product+variant can't appear twice
-- But variant_id can be NULL (no variant)
-- Without NULLS NOT DISTINCT: two rows with variant_id=NULL are NOT considered duplicates
-- With NULLS NOT DISTINCT: two rows with variant_id=NULL ARE considered duplicates
CONSTRAINT uq_cart_item UNIQUE NULLS NOT DISTINCT (cart_id, product_id, variant_id)
```

### ON DELETE Behavior
```sql
ON DELETE CASCADE  -- parent deleted → children deleted automatically
                   -- use when: children have no meaning without parent
                   -- example: order_items → orders, cart_items → carts

ON DELETE RESTRICT -- parent deletion blocked if children exist
                   -- use when: children must not be lost
                   -- example: orders → users (order history must survive)

ON DELETE SET NULL -- parent deleted → FK set to null
                   -- use when: child can exist without parent
                   -- example: products.category_id (product survives category deletion)
```

---

## Indexes

### Why Indexes
Without index: full table scan — reads every row.
With index: B-tree lookup — O(log n).

### Indexes We Created
```sql
-- composite index — most common query pattern
CREATE INDEX idx_products_vendor   ON products(vendor_id, is_active);
CREATE INDEX idx_products_category ON products(category_id, is_active);

-- partial index — only indexes featured products (small subset)
CREATE INDEX idx_products_featured ON products(is_featured) WHERE is_featured = true;

-- partial index — only low stock items
CREATE INDEX idx_stock_low ON stock(quantity) WHERE quantity <= low_stock_threshold;
```

### When to Add Index
```
Add index when:
  → column appears in WHERE clause frequently
  → column is a foreign key (JOIN target)
  → column used for ORDER BY on large tables
  → column used for pagination

Don't add index on:
  → every column (indexes slow down writes)
  → low cardinality columns (boolean, few distinct values)
  → small tables (full scan is faster)
```

### Interview Answer
*"We add composite indexes on frequently queried column combinations. For example `(vendor_id, is_active)` because almost every vendor query also filters by active status. We use partial indexes for sparse data like featured products — only indexing the small subset where `is_featured = true` keeps the index tiny and fast."*

---

## Soft Delete Strategy

### Rule
```
Never hard delete in production.
```

### Why
```sql
-- Hard delete
DELETE FROM products WHERE id = 1;
-- order_items.product_id = 1 → ON DELETE RESTRICT → ERROR
-- Even if allowed: order history loses product reference
-- Financial audit: incomplete
-- Customer support: "what did I order?" → no answer
```

### Implementation
```sql
-- Every table has is_active column
is_active BOOLEAN NOT NULL DEFAULT true

-- "Delete" = soft delete
UPDATE products SET is_active = false WHERE id = 1;

-- Queries filter inactive
SELECT * FROM products WHERE is_active = true;
```

### What Gets Soft Deleted
```
Users     → is_active = false (never hard delete)
Vendors   → is_active = false
Products  → is_active = false
Categories → is_active = false (cascades to children)
```

### What Never Gets Deleted
```
Orders         → immutable (financial records)
Order Items    → immutable
Payments       → immutable
Payment Events → immutable (audit trail)
```

---

## Audit Fields (BaseEntity)

### What BaseEntity Provides
```java
createdAt  → when record was created (set once, never updated)
updatedAt  → when record was last modified (updated on every save)
createdBy  → who created it (email from SecurityContext)
updatedBy  → who last modified it
```

### How It Works
```
@EnableJpaAuditing in main class
→ Spring watches all entities with @EntityListeners(AuditingEntityListener.class)
→ On INSERT: sets createdAt, updatedAt, createdBy, updatedBy
→ On UPDATE: sets updatedAt, updatedBy only
→ createdAt/createdBy: updatable=false → never changed after insert
```

### AuditorAware
```java
// Phase 1: returns "system"
// Phase 2+: returns logged-in user's email from SecurityContext
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> {
        try {
            return Optional.of(SecurityUtils.getCurrentUserEmail());
        } catch (Exception e) {
            return Optional.of("system");  // fallback for unauthenticated ops
        }
    };
}
```

### Tables Without Full Audit
Some tables (stock, cart) don't need all four audit fields — only timestamps.
Fix: add missing columns via new migration rather than removing from BaseEntity.

---

## Separate Stock Table Design

### Why Not Put quantity in products Table
```
Option A (wrong):
  products.quantity = ?
  Problem 1: Gold Necklace has Gold variant (qty=10) and Silver (qty=3)
             WHERE do you store two quantities in one column?
  Problem 2: No stock history — UPDATE overwrites previous value
  Problem 3: Low stock threshold per variant = impossible

Option B (correct — separate stock table):
  stock: product_id=1, variant_id=1, quantity=10  (Gold)
  stock: product_id=1, variant_id=2, quantity=3   (Silver)
  stock: product_id=2, variant_id=null, quantity=8 (no variants)
```

### Rule
```
variant_id = null  → stock at product level (no variants)
variant_id = X     → stock at variant level
quantity = 0       → out of stock
```

---

## Self-Referential Table (Categories)

```sql
CREATE TABLE categories (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    parent_id BIGINT REFERENCES categories(id)  -- references itself!
);
```

Supports unlimited tree depth:
```
Jewellery (id=1, parent_id=null)
└── Gold Plated (id=2, parent_id=1)
    └── Necklaces (id=3, parent_id=2)
        └── Wedding Necklaces (id=4, parent_id=3)
```