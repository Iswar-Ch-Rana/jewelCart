# 03 — Transaction Patterns

## @Transactional Basics

### What a Transaction Does
```
Transaction starts
  → all DB operations grouped together
  → either ALL succeed (commit)
  → or ALL fail (rollback)
Transaction ends
```

### Spring vs Jakarta @Transactional
```java
// Wrong — basic JEE annotation, limited features
import jakarta.transaction.Transactional;

// Right — Spring's version, more control
import org.springframework.transaction.annotation.Transactional;
```

Spring's version gives you:
- `readOnly = true`
- `propagation` control
- `rollbackFor` specific exceptions
- `timeout` settings

### Read vs Write Transactions
```java
// Write operations — full transaction
@Transactional
public VendorResponse createVendor(CreateVendorRequest request) { }

// Read operations — optimized
@Transactional(readOnly = true)
public Page<VendorResponse> getAllVendors(Pageable pageable) { }
```

### Why readOnly = true
Three benefits:
```
1. No write locks acquired → reads don't block other reads
2. Hibernate skips dirty checking → no snapshot comparison on commit
3. Some databases/connection pools route to read replicas automatically
```

---

## Dirty Checking

### How It Works
```
@Transactional starts
  → Hibernate loads entity from DB
  → takes snapshot of entity state

You modify fields:
  vendor.setName("New Name");

@Transactional ends (commit)
  → Hibernate compares current state vs snapshot
  → detects name changed
  → generates: UPDATE vendors SET name=? WHERE id=?
  → commits automatically
```

### No save() Needed for Updates
```java
@Transactional
public VendorResponse updateVendor(Long id, UpdateVendorRequest request) {
    Vendor vendor = vendorRepository.findById(id).orElseThrow();
    vendor.setName(request.name());
    // NO save() call needed — dirty checking handles it
    return toResponse(vendor);
}
```

### When save() IS Required
```java
// New entity — not managed by Hibernate yet
Vendor vendor = Vendor.builder().name("Arihant").build();
vendor = vendorRepository.save(vendor);  // INSERT — must call save()

// Existing entity loaded in transaction — dirty checking handles UPDATE
```

---

## Propagation

### The Hidden Problem
```java
// OrderService
@Transactional                    // outer transaction starts
public OrderResponse placeOrder(...) {

    // StockService
    @Transactional                // what happens here?
    public StockResponse deductStock(...) {
        // pessimistic lock acquired
    }
}
```

### PROPAGATION.REQUIRED (Default)
```
Inner transaction exists → joins outer transaction
Inner transaction doesn't exist → creates new one

Result: stockService.deductStock() joins placeOrder() transaction
→ pessimistic lock held until placeOrder() commits
→ locks held for entire order placement duration
```

```java
// Default — no annotation needed
@Transactional(propagation = Propagation.REQUIRED)
public StockResponse deductStock(...) { }
```

### PROPAGATION.REQUIRES_NEW
```
Always creates a new independent transaction
Outer transaction suspended while inner runs
Inner commits immediately → outer resumes

Result: lock acquired → deducted → committed → lock released immediately
```

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public StockResponse deductStock(...) { }
```

### Trade-off: REQUIRED vs REQUIRES_NEW

|               | REQUIRED                      | REQUIRES_NEW                          |
|---------------|-------------------------------|---------------------------------------|
| Atomicity     | Full — all or nothing         | Partial — inner commits independently |
| Lock duration | Held until outer commits      | Released immediately                  |
| Rollback      | Stock restored if order fails | Stock NOT restored if order fails     |
| Complexity    | Simple                        | Needs compensating transaction        |

### For Phase 1 — Use REQUIRED
```java
// Simple. Atomic. Stock and order are always consistent.
// If order save fails → stock deduction rolls back automatically
// Acceptable lock duration at current scale

// TODO Phase 5: switch to Kafka async
// → no locks during HTTP request at all
```

### For Phase 5 — Kafka Pattern
```
Customer places order → return "Order received" immediately
Kafka: OrderPlacedEvent published
Consumer: deducts stock → publishes StockDeductedEvent
Consumer: saves order → publishes OrderConfirmedEvent
If stock deduction fails → publishes CompensationEvent → order cancelled
```

No locks held during HTTP request. No transaction spanning multiple services.

---

## Pessimistic Locking

### The Race Condition Problem
```
Time  Thread 1 (Customer A)     Thread 2 (Customer B)
0ms   SELECT quantity = 1
1ms                              SELECT quantity = 1
2ms   quantity > 0 ✅ pass
3ms                              quantity > 0 ✅ pass
4ms   UPDATE quantity = 0
5ms                              UPDATE quantity = 0
      
Result: quantity = -1. Two customers bought the last item. OVERSOLD.
```

### The Fix — Pessimistic Write Lock
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Stock s WHERE s.product.id = :productId ...")
Optional<Stock> findByProductAndVariantWithLock(...);
```

Generated SQL:
```sql
SELECT * FROM stock WHERE product_id = ? FOR UPDATE
-- FOR UPDATE = row-level lock
-- Other transactions BLOCK until this one commits
```

### With Lock — Safe
```
Time  Thread 1 (Customer A)     Thread 2 (Customer B)
0ms   SELECT ... FOR UPDATE → acquires lock
1ms   quantity = 1 ✅           SELECT ... FOR UPDATE → BLOCKED
2ms   UPDATE quantity = 0
3ms   COMMIT → lock released
4ms                              → gets lock, reads quantity = 0
5ms                              quantity = 0 ❌ InsufficientStockException
      
Result: Customer A gets it. Customer B gets clear error. No overselling.
```

