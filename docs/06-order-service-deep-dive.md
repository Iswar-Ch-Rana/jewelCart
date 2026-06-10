# JewelCart — OrderService Engineering Learnings

## What This Document Is

Every engineering decision made while building `OrderService`.
Not just what we built — but WHY, what can go wrong, and how to explain it in an interview.

---

## 1. State Machine Pattern

### What It Is

Order status follows strict transition rules. Not every status can go to every other status.

### Valid Transitions

```
PENDING   → CONFIRMED, CANCELLED
CONFIRMED → PROCESSING, CANCELLED
PROCESSING → SHIPPED
SHIPPED   → DELIVERED
DELIVERED → (terminal — nothing)
CANCELLED → (terminal — nothing)
REFUNDED  → (terminal — nothing)
```

### Why It Matters

Without state machine validation:

```java
order.setStatus(DELIVERED);  // even if never shipped, corrupt data
```

With state machine:

```java
private void validateTransition(OrderStatus current, OrderStatus next) {
    boolean valid = switch (current) {
        case PENDING -> next == CONFIRMED || next == CANCELLED;
        case CONFIRMED -> next == PROCESSING || next == CANCELLED;
        case PROCESSING -> next == SHIPPED;
        case SHIPPED -> next == DELIVERED;
        case DELIVERED, CANCELLED, REFUNDED -> false;  // terminal
    };
    if (!valid) throw new IllegalStateException("Invalid: " + current + " → " + next);
}
```

### Interview Answer

*"Order status follows the State Machine pattern. Each status has defined valid transitions — you can't ship an
unconfirmed order. We validate transitions in the service layer before updating status, throwing `IllegalStateException`
for illegal transitions. This prevents data corruption and enforces business rules at the application level."*

---

## 2. The N+1 Problem in Order Placement

### The Problem (Old Approach)

```java
// 30 items = 30 separate DB queries
for(OrderItemRequest item :request.items()){
    Product product = productRepository.findById(item.productId()); // query per item
}
```

### The Fix (Bulk Loading)

```java
// Extract all IDs first
List<Long> productIds = sortedItems.stream()
                .map(OrderItemRequest::productId)
                .toList();

// ONE query for all products — WHERE id IN (1,2,3...30)
Map<Long, Product> productMap = productRepository.findAllById(productIds)
        .stream()
        .collect(Collectors.toMap(Product::getId, p -> p));
```

**Result:** 30 items = 2 queries (products + variants) instead of 60.

### Interview Answer

*"Loading entities one by one in a loop is the N+1 problem. For batch operations we extract all IDs first then
use `findAllById()` which generates a single `WHERE id IN (...)` query. This reduces 60 queries to 2 regardless of order
size."*

---

## 3. Pessimistic Locking + Deadlock Prevention

### The Race Condition Problem

```
Thread 1: reads stock = 1, passes check
Thread 2: reads stock = 1, passes check
Thread 1: deducts → stock = 0
Thread 2: deducts → stock = -1  ← OVERSOLD!
```

### The Fix — Pessimistic Locking

```java

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Stock s WHERE s.product.id = :productId ...")
Optional<Stock> findByProductAndVariantWithLock(...);
```

Generated SQL:

```sql
SELECT *
FROM stock
WHERE product_id = ? FOR UPDATE
```

`FOR UPDATE` blocks other transactions until this one commits.

### The Deadlock Problem

```
Thread 1: locks product 1, wants product 2
Thread 2: locks product 2, wants product 1
Both waiting for each other = DEADLOCK
```

### The Fix — Sort by productId

```java
List<OrderItemRequest> sortedItems = request.items().stream()
        .sorted(Comparator.comparing(OrderItemRequest::productId))
        .toList();
```

Both threads now lock in same order → no deadlock.

### Interview Answer

*"We sort items by productId before locking to prevent deadlocks. If two transactions always acquire locks in the same
order, they can never deadlock — Thread 2 waits for Thread 1 to release lock on product 1 before proceeding, instead of
each holding what the other needs."*

---

## 4. The Four-Phase placeOrder Design

### Why Four Phases?

Old approach mixed data loading, locking, and calculation together.
New approach separates concerns and minimizes lock duration.

### Phase 1 — Load All Data (No Locks)

```java
// Fail fast before acquiring any locks
// Load products and variants in bulk — 2 queries total
// Validate all exist — throw ResourceNotFoundException early
```

**Why:** If a product doesn't exist, fail before locking anything. No cleanup needed.

