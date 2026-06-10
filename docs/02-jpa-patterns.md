# 02 — JPA Patterns

## Entity Basics

### Required Annotations
```java
@Entity                    // marks this as a JPA managed class
@Table(name = "products")  // maps to specific table name
                           // without this: Hibernate guesses table name
                           // "users" is reserved in PostgreSQL — always specify
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // use PostgreSQL BIGSERIAL
    private Long id;
}
```

### GenerationType.IDENTITY vs AUTO
```
AUTO   → Hibernate decides → creates separate hibernate_sequence table in PostgreSQL
         messy, unnecessary extra table

IDENTITY → uses database's native auto-increment (BIGSERIAL in PostgreSQL)
           maps directly to your Flyway migration
           ALWAYS use IDENTITY with PostgreSQL
```

---

## Lombok on Entities

### Correct Annotations for JPA Entity
```java
@Getter
@Setter
@NoArgsConstructor    // JPA requires empty constructor — loads entities by reflection
@AllArgsConstructor   // for manual object creation
@Builder              // enables Product.builder().name("x").build()
@EqualsAndHashCode(callSuper = true)  // include BaseEntity fields
@Entity
@Table(name = "products")
public class Product extends BaseEntity {
```

### Why NOT @Data on Entities
```java
// Wrong
@Data  // generates equals/hashCode based on ALL fields
       // causes issues with JPA lazy loading proxies
       // toString() on lazy fields triggers extra DB queries

// Right
@Getter
@Setter
// handle equals/hashCode separately
```

### @Builder.Default — Critical
```java
// Wrong — @Builder ignores field initializer
private Boolean isActive = true;
// Builder: Product.builder().build() → isActive = null → NullPointerException

// Right — @Builder.Default preserves the default value
@Builder.Default
private Boolean isActive = true;
// Builder: Product.builder().build() → isActive = true ✅

// Same for collections
@Builder.Default
private List<OrderItem> items = new ArrayList<>();
// Without @Builder.Default → items = null → NullPointerException on items.add()
```

---

## Relationships

### The Two Sides Rule
Every relationship has an OWNER and an INVERSE side.

```
OWNER side   → has @JoinColumn → has FK column in DB
INVERSE side → has mappedBy   → no column in DB, just Java navigation
```

```java
// ProductImage — OWNER (has product_id column in DB)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "product_id", nullable = false)
private Product product;

// Product — INVERSE (no column in DB)
@OneToMany(mappedBy = "product")  // "product" = field name in ProductImage
private List<ProductImage> images;
```

### @Column vs @JoinColumn
```java
// @Column — for primitive types (String, Long, BigDecimal, Boolean)
@Column(name = "name", nullable = false)
private String name;

// @JoinColumn — for entity relationships (foreign keys)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "vendor_id", nullable = false)
private Vendor vendor;
```

### Relationship Types
```
@ManyToOne  → many products belong to one vendor
              FK column on the "many" side
              default fetch: EAGER (dangerous — always override to LAZY)

@OneToMany  → one vendor has many products
              no FK column — uses mappedBy
              default fetch: LAZY (safe default)

@OneToOne   → one user has one profile
              default fetch: EAGER (dangerous — always override to LAZY)

@ManyToMany → one student takes many courses, one course has many students
              uses join table
              default fetch: LAZY (safe default)
```

### FetchType — Always LAZY
```java
// WRONG — EAGER default
@ManyToOne
private Vendor vendor;
// Loads vendor immediately every time you load a product
// 100 products = 101 queries (N+1!)

// RIGHT — always explicit LAZY
@ManyToOne(fetch = FetchType.LAZY)
private Vendor vendor;
// Vendor loaded only when you call product.getVendor()
// You control exactly when the extra query fires
```

### CascadeType
```java
// CascadeType.ALL — save/delete parent cascades to children
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
private List<OrderItem> items;

// Save order → items saved automatically (no need to save items separately)
// Delete order → items deleted automatically
```

### orphanRemoval
```java
@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderItem> items;

// Without orphanRemoval:
order.getItems().remove(item1);  // removed from list but STILL IN DATABASE

// With orphanRemoval:
order.getItems().remove(item1);  // removed from list AND deleted from database
```

Use orphanRemoval when child has no meaning without parent (OrderItem without Order = useless).

### Setting Both Sides of Relationship
```java
// For saving to DB — only owner side required
item.setOrder(order);  // writes order_id FK to DB

// For Java object consistency — set inverse side too
order.getItems().add(item);  // keeps Java object in sync

// If you skip inverse side:
order = orderRepository.save(order);
order.getItems();  // returns EMPTY LIST even though items exist in DB
                   // because you never added them to the Java collection

// ALWAYS set both sides before saving
```

### Self-Referential Relationship (Categories)
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")  // nullable — root categories have no parent
private Category parent;         // same type as the class!

@OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
@Builder.Default
private List<Category> children = new ArrayList<>();
```

---

## JPQL

### JPQL vs SQL
```
SQL   → talks to tables and columns
JPQL  → talks to Java entities and fields