### Lock Lifecycle
```java
@Transactional          // transaction begins
public void deductStock(...) {
    Stock stock = stockRepository.findByProductAndVariantWithLock(...);
    // ↑ row locked here — SELECT ... FOR UPDATE

    stock.setQuantity(stock.getQuantity() - quantity);
    // changes in memory

}   // @Transactional ends → COMMIT → lock released automatically
```

Lock is ALWAYS released on transaction commit or rollback.
You never release manually.

### Lock Types
```
PESSIMISTIC_WRITE  → exclusive lock
                   → no other transaction can read OR write
                   → use for: inventory deduction, payment processing

PESSIMISTIC_READ   → shared lock
                   → others can read, no one can write
                   → use for: reading data that must not change mid-transaction

OPTIMISTIC         → no DB lock — uses @Version column
                   → fails if another transaction modified same row
                   → use for: low contention, high read scenarios
```

### Interview Answer
*"We use pessimistic locking on stock deduction to prevent race conditions. The `SELECT FOR UPDATE` locks the stock row exclusively. The second transaction blocks until the first commits. By then stock is 0, so the second customer gets InsufficientStockException. The lock releases automatically on transaction commit — we never manage it manually. This is the correct approach for inventory because the cost of overselling is higher than making customers wait milliseconds."*

---

## Deadlock Prevention

### What is a Deadlock
```
Thread 1: locks product 1 → wants product 2
Thread 2: locks product 2 → wants product 1
Both waiting for each other forever = DEADLOCK
Database kills one transaction after timeout
```

### Fix — Sort by productId Before Locking
```java
List<OrderItemRequest> sortedItems = request.items().stream()
        .sorted(Comparator.comparing(OrderItemRequest::productId))
        .toList();
```

Both threads now lock in the same order:
```
Thread 1: lock product 1 → lock product 2 → lock product 3
Thread 2: wait for product 1 → (Thread 1 finishes) → lock product 1 → lock product 2

No circular dependency. No deadlock.
```

### Interview Answer
*"We sort order items by productId before acquiring locks. If two transactions always acquire locks in ascending ID order, they can never deadlock — no circular dependency is possible. Thread 2 waits for Thread 1 to release product 1 before proceeding."*

---

## The Four-Phase Order Pattern

### Why Four Phases
Old approach: load data + lock + calculate + save all in one loop.
Problem: locks held while loading data and calculating = long lock duration.

### Phase Design
```
Phase 1 — Load all data (ZERO locks held)
  → sort items by productId
  → bulk load products + variants (2 queries, not N)
  → validate all exist — fail fast before locking anything

Phase 2 — Deduct stock (locks held BRIEFLY per item)
  → locks acquired in sorted order — no deadlock
  → each deductStock: lock → deduct → release

Phase 3 — Calculate prices (ZERO DB calls)
  → pure Java math using productMap from Phase 1
  → no DB calls = no locks needed

Phase 4 — Save order (single write)
  → one INSERT for order
  → cascade saves all items
```

### Lock Duration Comparison
```
Old approach: 10 items × 200ms = 2000ms locks held
New approach: lock held only during Phase 2 (milliseconds per item)
```

---

## Atomicity — Rollback Behavior

### Default Rollback
Spring rolls back on unchecked exceptions (RuntimeException and subclasses):
```java
throw new ResourceNotFoundException(...)    // RuntimeException → rollback ✅
throw new InsufficientStockException(...)   // RuntimeException → rollback ✅
throw new IOException(...)                  // Checked exception → NO rollback ❌
```

### Force Rollback on Checked Exception
```java
@Transactional(rollbackFor = Exception.class)
public void someMethod() throws IOException {
    // IOException now also triggers rollback
}
```

### cancelOrder — Both Operations Atomic
```java
@Transactional
public OrderResponse cancelOrder(Long id) {
    // Operation 1: restore stock
    order.getItems().forEach(item -> stockService.restock(...));

    // Operation 2: update status
    order.setStatus(OrderStatus.CANCELLED);

    // Both in same @Transactional
    // If restock fails → status NOT updated → data consistent
    // If status update fails → restock rolled back → data consistent
}
```

---

## Transaction Best Practices

### Keep Transactions Short
```
Long transaction = locks held longer = poor concurrency
Short transaction = locks released quickly = good concurrency

Rule: do all validation and preparation OUTSIDE the lock
      acquire lock → do minimum work → release lock
```

### Don't Call External Services Inside Transaction
```java
// Wrong — HTTP call inside transaction
@Transactional
public void processOrder() {
    saveOrder();
    razorpayService.createPayment();  // if this hangs → transaction hangs → locks held
}

// Right — separate concerns
@Transactional
public void saveOrder() { ... }  // commits quickly

public void initiatePayment() {
    saveOrder();
    razorpayService.createPayment();  // outside transaction
}
```

### Never Catch and Swallow Exceptions in @Transactional
```java
// Wrong — swallowing exception prevents rollback
@Transactional
public void updateStock() {
    try {
        stockRepository.save(stock);
    } catch (Exception e) {
        log.error("Error", e);  // transaction thinks everything is fine!
        // no rollback triggered — data may be partially updated
    }
}

// Right — let exception propagate
@Transactional
public void updateStock() {
    stockRepository.save(stock);  // exception propagates → rollback triggered
}
```
