# 08 — DevOps Pipeline

## Overview

```
Developer pushes code to GitHub
         ↓
GitHub Actions CI/CD Pipeline
  Job 1: Build + Test
  Job 2: Build Docker image + Push to ECR
         ↓
AWS ECR (Docker image registry)
         ↓
AWS EC2 (virtual machine — pulls image, runs container)
         ↓
AWS RDS PostgreSQL (managed database)
         ↓
AWS Parameter Store (secrets — DB URL, JWT, Razorpay keys)
         ↓
AWS IAM Role (EC2 reads secrets without hardcoded credentials)
```

---

## Why Docker

### Problem Without Docker
```
Developer machine:  Java 21, Ubuntu 22, specific configs
AWS EC2:            Different OS version, different Java
Result:             "Works on my machine" → crashes on server
```

### Solution With Docker
```
Docker image = app + Java 21 + OS configs packed together
Run anywhere: laptop, EC2, K8s — identical behavior
"Works in container" → works everywhere
```

### Multi-Stage Dockerfile
```dockerfile
# Stage 1: Builder (Maven + JDK) — ~500MB
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q    # cache deps layer
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Runtime (JRE only) — ~276MB
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S jewelcart && adduser -S jewelcart -G jewelcart
USER jewelcart                       # never run as root
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s CMD wget -q --spider http://localhost:8080/api/actuator/health
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage:**
```
Final image contains only JRE + JAR
Maven, source code, build tools NOT in production image
Smaller image = faster pull, less attack surface
276MB vs ~500MB if single stage
```

**Why non-root user:**
```
If container is compromised, attacker has limited permissions
Running as root = attacker has root access to host
Security best practice — always use non-root in production
```

**Why HEALTHCHECK:**
```
Docker/K8s knows when app is actually ready
Without it: container "running" but app still starting
With it: container marked healthy only when /actuator/health returns 200
```

---

## GitHub Actions CI/CD Pipeline

### Trigger
```yaml
on:
  push:
    branches: [ main ]      # runs on every push to main
  pull_request:
    branches: [ main ]      # runs on every PR to main
```

### Job 1 — Build and Test

Runs on every push AND pull request.

```
Step 1: Checkout code         → git clone your repo
Step 2: Set up Java 21        → install Temurin JDK (cached)
Step 3: Start PostgreSQL      → service container for tests
Step 4: Maven compile         → ./mvnw clean compile
Step 5: Maven test            → ./mvnw test
Step 6: Upload test results   → artifacts for debugging
```

**Why PostgreSQL service container:**
```
Tests need a real database
GitHub Actions spins up postgres:16-alpine container automatically
Available at localhost:5432 during the job
Destroyed after job completes
No external database needed
```

**Why Maven cache:**
```yaml
cache: maven
```
```
First run: downloads all dependencies (~200MB) → 3-4 minutes
Subsequent runs: restores from cache → 30 seconds
Saves significant CI time and bandwidth
```

### Job 2 — Build and Push Docker Image

Only runs on main branch push (not PRs). Only runs if Job 1 passes.

```yaml
needs: build-and-test         # dependency on Job 1
if: github.ref == 'refs/heads/main' && github.event_name == 'push'
```

```
Step 1: Checkout code
Step 2: Configure AWS credentials   → from GitHub Secrets
Step 3: Login to ECR                → temporary token (12 hours)
Step 4: Build Docker image          → two tags: SHA + latest
Step 5: Push to ECR                 → only changed layers pushed
Step 6: Summary                     → log image URI
```

**Why two tags:**
```
:latest    → always points to most recent build
            EC2 pulls :latest → always gets newest version

:<sha>     → unique per commit
            e.g. :2c9a3d35f178ea168e499f29554cbe2d3206d63b
            can roll back to any specific commit
            audit trail — know exactly what code is running
