# ── STAGE 1: Build ────────────────────────────────────────────────────────────
# Use Maven + Java 21 to compile and package
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml first — Docker caches this layer
# If pom.xml unchanged, Maven deps not re-downloaded on next build
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code
COPY src ./src

# Build JAR — skip tests (tests run in GitHub Actions separately)
RUN mvn clean package -DskipTests -q

# ── STAGE 2: Run ──────────────────────────────────────────────────────────────
# Lightweight JRE only — no Maven, no source code
# alpine = smaller image (reduces attack surface + faster pull)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user — never run as root in production
RUN addgroup -S jewelcart && adduser -S jewelcart -G jewelcart
USER jewelcart

# Copy only the JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check — Docker/K8s knows when app is ready
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/actuator/health || exit 1

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
