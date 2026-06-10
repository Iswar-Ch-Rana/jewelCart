# 04 — Security Patterns

## JWT — JSON Web Token

### Structure
```
eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJhZG1pbkBqZXdlbGNhcnQuY29tIn0.signature
        ↑ header              ↑ payload                               ↑ signature

header.payload.signature
```

Each part is Base64 encoded (NOT encrypted).

### Payload — What to Store
```json
{
  "sub": "admin@jewelcart.com",   // subject — who this token belongs to
  "userId": 1,                    // useful for queries, avoids DB call
  "role": "ROLE_ADMIN",          // for authorization checks
  "iat": 1716458752,             // issued at (unix timestamp)
  "exp": 1716545152              // expiry (iat + 24 hours)
}
```

### What NOT to Store in Payload
```
Password    → anyone with token can decode it at jwt.io
Credit card → same reason
Sensitive PII → payload is readable by anyone

JWT guarantees: tamper detection (signature), expiry
JWT does NOT guarantee: confidentiality (payload is Base64, not encrypted)
```

### Why userId in Payload
```java
// Without userId in token:
String email = jwtService.extractEmail(token);
User user = userRepository.findByEmail(email);  // extra DB call on every request
Long userId = user.getId();

// With userId in token:
Long userId = jwtService.extractUserId(token);  // no DB call needed
```

### Token Generation
```java
return Jwts.builder()
    .subject(user.getEmail())
    .claim("userId", user.getId())
    .claim("role", user.getRole().name())
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + expiration))
    .signWith(getSigningKey())
    .compact();
```

### Secret Key Location
```yaml
# application-dev.yaml — hex encoded 256-bit key
application:
  security:
    jwt:
      secret-key: 404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
      expiration: 86400000  # 24 hours

# application-prod.yaml — from environment variable
application:
  security:
    jwt:
      secret-key: ${JWT_SECRET_KEY}
      expiration: ${JWT_EXPIRATION}
```

Never hardcode secret key in source code. Anyone with GitHub access can forge tokens.

### Token Validation
```java
public boolean isTokenValid(String token, UserDetails userDetails) {
    final String email = extractEmail(token);
    return email.equals(userDetails.getUsername())  // email matches
            && !isTokenExpired(token);               // not expired
}
// If token tampered → signature mismatch → JwtException thrown
// No valid secret key = can't forge valid signature
```

### What Happens When Token is Stolen
```
Stolen JWT is valid until expiry — stateless auth trade-off.

Mitigations:
  Short expiry (15 min access token + refresh token)
  HTTPS only — prevents interception
  Token blacklist on logout (requires Redis — Phase 2)
  Refresh token rotation
```

---

## Spring Security Filter Chain

### Request Flow
```
HTTP Request
     ↓
JwtAuthFilter          ← our custom filter
     ↓
UsernamePasswordAuthenticationFilter  ← Spring's default
     ↓
Other filters...
     ↓
Controller
```

### Why JwtAuthFilter Runs First
```java
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

If JWT is valid → authentication set in SecurityContext → Spring's filter skips.
If no JWT → Spring's filter runs → handles form login (not used in our REST API).

### JwtAuthFilter — Step by Step
```java
protected void doFilterInternal(...) {

    // Step 1: read Authorization header
    String header = request.getHeader("Authorization");

    // Step 2: check Bearer prefix — "Authorization: Bearer eyJ..."
    if (header == null || !header.startsWith("Bearer ")) {
        filterChain.doFilter(request, response);
        return;  // no token — let request continue (public endpoints handle themselves)
    }

    // Step 3: extract token (remove "Bearer " — 7 characters)
    String token = header.substring(7);

    // Step 4: extract email from token
    String email = jwtService.extractEmail(token);

    // Step 5: skip if already authenticated
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
        filterChain.doFilter(request, response);
        return;
    }

    // Step 6: load user from DB
    UserDetails user = userRepository.findByEmail(email).orElse(null);

    // Step 7: validate token
    if (jwtService.isTokenValid(token, user)) {

        // Step 8: set authentication in SecurityContext
        UsernamePasswordAuthenticationToken authToken =
            new UsernamePasswordAuthenticationToken(
                user,                    // principal — your User entity
                null,                    // credentials — null after JWT validation
                user.getAuthorities()    // [ROLE_ADMIN], [ROLE_VENDOR], etc.
            );
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }

    // Step 9: always continue
    filterChain.doFilter(request, response);
}
```

### SecurityContextHolder
```
Thread-local storage for current request's authentication.
Each request has its own SecurityContext.
Cleared automatically after request completes.

