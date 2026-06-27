# INTERVIEW PREP — JewelCart

All interview answers in one place.
Read this before every interview.

---

## Spring Boot Core

**Q: What is the difference between @Controller and @RestController?**
*"@RestController is a combination of @Controller and @ResponseBody. @Controller is for MVC applications that return HTML views. @RestController is for REST APIs that return JSON — every method automatically serializes the return value to JSON without needing @ResponseBody on each method."*

**Q: What is @SpringBootApplication?**
*"It's a combination of three annotations: @Configuration (marks this as a config class), @EnableAutoConfiguration (Spring Boot auto-configures beans based on classpath), and @ComponentScan (scans the package for Spring components). It's the entry point of the application."*

**Q: What is dependency injection and why constructor injection?**
*"Dependency injection means Spring creates and manages objects for you. Constructor injection is preferred over field injection because: dependencies are explicit and visible, the class can't be instantiated without required dependencies (fail-fast), and it's easier to test — you can pass mock objects directly in the constructor. Field injection with @Autowired hides dependencies and makes testing harder."*

**Q: What is @Component, @Service, @Repository difference?**
*"All three register a class as a Spring bean. @Service indicates business logic layer — semantic clarity. @Repository adds exception translation — JPA exceptions are converted to Spring's DataAccessException. @Component is generic — use when neither @Service nor @Repository applies. They're functionally similar but semantically different."*

---

## JPA and Database

**Q: What is the N+1 problem?**
*"When you load N entities and then access a lazy relationship on each one, you trigger N additional queries — one per entity. Loading 100 products then calling product.getVendor() for each = 101 queries. Solutions: JOIN FETCH for single entities, @EntityGraph for multiple associations, findAllById() for batch loading in bulk operations."*

**Q: What is dirty checking in JPA?**
*"JPA dirty checking automatically detects changes to managed entities within a transaction. When the transaction starts, Hibernate takes a snapshot of loaded entities. When the transaction commits, it compares current state against the snapshot and generates UPDATE statements only for changed fields. No explicit save() call needed for updates — just modify the fields and commit."*

**Q: JOIN FETCH vs @EntityGraph — when to use which?**
*"JOIN FETCH is safe for loading a single entity with its associations — no pagination, no multiple collections. @EntityGraph is used when loading multiple collections simultaneously — JOIN FETCH on two collections causes MultipleBagFetchException due to Cartesian product. @EntityGraph fetches each association in separate optimized queries, avoiding the problem."*

**Q: Why GenerationType.IDENTITY over AUTO in PostgreSQL?**
*"AUTO lets Hibernate decide — in PostgreSQL it creates a separate hibernate_sequence table. IDENTITY uses the database's native auto-increment which maps directly to BIGSERIAL in PostgreSQL. IDENTITY is cleaner, no extra tables, directly uses what Flyway already defined."*

**Q: What is LazyInitializationException?**
*"When you access a lazy-loaded relationship outside of a transaction, Hibernate tries to load it but the session is already closed. Fix: access within @Transactional method, use JOIN FETCH to eagerly load when needed, or set open-in-view to false and handle it properly in the service layer."*

**Q: Why FetchType.LAZY on all relationships?**
*"EAGER loads related entities immediately every time — even when you don't need them. 100 products with EAGER vendor = 101 queries (N+1). LAZY loads only when accessed — you control exactly when the extra query fires. Rule: always LAZY by default, use JOIN FETCH explicitly when you know you need the data."*

**Q: Why separate stock table instead of quantity in products?**
*"Stock is a variant-level concept. A product with Gold and Silver variants has independent quantities per variant. Putting quantity in the products table means one number for the entire product — you lose variant-level tracking. Separate stock table also enables stock history, per-variant thresholds, and future multi-warehouse support without changing the product schema."*

**Q: What is the difference between JPQL and SQL?**
*"SQL talks to tables and columns. JPQL talks to Java entity classes and their fields. JPQL is database-agnostic — same query works on PostgreSQL, MySQL, Oracle. JPQL uses dot notation to navigate relationships (p.vendor.id) which isn't possible in SQL without an explicit JOIN. Hibernate translates JPQL to the appropriate SQL dialect."*

**Q: What is JPQL LIKE syntax?**
*"LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')). LOWER on both sides makes it case-insensitive. Without LOWER — 'NECKLACE' won't find 'Necklace'. The trade-off is LOWER prevents index usage on that column — acceptable for search where correctness matters more than raw speed."*

---

## Transactions

