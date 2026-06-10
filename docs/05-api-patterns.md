# 05 — API Patterns

## REST Conventions

### HTTP Methods
```
GET     → read data, no side effects, safe to call multiple times
POST    → create new resource → returns 201 Created
PUT     → replace entire resource → returns 200 OK
PATCH   → partial update → returns 200 OK
DELETE  → remove resource → returns 204 No Content
```

### Status Codes Used in JewelCart
```
200 OK           → successful GET, PUT, PATCH
201 Created      → successful POST (new resource created)
204 No Content   → successful DELETE or deactivate (no body returned)
400 Bad Request  → validation failure (@Valid)
401 Unauthorized → missing or invalid token
403 Forbidden    → valid token but wrong role
404 Not Found    → resource doesn't exist (ResourceNotFoundException)
409 Conflict     → duplicate resource or invalid state transition
500 Server Error → unexpected error (never expose details to frontend)
```

### URL Design
```
/api/v1/vendors              → collection
/api/v1/vendors/1            → single resource
/api/v1/vendors/1/deactivate → action on resource (PATCH)

/api/v1/products/search      → search endpoint
/api/v1/products/featured    → filtered subset
/api/v1/products/vendor/1    → products by vendor
/api/v1/stock/low-stock      → filtered subset
```

### Why `/deactivate` in URL
```
PATCH /api/v1/vendors/1          → ambiguous (what are we changing?)
PATCH /api/v1/vendors/1/deactivate → clear intent

REST best practice for state transitions:
use sub-resource that describes the action
```

---

## API Versioning

### URL Versioning (What We Use)
```
/api/v1/vendors   ← current
/api/v2/vendors   ← future breaking change
```

### Why in application.yaml Not Controllers
```yaml
# application.yaml
server:
  servlet:
    context-path: /api  # applied to ALL endpoints automatically
```

```java
// Controller — just the version
@RequestMapping("/v1/vendors")
// not "/api/v1/vendors" — /api handled at app level
```

Benefit: change the prefix once in yaml — not in every controller.

### When to Create v2
```
Breaking changes require new version:
  → removing a field from response
  → changing field type (String → Integer)
  → changing URL structure
  → changing required/optional fields

Non-breaking changes don't need new version:
  → adding new optional field to response
  → adding new endpoint
  → adding new optional query parameter
```

### Interview Answer
*"We version APIs with a /v1/ URL prefix. This allows introducing breaking changes in /v2/ while existing clients continue using /v1/. The /api context path is handled at the application level in yaml, not repeated in every controller. We create a new version only for breaking changes — adding fields or endpoints doesn't require versioning."*

---

## ApiResponse Envelope

### Why Consistent Response Format
```json
// Without envelope — three different shapes
// Success:
{ "id": 1, "name": "Arihant" }

// Error:
{ "timestamp": "...", "status": 404, "error": "Not Found" }

// Validation:
{ "errors": [{ "field": "name", "message": "required" }] }

// Frontend must handle three different structures → complex, error-prone
```

```json
// With envelope — same shape always
// Success:
{ "success": true, "message": "...", "data": {...} }

// Error:
{ "success": false, "message": "..." }

// Validation:
{ "success": false, "message": "Validation failed", "errors": {...} }

// Frontend checks "success" field — one pattern for everything
```

### ApiResponse Implementation
```java
@JsonInclude(JsonInclude.Include.NON_NULL)  // null fields hidden from JSON
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {
    // static factory methods — clean creation
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> validationError(String message,
            Map<String, String> errors) {
        return new ApiResponse<>(false, message, null, errors, Instant.now());
    }
}
```

### @JsonInclude — Why
```java
// Without @JsonInclude — null fields included in JSON
{ "success": true, "data": {...}, "errors": null, "timestamp": "..." }
// "errors": null is noise — frontend must handle null everywhere

// With @JsonInclude(NON_NULL) — null fields hidden
{ "success": true, "data": {...}, "timestamp": "..." }
// errors field completely absent — cleaner, frontend only sees relevant fields
```

### Static Import — Cleaner Code
```java
import static com.jewelcart.common.dto.ApiResponse.success;
import static com.jewelcart.common.dto.ApiResponse.error;

// Without static import
return ResponseEntity.ok(ApiResponse.success("Created", response));

// With static import
return ResponseEntity.ok(success("Created", response));
```

---

## Global Exception Handler

### Why @RestControllerAdvice
```java
// Without GlobalExceptionHandler — every method needs try-catch
@PostMapping
public ResponseEntity<...> createVendor(...) {
    try {
        ...
    } catch (DuplicateResourceException ex) {
        return ResponseEntity.status(409).body(error(ex.getMessage()));
    } catch (Exception ex) {
        return ResponseEntity.status(500).body(error("Unexpected error"));
    }
}
// 5 methods × same try-catch = 15+ catch blocks — pure repetition
```

