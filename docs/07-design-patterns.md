# 07 — Design Patterns

## What Is a Design Pattern

A reusable solution to a commonly occurring problem in software design.
Not a finished design — a template for how to solve a problem.

The goal: write code that is easy to extend, maintain, and understand.

---

## 1. Repository Pattern

### What It Is
Abstracts data access behind an interface. Service never writes SQL or JPQL directly — it always goes through a repository.

### Where in JewelCart
```java
// Every module has a repository
VendorRepository   extends JpaRepository<Vendor, Long>
ProductRepository  extends JpaRepository<Product, Long>
OrderRepository    extends JpaRepository<Order, Long>
PaymentRepository  extends JpaRepository<Payment, Long>
```

### Why
```
Without repository pattern:
  Service writes SQL directly → tightly coupled to DB
  Change from PostgreSQL to MySQL → rewrite every service

With repository pattern:
  Service calls repository.findById() → doesn't care about DB
  Change database → only repository implementation changes
  Service code untouched
```

### Interview Answer
*"Repository pattern abstracts data access — services call repository methods like `findByVendorId()` without knowing the underlying SQL. This decouples business logic from persistence technology. If we switch databases, only the repository layer changes — all service code remains untouched."*

---

## 2. DTO Pattern (Data Transfer Object)

### What It Is
Separate objects for transferring data across layers. Entities never leave the service layer — DTOs carry data to/from controllers.

### Where in JewelCart
```java
// Request DTOs (data coming IN)
CreateVendorRequest, PlaceOrderRequest, InitiatePaymentRequest

// Response DTOs (data going OUT)
VendorResponse, OrderResponse, PaymentResponse

// Summary DTOs (lightweight list views)
VendorSummaryDTO, OrderSummaryDTO, ProductSummaryDTO
```

### Why
```
Without DTO:
  Return User entity → passwordHash exposed in API response
  Change DB schema → API response changes automatically → frontend breaks

With DTO:
  API contract is separate from DB schema
  Add/remove columns in DB → DTO unchanged → frontend unaffected
  Security — only expose what frontend needs
```

### Two Response DTOs — Summary vs Full
```java
// List view — lightweight
ProductSummaryDTO → id, name, price, primaryImage

// Detail view — full
ProductResponse   → id, name, description, all images, variants, vendor, category, gstRate
```

Product list: 100 products × summary = fast.
Product detail: 1 product × full = complete.

### Interview Answer
*"DTO pattern decouples the API contract from the database schema. The User entity has a passwordHash field — returning the entity directly would expose it. DTOs let us control exactly what data leaves the service layer. We also have separate summary and full DTOs — the summary is lightweight for list views, the full response has all details for single entity views."*

---

## 3. Builder Pattern

### What It Is
Constructs complex objects step by step. Avoids telescoping constructors (constructors with many parameters).

### Where in JewelCart
```java
// Lombok @Builder on every entity and DTO
Order order = Order.builder()
        .orderNumber(generateOrderNumber())
        .user(currentUser)
        .status(OrderStatus.PENDING)
        .subtotal(subtotal)
        .gstAmount(totalGst)
        .totalAmount(subtotal.add(totalGst))
        .shippingAddress(request.shippingAddress())
        .notes(request.notes())
        .build();
```

### Why
```
Without builder (telescoping constructor):
new Order(orderNumber, user, status, subtotal, gstAmount,
          totalAmount, shippingAddress, notes, items);
→ which argument is which? Impossible to read.
→ easy to swap arguments by mistake.

With builder:
Order.builder().status(PENDING).subtotal(2598).build()
→ self-documenting, readable, impossible to confuse fields
→ optional fields can be skipped cleanly
```

### @Builder.Default — Critical
```java
// Without @Builder.Default — builder ignores initializer
private Boolean isActive = true;
// Product.builder().build() → isActive = null → NullPointerException!

// With @Builder.Default — preserves default
@Builder.Default
private Boolean isActive = true;
// Product.builder().build() → isActive = true ✅
```