### Phase 2 — Deduct Stock (Locks Held Briefly)

```java
// Locks acquired in sorted productId order — prevents deadlock
// Each deductStock call: lock row → deduct → release (per item)
for(OrderItemRequest item :sortedItems){
    stockService.deductStock(...);
}
```

**Why sorted:** Consistent lock ordering prevents deadlock between concurrent transactions.

### Phase 3 — Calculate Prices (No DB Calls)

```java
// Pure Java math — productMap already loaded in Phase 1
// unitPrice = sellingPrice + variantAdditionalPrice
// gstAmount = itemSubtotal × gstRate / 100
// totalPrice = itemSubtotal + gstAmount
```

**Why separate:** No DB calls during calculation. If math fails, no locks held.

### Phase 4 — Save Order (Single Write)

```java
// One INSERT for order + cascade saves all items
orderItems.forEach(item ->item.setOrder(order));

order.getItems().addAll(orderItems);

orderRepository.save(order);  // cascade handles items
```

**Why single save:** `CascadeType.ALL` on `Order.items` — one save persists everything.

---

## 5. Transaction Propagation — The Hidden Problem

### What We Discovered

`@Transactional` on `placeOrder` + `@Transactional` on `deductStock`:

```
Default propagation = REQUIRED
→ inner method joins outer transaction
→ pessimistic locks held until outer transaction commits
→ all product locks held for entire order placement duration
```

### The Real Fix (Phase 5)

```java
// REQUIRES_NEW creates independent transaction
@Transactional(propagation = Propagation.REQUIRES_NEW)
public StockResponse deductStock(...) {
    // commits immediately → lock released immediately
}
```

**Trade-off:** If order save fails after stock deducted — stock already committed. Need compensating transaction to
restore.

### Phase 5 Solution — Kafka

```
Place order → return "Order received" immediately
Kafka event → OrderPlacedEvent
Consumer deducts stock → StockDeductedEvent
Consumer saves order → OrderConfirmedEvent
If anything fails → compensating event restores stock
```

No locks held during HTTP request at all.

### TODO Comment in Code

```java
// TODO Phase 5: move to Kafka async processing
// Currently all operations in single transaction
// Pro: atomic — stock and order always consistent
// Con: locks held longer — acceptable at current scale
```

---

## 6. Stock Restoration on Cancel

### Why Important

When order is canceled, stock must be restored:

```java

@Transactional
public OrderResponse cancelOrder(Long id) {
    // validate transition (PENDING/CONFIRMED → CANCELLED only)
    validateTransition(order.getStatus(), OrderStatus.CANCELLED);

    // restore stock for each cancelled item
    order.getItems().forEach(item ->
            stockService.restock(new RestockRequest(
                    item.getProduct().getId(),
                    item.getVariant() != null ? item.getVariant().getId() : null,
                    item.getQuantity()
            ))
    );

    order.setStatus(OrderStatus.CANCELLED);
}
```

Both stock restoration AND status update in same `@Transactional`.
If either fails → both roll back → data stays consistent.

---

## 7. Price Calculation — BigDecimal Rules

### Never Use Double for Money

```java
// Wrong — floating point imprecision
double price = 0.1 + 0.2;  // = 0.30000000000000004

// Right — exact decimal arithmetic
BigDecimal price = new BigDecimal("0.1").add(new BigDecimal("0.2"));
```

### Always Specify Rounding Mode for Division

```java
// Wrong — throws ArithmeticException for non-terminating decimals
.divide(BigDecimal.valueOf(100))

// Right — 2 decimal places, standard financial rounding
.divide(BigDecimal.valueOf(100), 2,RoundingMode.HALF_UP)
```

### Price Formula

```
unitPrice    = sellingPrice + variantAdditionalPrice (if variant exists)
itemSubtotal = unitPrice × quantity
gstAmount    = itemSubtotal × gstRate / 100  (HALF_UP rounding)
totalPrice   = itemSubtotal + gstAmount

order.subtotal    = sum of all itemSubtotals
order.gstAmount   = sum of all gstAmounts
order.totalAmount = subtotal + gstAmount
```

---

## 8. Order Number Generation

### Approach

```java
private String generateOrderNumber() {
    String datePart = LocalDate.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    String msPart = String.valueOf(System.currentTimeMillis()).substring(9);
    return "ORD-" + datePart + "-" + msPart;
    // Output: ORD-20240601-7890
}
```