```java
// With GlobalExceptionHandler — service just throws
// Handler catches everything in one place
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(...) {
        return ResponseEntity.status(404).body(error(ex.getMessage()));
    }
    // ... one handler per exception type
}
```

### Exception → Status Code Mapping
```
ResourceNotFoundException      → 404 Not Found
DuplicateResourceException     → 409 Conflict
InsufficientStockException     → 409 Conflict
IllegalStateException          → 409 Conflict (invalid order transition)
MethodArgumentNotValidException → 400 Bad Request (validation)
AuthenticationException        → 401 Unauthorized
AccessDeniedException          → 403 Forbidden
Exception (catch-all)          → 500 Internal Server Error
```

### Validation Error Handler
```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiResponse<Void>> handleValidation(
        MethodArgumentNotValidException ex) {

    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach(err -> {
        String field = ((FieldError) err).getField();
        String message = err.getDefaultMessage();  // from @NotBlank(message="...")
        errors.put(field, message);
    });

    return ResponseEntity.status(400)
            .body(validationError("Validation failed", errors));
}
```

Response:
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "name": "Vendor name is required",
    "email": "Invalid email format"
  }
}
```

### Never Expose Internal Errors
```java
// Wrong — exposes stack trace, internal details
return ResponseEntity.status(500).body(error(ex.getMessage()));