### Interview Answer
*"Builder pattern constructs complex objects step by step. We use Lombok's @Builder on all entities. It's especially important for entities with many optional fields — you only set what you need, and the code reads like documentation. The @Builder.Default annotation is critical — without it, Lombok's builder ignores field initializers and sets null."*

---

## 4. Factory Method Pattern

### What It Is
Static factory methods that create objects — hides construction complexity.

### Where in JewelCart
```java
// ApiResponse — static factory methods
ApiResponse.success("Created", response)
ApiResponse.error("Not found")
ApiResponse.validationError("Failed", errors)
```

### Why
```
Without factory:
new ApiResponse(true, "Created", response, null, Instant.now())
→ which boolean is success? What's the null?

With factory:
ApiResponse.success("Created", response)
→ intent is clear, construction is hidden
→ one place to change if ApiResponse structure changes
```

---

## 5. State Machine Pattern

### What It Is
An object has defined states and strict rules for transitioning between them.

### Where in JewelCart
```java
// OrderService.validateTransition()
PENDING    → CONFIRMED, CANCELLED
CONFIRMED  → PROCESSING, CANCELLED
PROCESSING → SHIPPED
SHIPPED    → DELIVERED
DELIVERED, CANCELLED, REFUNDED → terminal (no further transitions)
```

### Why
```
Without state machine:
order.setStatus(DELIVERED);  // even if never shipped → corrupt data
→ anyone can set any status → business rules violated

With state machine:
validateTransition(PENDING, DELIVERED) → IllegalStateException
→ impossible to skip steps
→ impossible to reverse terminal states
→ business rules enforced at application level
```

### Implementation
```java
private void validateTransition(OrderStatus current, OrderStatus next) {
    boolean valid = switch (current) {
        case PENDING    -> next == CONFIRMED || next == CANCELLED;
        case CONFIRMED  -> next == PROCESSING || next == CANCELLED;
        case PROCESSING -> next == SHIPPED;
        case SHIPPED    -> next == DELIVERED;
        case DELIVERED, CANCELLED, REFUNDED -> false;  // terminal
    };
    if (!valid) throw new IllegalStateException(
        "Invalid transition: " + current + " → " + next);
}
```

### Interview Answer
*"State machine pattern enforces valid order status transitions. Each status has defined valid next states — you can't deliver an unconfirmed order. The validateTransition() method throws IllegalStateException for invalid transitions, which our GlobalExceptionHandler converts to 409 CONFLICT. Terminal states like DELIVERED and CANCELLED have no valid next state — the order lifecycle is complete."*

---

## 6. Strategy Pattern

### What It Is
Defines a family of algorithms, encapsulates each one, and makes them interchangeable. The algorithm varies independently from the clients that use it.

### Where in JewelCart
```java
// PaymentGateway interface — the strategy
public interface PaymentGateway {
    GatewayOrderResponse createOrder(...);
    GatewayVerifyResponse verifyPayment(...);
    GatewayRefundResponse refundPayment(...);
    boolean verifyWebhookSignature(...);
}

// Concrete strategies
@Component("razorpay")
public class RazorpayGateway implements PaymentGateway { }

// Future
@Component("payu")
public class PayUGateway implements PaymentGateway { }
```

### How Switching Works
```yaml
# application.yaml — change one line to switch gateway
payment:
  gateway: razorpay   # change to "payu" to switch
```

```java
// PaymentService selects strategy from yaml config
public PaymentService(
        @Value("${payment.gateway}") String gatewayName,
        Map<String, PaymentGateway> gateways) {
    this.paymentGateway = gateways.get(gatewayName);
}
```

### Also — PasswordEncoder Strategy
```java
// Strategy for password encoding
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
    // Swap to Argon2: return new Argon2PasswordEncoder(...)
    // Zero changes to AuthService
}
```

### Interview Answer
*"Strategy pattern lets us swap payment gateways without changing business logic. PaymentGateway interface defines the contract — createOrder, verifyPayment, refundPayment. RazorpayGateway implements it. To switch to PayU, we add PayUGateway implementing the same interface and change one yaml property. PaymentService never changes — Open/Closed Principle: open for extension, closed for modification."*

---

## 7. Template Method Pattern