```

**Why only push on main:**
```
PRs: run tests only (validate the code) — don't push images
main: tests pass + push image (ready to deploy)
Prevents ECR filling up with unreviewed code images
```

---

## AWS Services — What Each Does

### IAM (Identity and Access Management)

**IAM User (jewelcart-github-actions):**
```
Used by: GitHub Actions
Has: permanent access keys (stored in GitHub Secrets)
Permissions: ECR push/pull only
Why user not role: GitHub Actions is external (not AWS service)
```

**IAM Role (jewelcart-ec2-role):**
```
Used by: EC2 instance
Has: no permanent keys — AWS provides temporary rotating credentials
Permissions:
  AmazonEC2ContainerRegistryReadOnly → pull images from ECR
  AmazonSSMReadOnlyAccess           → read Parameter Store secrets
  CloudWatchAgentServerPolicy       → send logs to CloudWatch

Why role not user:
  EC2 is an AWS service → can assume roles
  No credentials stored on disk → much more secure
  AWS rotates credentials automatically every hour
  If server is compromised → no permanent keys to steal
```

**Interview Answer:**
*"We attach an IAM Role to EC2 instead of storing access keys on the server. The role grants permissions to ECR and Parameter Store. AWS automatically provides temporary rotating credentials through the instance metadata service. No credentials are ever stored on disk — if the server is compromised, there are no permanent keys to steal."*

---

### ECR (Elastic Container Registry)

```
Private Docker image registry (like Docker Hub but on AWS)

Our repository: 966579633568.dkr.ecr.ap-south-2.amazonaws.com/jewelcart-app

Lifecycle policy: keep only 2 images
  → prevents storage exceeding free tier (500MB/month)
  → old images auto-deleted

Why ECR over Docker Hub:
  → private by default
  → same AWS network as EC2 → faster pulls, no egress cost
  → IAM authentication → no separate credentials
  → lifecycle policy → cost control
```

**Image push flow:**
```
docker build → tag with ECR URI → docker push
Only changed layers uploaded (not full 276MB every time)
Base JRE layer: cached after first push
Changed JAR layer: ~50MB per push
```

---

### Parameter Store (AWS Systems Manager)

```
Secure key-value store for configuration and secrets

Parameters created:
  /jewelcart/db-url               → RDS JDBC URL
  /jewelcart/db-username          → database username
  /jewelcart/db-password          → database password
  /jewelcart/jwt-secret           → JWT signing key
  /jewelcart/razorpay-key-id      → Razorpay API key
  /jewelcart/razorpay-key-secret  → Razorpay secret
  /jewelcart/razorpay-webhook-secret → webhook verification

Type: SecureString → encrypted at rest using AWS KMS
Free tier: 10,000 requests/month → sufficient for our scale
```

**How EC2 reads secrets at runtime:**
```bash
aws ssm get-parameter \
  --name /jewelcart/db-url \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region ap-south-2
```

IAM Role gives EC2 permission to call this API → no hardcoded credentials.

**Why Parameter Store over .env file:**
```
.env file on server:
  → stored on disk in plaintext
  → anyone with server access can read it
  → not auditable

Parameter Store:
  → encrypted at rest
  → access controlled by IAM
  → full audit trail (who read what, when)
  → centralized — update once, all instances get new value
  → never stored on disk
```

---

### RDS (Relational Database Service)

```
Managed PostgreSQL — AWS handles:
  → installation and patching
  → automated backups (1 day retention)
  → multi-AZ failover (if enabled)
  → storage management

Our instance:
  Identifier: jewelcart-db
  Engine: PostgreSQL 18.3
  Class: db.t4g.micro (free tier)
  Storage: 20 GiB gp2
  Region: ap-south-2c (Hyderabad)
  Endpoint: jewelcart-db.c7csgua2ykek.ap-south-2.rds.amazonaws.com

Flyway runs migrations on app startup → schema managed automatically
```

**Why RDS over self-managed PostgreSQL:**
```
Self-managed (on EC2):
  → you install, patch, backup, monitor PostgreSQL
  → if disk fills up → you fix it
  → if instance crashes → you restore backup manually

RDS:
  → AWS handles all of the above
  → automatic backups
  → point-in-time recovery
  → ~$0 on free tier (750 hours/month)