Without it:
  Filter validates JWT ✅
  Controller: @PreAuthorize("hasRole('ADMIN')") → no user in context → 403 ❌

With it:
  Filter validates JWT → sets user in SecurityContextHolder
  Controller: @PreAuthorize checks SecurityContextHolder → finds ROLE_ADMIN → ✅
```

---

## SecurityConfig

### Key Settings
```java
.csrf(AbstractHttpConfigurer::disable)
// WHY: CSRF protects browser session cookies
//      JWT sent in Authorization header — not a cookie
//      No session = no CSRF risk = disable it

.sessionManagement(session -> session
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
// WHY: JWT carries all state — server remembers nothing between requests
//      STATELESS = never create HTTP session
//      Enables horizontal scaling — any server handles any request

.authorizeHttpRequests(auth -> auth
    .requestMatchers("/v1/auth/**").permitAll()
    .requestMatchers("GET", "/v1/products/**").permitAll()
    .anyRequest().authenticated())
// Public endpoints need no token
// Everything else requires valid JWT
```

### UserDetailsService
```java
@Bean
public UserDetailsService userDetailsService() {
    return username -> userRepository.findByEmail(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
}
```

Spring Security doesn't know about your User class.
UserDetailsService = the bridge: "here's how to load a user from your DB."

### AuthenticationProvider
```java
@Bean
public AuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService());  // how to load user
    provider.setPasswordEncoder(passwordEncoder());         // how to verify password
    return provider;
}
```

Used during login only — not for JWT validation.
Combines UserDetailsService + PasswordEncoder → verifies email + password.

---

## UserDetails Interface

### Why Implement UserDetails on User Entity
```
Spring Security lives in its own world.
It doesn't know about YOUR User class.
It only understands UserDetails interface.

By implementing UserDetails:
→ your User "speaks Spring Security's language"
→ Spring Security can work with your DB users directly
→ no manual conversion needed
```

### Required Methods
```java
public class User extends BaseEntity implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // "ROLE_" prefix required by Spring Security
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        // ADMIN → "ROLE_ADMIN"
        // VENDOR → "ROLE_VENDOR"
    }

    @Override
    public String getPassword() {
        return passwordHash;  // Spring uses this for login verification
    }

    @Override
    public String getUsername() {
        return email;  // we use email as username
    }

    @Override
    public boolean isEnabled() {
        return isActive;  // deactivated users cannot login
    }

    // these return true for simplicity — handle in Phase 2 if needed
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
```

---

## BCrypt Password Hashing

### Why BCrypt Not MD5/SHA-256
```
MD5, SHA-256 = fast hashing algorithms (designed for speed)
  → attacker can hash millions of guesses per second (brute force easy)

BCrypt = deliberately slow hashing algorithm
  → cost factor = number of rounds (default 10 = 2^10 = 1024 iterations)
  → attacker can only hash thousands per second
  → as hardware gets faster → increase cost factor → stays secure

BCrypt also adds random salt automatically:
  same password hashed twice → different hash
  "password123" → "$2a$10$N9qo8uLOickgx2ZMR..."
  "password123" → "$2a$10$X7k2JmP8vQp3NrT9s..."
  prevents rainbow table attacks (pre-computed hash tables)
```

### Usage
```java
// Encoding (registration)
String hash = passwordEncoder.encode(request.password());
// Never store plain text password

// Verification (login)
passwordEncoder.matches(rawPassword, storedHash);
// BCrypt handles the salt comparison internally
```

### Interview Answer
*"BCrypt is a one-way hash with automatic random salt. Same password produces different hash every time — prevents rainbow table attacks. BCrypt is intentionally slow — adjustable cost factor means it stays secure as hardware gets faster. MD5 and SHA-256 are designed for speed which makes brute force easy — wrong choice for passwords."*

---

## Role-Based Access Control

### Roles in JewelCart
```
ADMIN    → full access — manage vendors, products, orders, users
VENDOR   → own products and stock only
CUSTOMER → browse products, place orders, view own orders
```

### @PreAuthorize on Methods
```java
// Only ADMIN
@PostMapping
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<...> createVendor(...) { }

// ADMIN or VENDOR
@PostMapping
@PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
public ResponseEntity<...> createProduct(...) { }