**Why not UUID:** UUID is not human readable. Customer can't read it to support agent.
**Why milliseconds:** Simple, practically unique at low volume, sortable.
**Collision risk:** Two orders same millisecond = same number. Acceptable at Phase 1 scale.

### Phase 5 Fix — Database Sequence

```sql
CREATE SEQUENCE order_seq START 10000 INCREMENT 1;
```

```java
SELECT 'ORD-'||

TO_CHAR(NOW(),'YYYYMMDD')||'-'||

NEXTVAL('order_seq')
```

Database guarantees uniqueness — no collision possible.

---

## 9. SecurityContextHolder — Getting Current User

### How It Works

`JwtAuthFilter` sets the authenticated user on every request:

```java
SecurityContextHolder.getContext().setAuthentication(authToken);
```

### Retrieving in Service

```java
// SecurityUtils.java — reusable across all services
public static User getCurrentUser() {
    return (User) SecurityContextHolder.getContext()
            .getAuthentication()
            .getPrincipal();
}
```

No extra DB call needed — user already loaded by `JwtAuthFilter`.

### Why Cast to `User`

`JwtAuthFilter` sets your `User` entity as the principal (not a generic `UserDetails`).
Safe to cast directly.

---

## 10. EntityGraph vs JOIN FETCH

### The Problem with JOIN FETCH + Collections

```sql
-- JOIN FETCH items AND images simultaneously
ORDER has 3 items × 2 images = 6 rows (Cartesian product)
Hibernate: MultipleBagFetchException
```

### The Fix — @EntityGraph

```java

@EntityGraph(attributePaths = {"items", "items.product", "items.product.images"})
@Query("SELECT o FROM Order o WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);
```

Hibernate generates separate queries:

```sql
Query
1:
SELECT *
FROM orders
WHERE id = ? Query 2:
SELECT *
FROM order_items
WHERE order_id IN (?) Query 3:
SELECT *
FROM products
WHERE id IN (1, 2, 3) Query 4:
SELECT *
FROM product_images
WHERE product_id IN (1, 2, 3)
```

Clean, no duplicates, no Cartesian product.

### Interview Answer

*"Fetching multiple collections with JOIN FETCH causes MultipleBagFetchException — Hibernate can't handle the Cartesian
product of multiple collections. @EntityGraph solves this by fetching each association in separate optimized queries.
Three items × two images = six rows with JOIN FETCH, three plus two = five rows with EntityGraph."*

---

## 11. Cascade + OrphanRemoval

```java
// In Order entity
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
private final List<OrderItem> items = new ArrayList<>();
```

**CascadeType.ALL:**

- Save order → items saved automatically
- Delete order → items deleted automatically

**orphanRemoval = true:**

- Remove item from list → item deleted from DB automatically
- Without it: item removed from Java list but stays in database (orphan)

---

## 12. What We Would Do Differently at Scale

| Problem                | Phase 1 Solution      | Phase 5 Solution                   |
|------------------------|-----------------------|------------------------------------|
| Lock contention        | Sort by productId     | Kafka async processing             |
| Order number collision | Millisecond suffix    | DB sequence                        |
| Stock deduction        | Sync in transaction   | Async Kafka consumer               |
| Price calculation      | In service            | Could be dedicated pricing service |
| Image loading          | Lazy + filter in Java | Redis cache                        |

---

## Interview Questions You Can Now Answer

**Q: Two customers buy last item simultaneously. What happens?**
Pessimistic locking. First transaction locks the stock row. Second waits. First commits, stock = 0. Second reads 0,
throws InsufficientStockException. One customer gets it, one gets a clear error. No overselling.

**Q: What if order save fails after stock was deducted?**
With REQUIRED propagation — both roll back atomically. Stock restored automatically. With REQUIRES_NEW — need
compensating transaction to restore stock manually. Phase 5 uses Kafka with compensating events.

**Q: How do you prevent deadlocks with pessimistic locking?**
Sort items by productId before locking. Both threads acquire locks in same order. No circular dependency. No deadlock.

**Q: Why four phases instead of one loop?**
Minimize lock duration. Fail fast before acquiring locks. Separate concerns. Phase 1 loads data, Phase 2 holds locks
briefly, Phase 3 calculates without DB calls, Phase 4 saves once.

**Q: How do you generate unique order numbers?**
Date + last 4 millisecond digits. Human readable, sortable, practically unique at current scale. At scale: database
sequence guarantees uniqueness without any collision risk.