```

---

### EC2 (Elastic Compute Cloud)

```
Virtual machine running our Docker container

Instance: t3.micro
  2 vCPU, 1 GiB RAM
  Free tier: 750 hours/month

OS: Amazon Linux 2023
  Optimized for AWS
  Pre-installed: AWS CLI, SSM agent

Setup done manually (can be automated with User Data):
  1. Install Docker
  2. Login to ECR (using IAM Role — no credentials needed)
  3. Pull jewelcart-app:latest from ECR
  4. Run container with secrets from Parameter Store

Port 8080 open in security group → publicly accessible
```

**docker run command explained:**
```bash
docker run -d \                    # detached (background)
  --name jewelcart \               # container name
  --restart always \               # restart if crashes or EC2 reboots
  -p 8080:8080 \                   # map host:container ports
  -e DB_URL=$(aws ssm ...) \       # inject secret as env var
  ...
  image:latest
```

`--restart always` ensures container starts automatically after EC2 reboot.

---

## Security Group Rules

**EC2 Security Group:**
```
SSH   22    0.0.0.0/0  → SSH access (restrict to your IP in production)
HTTP  80    0.0.0.0/0  → web traffic
HTTPS 443   0.0.0.0/0  → secure web traffic
TCP   8080  0.0.0.0/0  → Spring Boot app
```

**RDS Security Group:**
```
PostgreSQL 5432 → from EC2 security group only (not public)
```

**Production hardening (Phase 5):**
```
EC2 SSH → restrict to VPN/bastion host IP only
RDS → allow only from EC2 security group
App → put behind Application Load Balancer (ALB)
ALB → HTTPS only, redirect HTTP to HTTPS
```

---

## Deployment Flow — Step by Step

```
1. Developer writes code locally
2. Tests pass locally: ./mvnw test
3. git push origin main

4. GitHub Actions Job 1 starts:
   → spins up Ubuntu runner + PostgreSQL container
   → checks out code
   → Java 21 setup (cached)
   → ./mvnw clean compile
   → ./mvnw test (contextLoads + other tests)
   → uploads test report as artifact

5. If Job 1 passes → Job 2 starts:
   → AWS credentials configured from GitHub Secrets
   → ECR login (temporary token)
   → docker build (multi-stage, ~2 minutes)
   → docker push (only changed layers, ~30 seconds)
   → image tagged as :latest and :<commit-sha>

6. Manual deployment to EC2 (Phase 5 will automate this):
   → SSH into EC2
   → docker pull jewelcart-app:latest
   → docker stop jewelcart && docker rm jewelcart
   → docker run ... (with new image)

7. Flyway runs migrations on startup:
   → connects to RDS
   → checks flyway_schema_history
   → runs any new migration files
   → app starts

8. Health check passes:
   curl http://EC2-IP:8080/api/actuator/health → {"status":"UP"}
```

---

## What's Not Automated Yet (Phase 5)

```
Current: manual SSH + docker run to deploy
Phase 5: auto-deploy after image push

Options:
  ECS (Elastic Container Service) → AWS managed containers
  K8s (Kubernetes)                → industry standard orchestration
  EC2 + shell script              → simplest, good for learning

For Phase 5: add third GitHub Actions job:
  Job 3: Deploy
    → SSH into EC2 via GitHub Actions
    → pull latest image
    → restart container
    → health check
```

---

## Cost Summary (Free Tier)

```
EC2 t3.micro:    750 hours/month free → $0
RDS db.t4g.micro: 750 hours/month free → $0
ECR:             500MB/month free → $0 (lifecycle policy keeps us under)
Parameter Store: 10,000 requests/month free → $0
Data transfer:   1GB/month free → $0 (dev traffic)

Total monthly cost: ~$0
```

**What could cost money:**
```
RDS storage autoscaling  → disabled ✅
Performance Insights     → disabled ✅
Enhanced Monitoring      → disabled ✅
ECR images > 2           → lifecycle policy removes old ones ✅
```