SQL:   SELECT * FROM products WHERE vendor_id = 1
JPQL:  SELECT p FROM Product p WHERE p.vendor.id = 1
                     ↑ entity name          ↑ field navigation
```

### Basic JPQL Structure
```java
@Query("SELECT p FROM Product p WHERE p.isActive = true")
//             ↑ entity alias     ↑ entity field (not column name)
```

### LIKE Query — Case Insensitive
```java
@Query("SELECT p FROM Product p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))")
Page<Product> searchByName(@Param("name") String name, Pageable pageable);

// LOWER() on both sides = case insensitive
// "NECKLACE" finds "Necklace", "necklace", "NECKLACE"
// Trade-off: LOWER() prevents index usage on that column
// For search correctness > raw speed
```

### JOIN FETCH
```java
// Load product WITH vendor in one query — safe for single entity
@Query("SELECT p FROM Product p JOIN FETCH p.vendor WHERE p.id = :id")
Optional<Product> findByIdWithVendor(@Param("id") Long id);

// JOIN FETCH with LAZY relationship = explicit eager load for this query only
// vendor.name accessible without extra query after this
```

### Pagination with countQuery
```java
// JOIN FETCH + Pageable = DANGEROUS
// Hibernate loads ALL rows into memory, paginates in Java
// 100,000 products → OutOfMemoryError

// RIGHT — separate value and countQuery
@Query(
    value = "SELECT p FROM Product p WHERE p.vendor.id = :vendorId",
    countQuery = "SELECT COUNT(p) FROM Product p WHERE p.vendor.id = :vendorId"
)
Page<Product> findByVendorId(@Param("vendorId") Long vendorId, Pageable pageable);
// Hibernate generates clean LIMIT/OFFSET at SQL level
```

### Aggregation — Projection Interface (not Object[])
```java
// Wrong — fragile array index access
@Query("SELECT p.vendor.id, COUNT(p) FROM Product p GROUP BY p.vendor.id")
List<Object[]> countByVendor();
// Usage: (Long) row[0] — breaks if query column order changes

// Right — named projection interface
public interface VendorProductCount {
    Long getVendorId();
    String getVendorName();
    Long getProductCount();
}

@Query("SELECT p.vendor.id as vendorId, p.vendor.name as vendorName, " +
       "COUNT(p) as productCount FROM Product p GROUP BY p.vendor.id, p.vendor.name")
List<VendorProductCount> countActiveProductsPerVendor();
// Usage: result.getVendorId() — safe, named, type checked
```

### JPQL Dot Navigation
```java
// Navigate relationships with dot notation
WHERE p.vendor.id = :vendorId       // Product → Vendor → id
WHERE p.vendor.brandName LIKE ...   // Product → Vendor → brandName
WHERE p.category.name = :name       // Product → Category → name

// Only works with full relationship mapping (@ManyToOne)
// Does NOT work with just Long vendorId field
```

### Inherited Fields in JPQL
```java
// createdAt is in BaseEntity, not Product
// JPQL works on entity model — inheritance included
@Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
// p.createdAt works because Product extends BaseEntity
```

### Sort via Pageable (not hardcoded ORDER BY)
```java
// Flexible — caller decides sort
Page<Product> findByVendorId(Long vendorId, Pageable pageable);
// Call with: PageRequest.of(0, 10, Sort.by("createdAt").descending())

// When order is always fixed (e.g. recent products)
@Query("SELECT p FROM Product p WHERE p.isActive = true ORDER BY p.createdAt DESC")
List<Product> findRecentActiveProducts(Pageable pageable);
// Call with: PageRequest.of(0, 10) — sort already in query
```

---

## N+1 Problem

### What It Is
```java
// Loading 10 vendors
List<Product> products = productRepository.findAll();  // Query 1

// Accessing vendor on each product
for (Product p : products) {
    System.out.println(p.getVendor().getName());  // Query 2,3,4...11
}
// Total: 11 queries for 10 products = N+1
```

### Fix 1 — JOIN FETCH (single entity or non-paginated list)
```java
@Query("SELECT p FROM Product p JOIN FETCH p.vendor WHERE p.id = :id")
Optional<Product> findByIdWithVendor(@Param("id") Long id);
```

### Fix 2 — No JOIN FETCH on paginated queries
```java
// Don't join fetch — load vendor lazily only when needed
// If response DTO only needs product fields → vendor never loaded
@Query("SELECT p FROM Product p WHERE p.vendor.id = :vendorId")
Page<Product> findByVendorId(Long vendorId, Pageable pageable);
```

### Fix 3 — Bulk loading (batch operations)
```java
// Instead of loading one by one in loop (N queries)
for (Long id : productIds) {
    productRepository.findById(id);  // N queries
}

// Load all at once (1 query)
Map<Long, Product> productMap = productRepository.findAllById(productIds)
        .stream()
        .collect(Collectors.toMap(Product::getId, p -> p));
