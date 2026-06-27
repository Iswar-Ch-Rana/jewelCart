# ─── environments/production/variables.tf ─────────────────────────────────────
#
# All input variables for your infrastructure.
# Think of these like function parameters in Java.
#
# Format:
#   variable "name" {
#     description = "what is this?"
#     type        = string / number / bool / list / map
#     default     = "value"  ← optional, use when there's a sensible default
#   }
#
# Values come from terraform.tfvars file.
# Sensitive values (passwords, keys) should NEVER have defaults.
# Terraform will ask you to type them if not in tfvars.

# ─── AWS CONFIGURATION ────────────────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region where all resources will be created"
  type        = string
  default     = "ap-south-2" # Hyderabad — closest to India
}

variable "environment" {
  description = "Environment name — used in resource names and tags"
  type        = string
  default     = "production"
}

variable "project_name" {
  description = "Project name — used as prefix for all resource names"
  type        = string
  default     = "jewelcart"
}

# ─── NETWORKING ───────────────────────────────────────────────────────────────

variable "vpc_id" {
  description = "VPC ID where resources will be created. Use default VPC for learning."
  type        = string
  # Find in AWS Console → VPC → Your VPCs → copy the VPC ID (vpc-xxxxxxxxx)
}

variable "subnet_ids" {
  description = "List of subnet IDs for RDS (needs 2 subnets in different AZs) and EC2"
  type        = list(string)
  # Find in AWS Console → VPC → Subnets → copy subnet IDs
  # Example: ["subnet-abc123", "subnet-def456"]
}

# ─── ECR ──────────────────────────────────────────────────────────────────────

variable "ecr_repository_name" {
  description = "Name of the ECR repository that stores Docker images"
  type        = string
  default     = "jewelcart-app"
}

# ─── EC2 ──────────────────────────────────────────────────────────────────────

variable "ec2_instance_type" {
  description = "EC2 instance size. t3.micro is free tier eligible."
  type        = string
  default     = "t3.micro"
  # Options:
  # t3.micro  → 1GB RAM, free tier, good for learning
  # t3.small  → 2GB RAM, ~$15/month, better for real load
}

variable "ec2_key_name" {
  description = "Name of the EC2 key pair for SSH access. Create in AWS Console first."
  type        = string
  default     = "jewelcart-key"
  # AWS Console → EC2 → Key Pairs → Create key pair → download .pem file
}

# ─── RDS ──────────────────────────────────────────────────────────────────────

variable "rds_instance_class" {
  description = "RDS instance size. db.t3.micro is free tier eligible."
  type        = string
  default     = "db.t3.micro"
}

variable "db_name" {
  description = "Name of the PostgreSQL database to create"
  type        = string
  default     = "jewelcart_db"
}

variable "db_username" {
  description = "PostgreSQL master username"
  type        = string
  default     = "jewelcart_user"
}

variable "db_password" {
  description = "PostgreSQL master password. Stored in Parameter Store, never in code."
  type        = string
  sensitive   = true # Terraform hides this value in logs and plan output
  # No default — Terraform will ask you to type it, or read from tfvars
}

# ─── APPLICATION SECRETS ──────────────────────────────────────────────────────
#
# These are stored in AWS Parameter Store as SecureString (encrypted).
# EC2 reads them at runtime — never stored on disk.

variable "jwt_secret" {
  description = "JWT signing secret key — minimum 32 characters hex string"
  type        = string
  sensitive   = true
}

variable "razorpay_key_id" {
  description = "Razorpay API Key ID (rzp_test_xxx or rzp_live_xxx)"
  type        = string
  sensitive   = true
}

variable "razorpay_key_secret" {
  description = "Razorpay API Key Secret"
  type        = string
  sensitive   = true
}

variable "razorpay_webhook_secret" {
  description = "Razorpay webhook verification secret"
  type        = string
  sensitive   = true
}

# ─── GRAFANA CLOUD MONITORING ─────────────────────────────────────────────────

variable "grafana_remote_write_url" {
  description = "Grafana Cloud Prometheus remote write URL"
  type        = string
}

variable "grafana_username" {
  description = "Grafana Cloud Prometheus username (numeric ID)"
  type        = string
}

variable "grafana_api_token" {
  description = "Grafana Cloud API token for metrics push"
  type        = string
  sensitive   = true
}