**Q: What does @Transactional do?**
*"It wraps the method in a database transaction. All DB operations either all succeed (commit) or all fail (rollback). Spring uses a proxy — when you call the method, Spring's proxy starts the transaction, calls your method, then commits or rolls back based on whether an exception was thrown."*

**Q: readOnly = true — what does it do?**
*"Three things: no write locks acquired so reads don't block other reads, Hibernate skips dirty checking since we're not writing which improves performance, and some databases and connection pools route read-only transactions to read replicas automatically."*

**Q: PROPAGATION.REQUIRED vs REQUIRES_NEW?**
*"REQUIRED (default) — if a transaction exists, join it; if not, create one. All operations share one transaction — atomic, all-or-nothing. REQUIRES_NEW — always create a new independent transaction, suspend the outer one. Inner transaction commits independently. Trade-off: REQUIRED gives full atomicity but holds locks longer. REQUIRES_NEW releases locks quickly but if outer fails, inner already committed — needs compensating transaction to undo."*

**Q: What happens if you catch an exception inside @Transactional?**
*"If you catch and swallow the exception, Spring thinks the method succeeded and commits the transaction — even if data is in an inconsistent state. Always let exceptions propagate out of @Transactional methods so Spring can trigger rollback."*

---

## Pessimistic Locking

**Q: Two customers buy last item simultaneously — what happens?**
*"We use pessimistic locking with SELECT FOR UPDATE. The first transaction acquires a row-level lock on the stock row. The second transaction blocks — it can't read or write that row until the first commits. When the first commits, stock = 0. The second transaction reads 0, throws InsufficientStockException. One customer gets it, one gets a clear error. No overselling."*

**Q: How do you prevent deadlocks with pessimistic locking?**
*"Sort items by productId before acquiring locks. Both threads always lock in the same ascending order. If Thread 1 and Thread 2 both need products 1, 2, 3 — they both try to lock 1 first. Thread 2 waits for Thread 1 on product 1 instead of acquiring product 2 first. No circular dependency = no deadlock."*

**Q: When is the pessimistic lock released?**
*"Automatically when the transaction commits or rolls back. We never release manually. The lock lifecycle follows the transaction lifecycle — START: lock acquired on SELECT FOR UPDATE, END: lock released on COMMIT or ROLLBACK."*

---

## Security

**Q: How does JWT authentication work in your system?**
*"User logs in → AuthService verifies email/password via AuthenticationManager → generates JWT with userId, role, email, expiry. On subsequent requests, JwtAuthFilter intercepts, reads Authorization header, extracts Bearer token, validates signature and expiry, loads user from DB, sets authentication in SecurityContextHolder. Controllers then have access to the authenticated user and Spring Security enforces role-based access via @PreAuthorize."*

**Q: Why BCrypt over MD5 or SHA-256 for passwords?**
*"BCrypt is intentionally slow with a configurable cost factor. MD5 and SHA-256 are designed for speed — attackers can hash millions of guesses per second with GPUs. BCrypt's cost factor means attackers can only hash thousands per second. BCrypt also automatically generates a random salt for each hash — same password produces different hash every time — preventing rainbow table attacks."*

**Q: What is the difference between 401 and 403?**
*"401 Unauthorized means authentication is missing or invalid — no token, expired token, or invalid signature. The user is unknown to the system. 403 Forbidden means authentication is valid but the user doesn't have permission for that specific resource. We know who you are but you can't do this."*

**Q: Why is JWT payload not encrypted?**
*"JWT payload is Base64 encoded, not encrypted. Anyone with the token can decode it at jwt.io. This is why we never store sensitive data like passwords in the payload. JWT guarantees tamper detection via signature and expiry via exp claim — but not confidentiality. We only store non-sensitive identifiers: userId, role, email."*

**Q: Why STATELESS session management?**
*"JWT carries all authentication state — the server doesn't need to remember anything between requests. STATELESS means Spring never creates HTTP sessions. This enables horizontal scaling — any server instance can handle any request because there's no shared session state. Traditional session-based auth requires sticky sessions or shared session store."*

---

## System Design

**Q: Why modular monolith first, not microservices?**
*"Microservices add distributed systems complexity — network calls, distributed transactions, service discovery, independent deployments. Starting with a modular monolith gives us clean module boundaries and a working product faster. We use domain-based packaging so when we're ready to split, each domain becomes a service. The code moves, not gets rewritten."*

**Q: How would you decompose JewelCart into microservices?**
*"Each domain package becomes a service: Product Service, Vendor Service, Order Service, Payment Service, Auth Service, Inventory Service. An API Gateway handles routing and auth. Kafka for async events between services — OrderPlaced event triggers inventory deduction and payment initiation. gRPC for synchronous service-to-service calls where low latency matters."*