```

---

## MultipleBagFetchException

### What Causes It
```java
// Fetching TWO collections with JOIN FETCH simultaneously
@Query("SELECT o FROM Order o JOIN FETCH o.items JOIN FETCH o.items.product")
// items (3) × product images (2) = 6 rows — Cartesian product
// Hibernate refuses: MultipleBagFetchException
```

### Fix — @EntityGraph
```java
@EntityGraph(attributePaths = {"items", "items.product", "items.product.images"})
@Query("SELECT o FROM Order o WHERE o.id = :id")
Optional<Order> findByIdWithItems(@Param("id") Long id);

// Hibernate generates separate queries:
// Query 1: SELECT * FROM orders WHERE id = ?
// Query 2: SELECT * FROM order_items WHERE order_id IN (?)
// Query 3: SELECT * FROM products WHERE id IN (1,2,3)
// Query 4: SELECT * FROM product_images WHERE product_id IN (1,2,3)
// No Cartesian product. No duplicates.
```

---

## Dirty Checking

### What It Is
```java
@Transactional
public VendorResponse updateVendor(Long id, UpdateVendorRequest request) {
    Vendor vendor = vendorRepository.findById(id).orElseThrow(...);

    vendor.setName(request.name());  // change in memory

    // NO vendorRepository.save() call!
    return toResponse(vendor);
}
// Transaction commits → Hibernate compares current state vs snapshot
// Detects name changed → generates UPDATE SQL automatically
```

### How It Works
```
@Transactional starts    → Hibernate takes snapshot of loaded entities
You modify entity fields → changes tracked in memory
@Transactional ends      → Hibernate compares current vs snapshot
                         → generates UPDATE only for changed fields
                         → commits to DB
```

### When save() IS Needed
```java
// New entity — not yet managed by Hibernate
Product product = Product.builder()...build();
product = productRepository.save(product);  // INSERT — must call save()

// Existing entity loaded in transaction — dirty checking handles it
// NO save() needed
```

---

## LazyInitializationException

### What It Is
```java
@Transactional
public Product getProduct(Long id) {
    return productRepository.findById(id).orElseThrow();
}  // transaction ends here — session closed

// Later, outside transaction:
product.getVendor().getName();  // LazyInitializationException!
// Hibernate tries to load vendor — but session is already closed
```

### Fix 1 — Access inside @Transactional
```java
@Transactional
public ProductResponse getProduct(Long id) {
    Product product = productRepository.findById(id).orElseThrow();
    return toResponse(product);  // access vendor here — session still open
}
```

### Fix 2 — JOIN FETCH when you know you'll need the data
```java
@Query("SELECT p FROM Product p JOIN FETCH p.vendor WHERE p.id = :id")
Optional<Product> findByIdWithVendor(@Param("id") Long id);
```

### Fix 3 — open-in-view: false (already in application.yaml)
```yaml
spring:
  jpa:
    open-in-view: false  # NEVER true in REST APIs
    # true = keeps session open for entire HTTP request
    # causes N+1 problems, connection pool exhaustion
```

---

## PostgreSQL ENUM Mapping

### Three Annotations Required
```java
@Enumerated(EnumType.STRING)                    // 1. store as string not number
@JdbcType(PostgreSQLEnumJdbcType.class)         // 2. cast to PostgreSQL native type
@Column(name = "status", columnDefinition = "order_status")  // 3. exact PG type name
private OrderStatus status;
```

Without all three → `ERROR: column "status" is of type order_status but expression is of type character varying`

---

## Tree Building Algorithm (Categories)

### Problem
Load all categories flat from DB, build nested tree in Java.

### Why Not Pagination
Tree building needs the complete dataset. Pagination gives a subset.
You can't build a complete tree from a partial list.

### Algorithm — O(n)
```java
public List<CategoryTreeResponse> getAllCategoriesAsTree() {
    List<Category> all = categoryRepository.findByIsActiveTrue();

    // Step 1: convert all to nodes — O(n)
    Map<Long, CategoryTreeResponse> nodeMap = new HashMap<>();
    for (Category cat : all) {
        nodeMap.put(cat.getId(), new CategoryTreeResponse(
            cat.getId(), cat.getName(), cat.getDisplayOrder(),
            cat.getIsActive(), new ArrayList<>()  // mutable list
        ));
    }

    // Step 2: assign children to parents — O(n)
    List<CategoryTreeResponse> roots = new ArrayList<>();
    for (Category cat : all) {
        if (cat.getParent() == null) {
            roots.add(nodeMap.get(cat.getId()));         // root node
        } else {
            CategoryTreeResponse parent = nodeMap.get(cat.getParent().getId());
            if (parent != null) {
                parent.children().add(nodeMap.get(cat.getId()));
            }
        }
    }
    return roots;
}
```

Why O(n): HashMap lookup is O(1). Two loops = O(n) + O(n) = O(n).
Naive recursive approach = O(n²) — for each node search full list for children.