// Any authenticated user (customer, vendor, admin)
@GetMapping("/my-orders")
public ResponseEntity<...> getMyOrders(...) { }

// Public — no authentication needed
@GetMapping("/search")
// no @PreAuthorize — covered by .permitAll() in SecurityConfig
```

### Enabling @PreAuthorize
```java
// Required in SecurityConfig
@EnableMethodSecurity   // enables @PreAuthorize on controller methods
public class SecurityConfig { }
```

Without this → @PreAuthorize annotations are ignored.

### Access Denied vs Unauthorized
```
401 Unauthorized → no token or invalid token
                   "Who are you?"
                   JwtAuthFilter catches this

403 Forbidden    → valid token but wrong role
                   "I know who you are but you can't do this"
                   @PreAuthorize catches this
```

### GlobalExceptionHandler for Security
```java
// 401
@ExceptionHandler(AuthenticationException.class)
public ResponseEntity<ApiResponse<Void>> handleAuthentication(...) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(error("Authentication required — please login"));
}

// 403
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ApiResponse<Void>> handleAccessDenied(...) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(error("Access denied — you don't have permission"));
}
```

Without these → Spring returns 500 instead of correct status codes.

---

## Auth Flow — Register and Login

### Registration
```
POST /v1/auth/register
  ↓
Check duplicate email (DuplicateResourceException if exists)
  ↓
BCrypt encode password
  ↓
Save User with role = CUSTOMER (hardcoded — users can't choose own role)
  ↓
Generate JWT immediately
  ↓
Return AuthResponse { token, tokenType, email, role }
```

### Login
```
POST /v1/auth/login
  ↓
AuthenticationManager.authenticate()
  → loads user via UserDetailsService
  → verifies password with BCrypt
  → throws BadCredentialsException if wrong → GlobalExceptionHandler → 401
  ↓
Load user from DB (authentication passed)
  ↓
Generate JWT
  ↓
Return AuthResponse
```

### Create Vendor (Admin Only)
```
POST /v1/auth/admin/create-vendor
Authorization: Bearer <admin-token>
  ↓
@PreAuthorize("hasRole('ADMIN')") → 403 if not admin
  ↓
Same as register but role = VENDOR (not CUSTOMER)
```

### Why Role Not in RegisterRequest
```java
// WRONG — security vulnerability
public record RegisterRequest(String email, String password, UserRole role) {}
// Attacker sends role = "ADMIN" → becomes admin instantly

// RIGHT — system assigns role
.role(UserRole.CUSTOMER)  // hardcoded in AuthService
```

---

## SecurityUtils — Getting Current User

### Why a Utility Class
Multiple services need the current user.
Copy-pasting SecurityContextHolder everywhere = code duplication.

```java
// common/util/SecurityUtils.java
public class SecurityUtils {

    public static User getCurrentUser() {
        return (User) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        // cast to User because JwtAuthFilter sets User entity as principal
    }

    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    public static String getCurrentUserEmail() {
        return getCurrentUser().getEmail();
    }
}
```

### Why Cast to User (not UserDetails)
JwtAuthFilter sets your User entity as the principal:
```java
new UsernamePasswordAuthenticationToken(
    user,   // ← your User entity, not just UserDetails
    null,
    user.getAuthorities()
);
```
Safe to cast to `User` directly — no extra DB call needed.

### Updated AuditConfig
```java
@Bean
public AuditorAware<String> auditorProvider() {
    return () -> {
        try {
            return Optional.of(SecurityUtils.getCurrentUserEmail());
            // created_by/updated_by now shows real user's email
        } catch (Exception e) {
            return Optional.of("system");
            // fallback for unauthenticated ops (Flyway migrations, startup)
        }
    };
}
```

---

## DataInitializer — Bootstrap Admin

### Problem
First time the app starts — no admin exists.
But creating admin requires... being admin.

### Solution — ApplicationRunner
```java
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("admin@jewelcart.com").isEmpty()) {
            User admin = User.builder()
                    .email("admin@jewelcart.com")
                    .passwordHash(passwordEncoder.encode("admin123456"))
                    .role(UserRole.ADMIN)
                    .isActive(true)
                    .build();
            userRepository.save(admin);
        }
        // idempotent — runs every startup but only creates if not exists
    }
}
```

ApplicationRunner runs after Spring context is fully loaded.
Idempotent — safe to run multiple times.