**Q: How does your order placement handle high concurrency?**
*"Four phases: Phase 1 loads all data with no locks — fail fast if products don't exist. Phase 2 deducts stock with pessimistic locking in sorted productId order — prevents deadlocks, locks held briefly. Phase 3 calculates prices in pure Java — no DB calls. Phase 4 saves order in one transaction. At scale we'd move to Kafka async processing — HTTP request returns immediately, stock deduction happens asynchronously."*

**Q: What is the state machine pattern in your orders?**
*"Order status follows strict transition rules — PENDING can go to CONFIRMED or CANCELLED, but not directly to SHIPPED. DELIVERED is a terminal state — no further transitions. We validate transitions in the service layer before updating status. Invalid transitions throw IllegalStateException → 409 Conflict. This prevents corrupt data like marking an order as delivered before it was even confirmed."*

**Q: Why soft delete everywhere?**
*"Hard deletes are dangerous in e-commerce. Orders reference products — deleting a product breaks order history. Financial records must be preserved. Users have order history — deleting a user loses that context. We set is_active = false instead. For financial records like orders and payments, we don't even have a delete endpoint — those are immutable by design."*

**Q: How do you handle 10,000 products in search?**
*"Pagination at database level using LIMIT/OFFSET — never load all into memory. JPQL with separate countQuery prevents Hibernate from paginating in memory. Case-insensitive LIKE search with LOWER(). For Phase 5 we'd add Elasticsearch for full-text search with relevance scoring — much better for product search at scale."*

**Q: What index would you add and why?**
*"Composite index on (vendor_id, is_active) because the most common query is 'active products for this vendor'. Both columns in one index means one B-tree lookup instead of two. Partial index on (is_featured) WHERE is_featured = true — only indexes the small subset of featured products, keeping the index tiny. Index on (selling_price) for price range filtering."*

---

## DevOps

**Q: Walk me through your Docker setup.**
*"docker-compose.yml runs PostgreSQL 16 Alpine with a named volume for data persistence — data survives container restarts. We use a healthcheck so the app waits for PostgreSQL to accept connections before starting. Alpine base image keeps it under 50MB. Named volumes work cross-platform — no permission issues like bind mounts on Windows."*

**Q: Why Flyway over Hibernate ddl-auto?**
*"Hibernate's ddl-auto: create drops all tables on startup — catastrophic in production. ddl-auto: update tries to alter tables unpredictably. Flyway gives version-controlled migrations with checksums — every migration runs exactly once, in order, and is verified. If a migration file changes after running, Flyway throws a checksum error. Full audit trail of every schema change."*

---

## Behavioral / STAR Stories

**Q: Tell me about a production issue you solved.**
*"At Arealytics, we had a stored procedure taking 30+ seconds. I used EXPLAIN ANALYZE and found double materialization — the query was processing a subquery multiple times, stale optimizer statistics, and a missing composite index. I added the index, updated statistics, and rewrote the subquery. Performance dropped from 30s to under 5s. Taught me to always check execution plans before optimizing."*

**Q: Tell me about a technical decision you made.**
*"In JewelCart I chose domain-based packaging over layer-based. I knew we'd split into microservices later. Domain-based means vendor, product, order — each domain self-contained. When we split, we move a package instead of refactoring across layers. The decision proved right when we started planning the microservices phase — minimal code changes needed."*

**Q: Tell me about a time you improved code quality.**
*"I introduced the four-phase order placement pattern. The original approach loaded products, locked stock, and calculated prices all mixed together in one loop. I identified three problems: N+1 queries, long lock duration, and deadlock risk. Separated into phases: bulk load first, then lock in sorted order, then calculate, then save. Eliminated N+1, reduced lock duration, prevented deadlocks."*

---

## Things to Say in Every Interview

```
1. "I use domain-based packaging because we planned microservices from day one."
2. "Flyway owns the schema — Hibernate only validates."
3. "Always LAZY fetch by default — JOIN FETCH only when you know you need the data."
4. "Soft delete everywhere — hard deletes break order history and financial records."
5. "Pessimistic locking with sorted lock ordering prevents both overselling and deadlocks."
6. "JWT is stateless — no sessions, horizontal scaling, any server handles any request."
7. "ApiResponse envelope gives consistent response format — frontend has one pattern."
8. "readOnly=true on all read methods — no write locks, no dirty checking overhead."
```