// Right — generic message, log internally
log.error("Unexpected error", ex);  // log for debugging
return ResponseEntity.status(500).body(error("An unexpected error occurred"));
```

---

## Pagination

### Why Pagination
```
No pagination: GET /products → loads ALL 10,000 products into memory → OOM
With pagination: GET /products?page=0&size=10 → loads 10 → fast
```

### Page Response Structure
```json
{
  "content": [...],        // the actual data
  "totalElements": 1000,   // total count
  "totalPages": 100,       // total pages
  "pageNumber": 0,         // current page (0-indexed)
  "pageSize": 10,          // items per page
  "first": true,           // is this first page?
  "last": false            // is this last page?
}
```

### Implementation
```java
// Controller — accept page/size params
@GetMapping
public ResponseEntity<ApiResponse<Page<VendorResponse>>> getAllVendors(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {

    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(success("Vendors retrieved",
            vendorService.getAllVendors(pageable)));
}

// Repository — accepts Pageable, returns Page
Page<Vendor> findByIsActiveTrue(Pageable pageable);

// Service — maps Page<Entity> to Page<DTO>
return vendorRepository.findByIsActiveTrue(pageable)
        .map(this::toResponse);  // map() works on Page — preserves metadata
```

### @Min/@Max on Pagination Params
```java
@Min(value = 0, message = "Page must be 0 or greater")  int page
@Min(value = 1, message = "Size must be at least 1")
@Max(value = 100, message = "Size cannot exceed 100")   int size
```

Prevents: `GET /products?size=999999` → loading 1M records.

### When NOT to Paginate
```
Category tree → need full dataset to build tree → no pagination
Recent products (top 10) → use PageRequest.of(0, 10) directly → no pagination param
```

---

## Validation

### Bean Validation Annotations
```java
public record CreateVendorRequest(
    @NotBlank(message = "Name is required")     // not null, not empty, not whitespace
    String name,

    @NotNull(message = "Price is required")      // not null (use for non-String)
    BigDecimal price,

    @Email(message = "Invalid email format")     // valid email pattern
    String email,

    @Size(min = 8, message = "Min 8 characters") // length constraint
    String password,

    @Positive(message = "Must be greater than 0") // > 0
    BigDecimal price,

    @Min(value = 1, message = "Min 1")           // >= 1
    Integer quantity
) {}
```

### @NotNull vs @NotBlank
```
@NotNull  → rejects null only — "" (empty string) passes through
@NotBlank → rejects null, empty "", and whitespace "   "
Use @NotBlank for String fields always
Use @NotNull for non-String fields (BigDecimal, Integer, Long, Enum)
```

### Activating Validation
```java
// On controller method — validates request body
public ResponseEntity<...> createVendor(@Valid @RequestBody CreateVendorRequest request)
//                                       ↑ required

// On class — enables @Min/@Max on method params
@Validated
public class VendorController {
```

### Validating Nested Objects
```java
public record PlaceOrderRequest(
    @NotEmpty(message = "Order must have at least one item")
    @Valid  // ← validates each OrderItemRequest inside the list
    List<OrderItemRequest> items,
    ...
)
```

Without `@Valid` on the list — inner validations on `OrderItemRequest` are never checked.

---

## DTO Pattern

### Why DTOs (Not Returning Entities)
```
Problem 1 — Security:
  User entity has passwordHash field
  Return entity directly → passwordHash exposed in API response

Problem 2 — Coupling:
  Change DB schema → API response changes automatically
  Frontend breaks without warning

Problem 3 — Flexibility:
  API needs vendor name in product response
  Entity only has vendor_id FK
  DTO can combine data from multiple entities
```

### Records for DTOs
```java
// Immutable — perfect for data transfer
// No setters — can't be accidentally modified
// Compact — no boilerplate
public record VendorResponse(
    Long id,
    String name,
    String email,
    Boolean isActive,
    Instant createdAt
) {}
```

### Two Response DTOs (Summary vs Full)
```java
// Lightweight — for list views
public record ProductSummaryDTO(
    Long id, String name, BigDecimal sellingPrice,
    String primaryImageUrl, String vendorName
    // no description, no all-images, no full details
)

// Full — for detail view
public record ProductResponse(
    Long id, String name, String description,
    List<String> imageUrls,  // all images
    String vendorName, String categoryName,
    BigDecimal basePrice, BigDecimal sellingPrice, BigDecimal gstRate,
    // ... all fields
)
```

Product list: 100 products × summary = fast
Product detail: 1 product × full = complete

### Manual Mapping vs MapStruct
```java
// Phase 1 — manual mapping
private VendorResponse toResponse(Vendor vendor) {
    return new VendorResponse(
        vendor.getId(), vendor.getName(), vendor.getEmail(), ...
    );
}
// Verbose but transparent — you see exactly what maps to what

// Phase 2 — MapStruct (code generation)
@Mapper(componentModel = "spring")
public interface VendorMapper {
    VendorResponse toResponse(Vendor vendor);
}
// Zero boilerplate — MapStruct generates implementation at compile time
```

---

## Controller Pattern

### Standard Controller Structure
```java
@RestController                    // = @Controller + @ResponseBody
@RequiredArgsConstructor           // constructor injection
@RequestMapping("/v1/vendors")     // base URL (no /api — handled by context-path)
@Validated                         // enables @Min/@Max on method params
public class VendorController {

    private final VendorService vendorService;

    @PostMapping                   // POST /api/v1/vendors → 201
    public ResponseEntity<ApiResponse<VendorResponse>> createVendor(
            @Valid @RequestBody CreateVendorRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(success("Vendor created successfully",
                        vendorService.createVendor(request)));
    }

    @GetMapping("/{id}")           // GET /api/v1/vendors/1 → 200
    public ResponseEntity<ApiResponse<VendorResponse>> getVendorById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                success("Vendor retrieved successfully",
                        vendorService.getVendorById(id)));
    }

    @PatchMapping("/{id}/deactivate")  // PATCH /api/v1/vendors/1/deactivate → 204
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateVendor(@PathVariable Long id) {
        vendorService.deactivateVendor(id);
        return ResponseEntity.noContent().build();  // 204 — no body
    }
}
```

### ResponseEntity vs @ResponseStatus
```java
// Wrong — using both for same thing
@ResponseStatus(HttpStatus.CREATED)   // ← redundant
public ResponseEntity<...> createVendor(...) {
    return ResponseEntity.status(HttpStatus.CREATED).body(...);  // ← this controls it
}

// Right — pick one, ResponseEntity is more flexible
public ResponseEntity<...> createVendor(...) {
    return ResponseEntity.status(HttpStatus.CREATED).body(...);
}
```

### 204 No Content — No Body
```java
// Deactivate/delete — no body needed
return ResponseEntity.noContent().build();  // 204

// Wrong — body on 204 is ignored by HTTP spec anyway
return ResponseEntity.status(204).body(success("Deactivated"));
```

---

## Timestamps — Instant vs LocalDateTime

### Why Instant Not LocalDateTime
```
LocalDateTime → no timezone info → "2024-01-01 10:00:00" (which timezone?)
  Problem: server in UTC, user in IST → 5:30 hours off

Instant → always UTC → "2024-01-01T10:00:00Z"
  Safe: store in UTC, convert to local time in frontend
```

### application.yaml Setting
```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
# Without this → Instant serialized as epoch millis: 1716458752000 (unreadable)
# With this → Instant serialized as ISO-8601: "2024-01-01T10:00:00Z" (readable)
```

### Hibernate UTC Setting
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
# All timestamps stored in UTC regardless of server timezone
```