### What It Is
Defines the skeleton of an algorithm in a base class. Subclasses fill in specific steps without changing the overall structure.

### Where in JewelCart
```java
// BaseEntity — defines the template for all entities
@MappedSuperclass
public abstract class BaseEntity {
    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}

// All entities follow this template
public class Vendor extends BaseEntity { }
public class Product extends BaseEntity { }
public class Order extends BaseEntity { }
```

### Why
```
Without BaseEntity:
  Add createdAt to every entity separately
  Change audit field name → update 10 entity files

With BaseEntity:
  One change in BaseEntity → all entities updated
  DRY principle — Don't Repeat Yourself
```

---

## 8. Chain of Responsibility Pattern

### What It Is
Passes a request through a chain of handlers. Each handler decides to process or pass to the next.

### Where in JewelCart
```
HTTP Request
     ↓
JwtAuthFilter          → validates JWT, sets SecurityContext
     ↓
UsernamePasswordFilter → handles form login (skipped for REST)
     ↓
AuthorizationFilter    → checks @PreAuthorize roles
     ↓
Controller             → handles business logic
```

### Why
```
Each filter has one responsibility
Adding a new concern (e.g. rate limiting) = add new filter
Existing filters unchanged
Order matters — JWT must validate before authorization checks
```

---

## 9. Observer Pattern

### What It Is
Object (subject) maintains a list of observers and notifies them of state changes automatically.

### Where in JewelCart
```java
// JPA Auditing — Spring observes entity lifecycle events
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    private Instant createdAt;
}

// AuditingEntityListener observes:
// → PRE_PERSIST: sets createdAt, updatedAt, createdBy, updatedBy
// → PRE_UPDATE: sets updatedAt, updatedBy only
```

You don't call any method — Spring fires events automatically on entity save/update.

---

## 10. Proxy Pattern

### What It Is
A surrogate that controls access to another object. Adds behavior before/after method calls.

### Where in JewelCart — Three Places

**@Transactional Proxy:**
```java
@Transactional
public OrderResponse placeOrder(...) { }

// Spring generates a proxy class:
// proxy.placeOrder() {
//     beginTransaction();
//     try { original.placeOrder(); commit(); }
//     catch { rollback(); }
// }
```

**@PreAuthorize Proxy:**
```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteVendor(Long id) { }

// Proxy checks role before calling actual method
// If no role → throws AccessDeniedException → 403
```

**Hibernate LAZY Loading Proxy:**
```java
@ManyToOne(fetch = FetchType.LAZY)
private Vendor vendor;

// product.getVendor() returns a proxy object
// When you access vendor.getName() → proxy fires SQL query
// If session is closed → LazyInitializationException
```

---

## Pattern Summary

| Pattern | Where Used | Problem It Solves |
|---|---|---|
| Repository | All modules | Decouples DB from business logic |
| DTO | All modules | Decouples API contract from DB schema |
| Builder | All entities | Readable object construction |
| Factory Method | ApiResponse | Consistent response creation |
| State Machine | OrderService | Valid order status transitions |
| Strategy | PaymentGateway | Swap gateways without code changes |
| Template Method | BaseEntity | Shared audit fields across entities |
| Chain of Responsibility | Security filters | Modular request processing |
| Observer | JPA Auditing | Automatic audit field population |
| Proxy | @Transactional, @PreAuthorize, Lazy loading | Add behavior without changing code |

---

## Coming in Phase 5

```
Circuit Breaker  → Resilience4j
  If payment service is down → fail fast, don't wait 30 seconds
  After 5 failures → open circuit → return fallback response

Saga Pattern     → distributed transactions across microservices
  Order Service places order → publishes event
  Payment Service processes payment → publishes event
  Stock Service deducts stock → publishes event
  If any step fails → compensating events restore state

Event Sourcing   → already partially built (payment_events table)
  Store every state change as an event
  Reconstruct any past state by replaying events
  Full audit trail

CQRS             → separate read and write models
  Write: OrderService → saves to PostgreSQL
  Read:  OrderQueryService → reads from Elasticsearch (optimized for search)
```