---

## Quick Reference

```
JWT expiry         → 24 hours (86400000ms)
BCrypt cost        → 10 rounds (default)
Max page size      → 100 items
Recent products    → max 50 items
HikariCP dev pool  → 5 connections
HikariCP prod pool → 20 connections
PostgreSQL version → 16
Java version       → 21
Spring Boot        → 3.5.14
Spring Security    → 6.5.10
```

---

## DevOps Questions

**Q: Walk me through your CI/CD pipeline.**

*"Every push to main triggers two GitHub Actions jobs. Job 1 spins up a PostgreSQL service container, compiles the code, and runs tests. If tests pass, Job 2 builds a multi-stage Docker image — Maven builds the JAR in one stage, then only the JRE and JAR go into the final image. The image is tagged with the commit SHA and pushed to AWS ECR. On EC2, we pull the latest image and restart the container with secrets injected from Parameter Store. Flyway runs migrations automatically on startup."*

---

**Q: Why Docker for deployment?**

*"Docker packages the application with its runtime environment — Java 21, configs, dependencies. This eliminates the 'works on my machine' problem. The same image runs identically on my laptop, GitHub Actions, and AWS EC2. Multi-stage build keeps the production image at 276MB — the builder stage with Maven never makes it into production. Non-root user adds another security layer."*

---

**Q: How do you manage secrets on AWS EC2?**

*"We use AWS Parameter Store with SecureString type — secrets are encrypted at rest using AWS KMS. EC2 reads them at container startup via the AWS CLI. The EC2 has an IAM Role that grants read-only access to Parameter Store — no credentials are stored on the server. If the server is compromised, there are no permanent keys to steal — the IAM Role provides temporary rotating credentials automatically."*

---

**Q: What is an IAM Role and why use it instead of IAM User on EC2?**

*"IAM User has permanent access keys that you download and store. IAM Role has no permanent keys — AWS provides temporary credentials that rotate automatically. EC2 can assume an IAM Role, so it gets permissions without storing any credentials on disk. If someone hacks the server, they can't steal permanent AWS credentials — the temporary credentials expire in an hour and are tied to the specific EC2 instance."*

---

**Q: How does GitHub Actions authenticate to AWS?**

*"We store AWS access keys as GitHub Secrets — AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY. The pipeline references them as `${{ secrets.AWS_ACCESS_KEY_ID }}` — never hardcoded in the yaml file. The IAM User has minimal permissions — only ECR push/pull. GitHub Actions uses these to login to ECR and push the Docker image."*

---

**Q: What happens if your EC2 crashes?**

*"The Docker container has `--restart always` flag — it restarts automatically if it crashes. If the EC2 instance itself crashes and restarts, Docker starts automatically (systemctl enable docker), and the container restarts because of the restart policy. For full EC2 failure, Phase 5 adds an Auto Scaling Group — AWS automatically launches a replacement EC2 and pulls the latest image from ECR."*

---

**Q: How do database migrations work in production?**

*"Flyway runs automatically on application startup. It connects to RDS, checks the flyway_schema_history table, and runs any new migration files in version order. If a migration fails, the app fails to start — preventing a broken app from running against an incomplete schema. This means deployments are atomic — either the migration succeeds and the app starts, or both fail and the old version keeps running."*

---

**Q: How do you handle zero-downtime deployment?**

*"Currently we have brief downtime during container restart — typically 10-15 seconds. Phase 5 fixes this with a blue-green deployment: run two EC2 instances (blue=current, green=new), deploy to green, test, then switch the load balancer to green. If green fails, switch back to blue instantly. Kubernetes rolling updates handle this automatically — gradually replacing old pods with new ones."*

---

**Q: What does your health check endpoint return?**

*"Spring Actuator exposes `/actuator/health` which checks: database connectivity (PostgreSQL via HikariCP), disk space, and ping. Returns `{status: UP}` when all checks pass. Docker uses this for the HEALTHCHECK instruction — the container is only marked healthy when the endpoint returns 200. Kubernetes uses the same endpoint for liveness and readiness probes."*

---

**Q: How would you scale JewelCart to handle 10x traffic?**

*"Horizontal scaling: add more EC2 instances behind an Application Load Balancer. Since our app is stateless (JWT authentication, no server-side sessions), any instance can handle any request. RDS read replicas handle increased read load — Spring's `@Transactional(readOnly=true)` routes to replicas automatically. For extreme scale: Redis caching for product catalog, Kafka for async order processing, and Kubernetes for automatic pod scaling based on CPU/memory metrics."*