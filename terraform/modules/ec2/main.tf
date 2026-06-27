# ─── modules/ec2/main.tf ──────────────────────────────────────────────────────
#
# Creates the EC2 instance that runs JewelCart.
#
# The user_data script runs AUTOMATICALLY on first boot:
#   1. Installs Docker
#   2. Logs into ECR using IAM role (no credentials needed)
#   3. Reads secrets from Parameter Store
#   4. Pulls JewelCart Docker image
#   5. Starts the container
#   6. Flyway runs migrations on app startup
#
# This means: after terraform apply → wait 5 minutes → app is running.
# No SSH needed. No manual steps.

# FIND AMAZON LINUX 2023 AMI
# AMI = Amazon Machine Image = the operating system template.
# This data source finds the latest Amazon Linux 2023 AMI automatically.
# Without this, you'd need to hardcode the AMI ID and update it manually.
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# EC2 INSTANCE
resource "aws_instance" "app" {
  # Operating system
  ami           = data.aws_ami.amazon_linux_2023.id
  instance_type = var.instance_type   # t3.micro = free tier

  # SSH key pair — create in AWS Console before running terraform
  key_name = var.key_name

  # Network
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  associate_public_ip_address = true   # gives public IP so we can reach the app

  # IAM Role
  # This gives EC2 permission to read from ECR and Parameter Store.
  # No access keys stored on the server — AWS provides temp credentials via the role.
  iam_instance_profile = var.iam_instance_profile

  # USER DATA SCRIPT
  # Runs automatically on first boot (like @PostConstruct in Spring).
  # Everything in this script runs as root.
  # heredoc syntax: <<-EOF ... EOF = multi-line string
  user_data = <<-EOF
    #!/bin/bash
    set -e   # exit on any error

    echo "=== JewelCart Startup Script ==="
    echo "Starting at: $(date)"

    # ── STEP 1: Install Docker ────────────────────────────────────────────────
    echo "Installing Docker..."
    yum update -y
    yum install -y docker

    # Start Docker service and enable it to start on reboot
    systemctl start docker
    systemctl enable docker

    # Add ec2-user to docker group so they can run docker without sudo
    usermod -a -G docker ec2-user

    echo "Docker installed: $(docker --version)"

    # ── STEP 2: Login to ECR ──────────────────────────────────────────────────
    # Uses IAM role — no credentials needed!
    # EC2 automatically gets temporary credentials from the role.
    echo "Logging into ECR..."
    aws ecr get-login-password --region ${var.aws_region} | \
      docker login --username AWS --password-stdin ${var.ecr_registry}

    echo "ECR login successful"

    # ── STEP 3: Read secrets from Parameter Store ─────────────────────────────
    # EC2 reads each secret by name using AWS CLI.
    # --with-decryption = decrypt the SecureString before returning
    # --query Parameter.Value = extract just the value (not the full JSON)
    # --output text = return as plain text (not JSON with quotes)
    echo "Reading secrets from Parameter Store..."

    DB_URL=$(aws ssm get-parameter \
      --name "${var.ssm_db_url_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    DB_USERNAME=$(aws ssm get-parameter \
      --name "${var.ssm_db_username_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    DB_PASSWORD=$(aws ssm get-parameter \
      --name "${var.ssm_db_password_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    JWT_SECRET_KEY=$(aws ssm get-parameter \
      --name "${var.ssm_jwt_secret_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    RAZORPAY_KEY_ID=$(aws ssm get-parameter \
      --name "${var.ssm_razorpay_key_id_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    RAZORPAY_KEY_SECRET=$(aws ssm get-parameter \
      --name "${var.ssm_razorpay_key_secret_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    RAZORPAY_WEBHOOK_SECRET=$(aws ssm get-parameter \
      --name "${var.ssm_razorpay_webhook_param}" \
      --with-decryption \
      --query Parameter.Value \
      --output text \
      --region ${var.aws_region})

    echo "Secrets loaded successfully"

    # ── STEP 4: Pull Docker image from ECR ────────────────────────────────────
    IMAGE="${var.ecr_registry}/${var.ecr_repository}:latest"
    echo "Pulling image: $IMAGE"
    docker pull $IMAGE
    echo "Image pulled successfully"

    # ── STEP 5: Run JewelCart container ───────────────────────────────────────
    # -d                → detached (runs in background)
    # --name jewelcart  → container name for easy management
    # --restart always  → restart if container crashes OR if EC2 reboots
    # -p 8080:8080      → map host port 8080 to container port 8080
    # -e KEY=VALUE      → inject secrets as environment variables
    #                     secrets never written to disk — only in memory
    echo "Starting JewelCart container..."
    docker run -d \
      --name jewelcart \
      --restart always \
      -p 8080:8080 \
      -e DB_URL="$DB_URL" \
      -e DB_USERNAME="$DB_USERNAME" \
      -e DB_PASSWORD="$DB_PASSWORD" \
      -e JWT_SECRET_KEY="$JWT_SECRET_KEY" \
      -e RAZORPAY_KEY_ID="$RAZORPAY_KEY_ID" \
      -e RAZORPAY_KEY_SECRET="$RAZORPAY_KEY_SECRET" \
      -e RAZORPAY_WEBHOOK_SECRET="$RAZORPAY_WEBHOOK_SECRET" \
      $IMAGE

    echo "JewelCart container started"

    # ── STEP 6: Wait for app to be healthy ────────────────────────────────────
    # Flyway runs migrations automatically on Spring Boot startup.
    # Wait up to 3 minutes for health check to pass.
    echo "Waiting for application to be healthy..."
    for i in {1..18}; do
      sleep 10
      if curl -s http://localhost:8080/api/actuator/health | grep -q '"status":"UP"'; then
        echo "Application is healthy! Startup complete."
        break
      fi
      echo "Attempt $i/18 — still starting..."
    done

    echo "=== Startup script complete at: $(date) ==="

    # ── STEP 7: Install Grafana Alloy ─────────────────────────────────────────
        echo "Installing Grafana Alloy..."

        # Add Grafana repo
        cat > /etc/yum.repos.d/grafana.repo << 'REPO'
    [grafana]
    name=grafana
    baseurl=https://rpm.grafana.com
    repo_gpgcheck=1
    enabled=1
    gpgcheck=1
    gpgkey=https://rpm.grafana.com/gpg.key
    sslverify=1
    sslcacert=/etc/pki/tls/certs/ca-bundle.crt
    REPO

        yum install -y alloy

        # Read Grafana API token from Parameter Store
        GRAFANA_TOKEN=$(aws ssm get-parameter \
          --name "${var.ssm_grafana_api_token_param}" \
          --with-decryption \
          --query Parameter.Value \
          --output text \
          --region ${var.aws_region})

        # ── STEP 8: Configure Alloy ───────────────────────────────────────────────
        # Alloy config tells it:
        #   1. WHERE to scrape metrics (your Spring Boot app)
        #   2. WHERE to send metrics (Grafana Cloud)
        cat > /etc/alloy/config.alloy << ALLOY
    prometheus.scrape "jewelcart" {
      targets = [{
        __address__ = "localhost:8080",
        __metrics_path__ = "/api/actuator/prometheus",
      }]
      forward_to = [prometheus.remote_write.grafana_cloud.receiver]

      scrape_interval = "15s"

      job_name = "jewelcart"
    }

    prometheus.remote_write "grafana_cloud" {
      endpoint {
        url = "${var.grafana_remote_write_url}"

        basic_auth {
          username = "${var.grafana_username}"
          password = "$GRAFANA_TOKEN"
        }
      }
    }
    ALLOY

        # Start Alloy
        systemctl start alloy
        systemctl enable alloy

        echo "Grafana Alloy started"
        echo "=== Full setup complete at: $(date) ==="
  EOF

  # TAGS
  tags = {
    Name = "${var.project_name}-server-${var.environment}"
  }

  # DEPENDENCY
  # Terraform creates EC2 AFTER all other resources.
  # This ensures:
  #   → ECR exists (so image can be pulled)
  #   → Parameter Store has secrets (so app can start)
  #   → Security groups exist (network rules ready)
}
