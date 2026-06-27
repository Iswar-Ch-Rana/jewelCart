# ─── environments/production/main.tf ──────────────────────────────────────────
#
# This is the entry point for your infrastructure.
# It calls each module in the right order.
#
# Think of it like your main() function in Java —
# everything starts here.
#
# Terraform automatically figures out the order based on dependencies.
# Example: RDS must exist before Parameter Store stores the RDS endpoint.
# You just write what you want — Terraform handles the order.

# ─── TERRAFORM CONFIGURATION ──────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0" # use AWS provider version 5.x
    }
  }

  # WHERE TERRAFORM STORES STATE
  # State = what infrastructure currently exists
  # Without state, Terraform can't know what to update or delete
  #
  # Option A (local — simple, for learning):
  # State stored in terraform.tfstate file locally
  # Problem: if file is lost → Terraform loses track of infrastructure
  #
  # Option B (S3 — production):
  # Uncomment the backend block below to store state in S3
  # Team members can share state
  # Never lose state even if your laptop dies

  # backend "s3" {
  #   bucket = "jewelcart-terraform-state"
  #   key    = "production/terraform.tfstate"
  #   region = "ap-south-2"
  # }
}

# ─── AWS PROVIDER ─────────────────────────────────────────────────────────────
#
# Tells Terraform which AWS account and region to use.
# Credentials come from ~/.aws/credentials (aws configure)
# or environment variables AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY

provider "aws" {
  region = var.aws_region

  # Tags applied to EVERY resource automatically
  # Makes it easy to see all JewelCart resources in AWS console
  # and track costs per project
  default_tags {
    tags = {
      Project     = "JewelCart"
      Environment = var.environment
      ManagedBy   = "Terraform" # tells team this was created by Terraform
    }
  }
}

# ─── MODULE 1: ECR ────────────────────────────────────────────────────────────
#
# Creates the Docker image registry.
# GitHub Actions pushes images here.
# EC2 pulls images from here.
# Must exist before EC2 (EC2 needs the ECR URI).

module "ecr" {
  source = "../../modules/ecr"

  repository_name = var.ecr_repository_name
  environment     = var.environment
}

# ─── MODULE 2: IAM ────────────────────────────────────────────────────────────
#
# Creates the IAM role for EC2.
# Role gives EC2 permission to:
#   → pull images from ECR
#   → read secrets from Parameter Store
#   → send logs to CloudWatch
#
# No permanent credentials stored on EC2 —
# AWS provides temporary rotating credentials via the role.
# Much more secure than storing access keys on the server.

module "iam" {
  source = "../../modules/iam"

  role_name   = "${var.project_name}-ec2-role"
  environment = var.environment
}

# ─── MODULE 3: SECURITY GROUPS ────────────────────────────────────────────────
#
# Creates firewall rules for EC2 and RDS.
#
# EC2 security group allows:
#   → port 22   (SSH) from anywhere — restrict to your IP in production
#   → port 8080 (app) from anywhere — your API
#   → port 80   (HTTP) from anywhere
#   → port 443  (HTTPS) from anywhere
#
# RDS security group allows:
#   → port 5432 (PostgreSQL) ONLY from EC2 security group
#   → RDS is NOT publicly accessible directly — only EC2 can reach it

module "security_groups" {
  source = "../../modules/security-groups"

  project_name = var.project_name
  environment  = var.environment
  vpc_id       = var.vpc_id
}

# ─── MODULE 4: RDS ────────────────────────────────────────────────────────────
#
# Creates the managed PostgreSQL database.
# AWS handles: installation, patching, backups, storage.
#
# Depends on security groups — RDS needs the security group ID.
# Terraform automatically waits for security_groups module to finish.
#
# Takes ~10 minutes to provision.

module "rds" {
  source = "../../modules/rds"

  project_name      = var.project_name
  environment       = var.environment
  db_name           = var.db_name
  db_username       = var.db_username
  db_password       = var.db_password
  instance_class    = var.rds_instance_class
  security_group_id = module.security_groups.rds_security_group_id
  subnet_ids        = var.subnet_ids
}

# ─── MODULE 5: PARAMETER STORE ────────────────────────────────────────────────
#
# Stores all secrets securely in AWS Parameter Store.
# Type: SecureString = encrypted with AWS KMS.
#
# Depends on RDS — needs the RDS endpoint to build the DB URL.
# Terraform automatically waits for RDS to finish before this runs.
#
# EC2 reads these secrets at startup via AWS CLI.
# Secrets never stored on disk — only in memory at runtime.

module "ssm" {
  source = "../../modules/ssm"

  project_name = var.project_name
  environment  = var.environment

  # RDS endpoint comes from the RDS module output
  # Terraform fills this automatically — no manual copy-paste needed!
  db_url                  = "jdbc:postgresql://${module.rds.endpoint}:5432/${var.db_name}"
  db_username             = var.db_username
  db_password             = var.db_password
  jwt_secret              = var.jwt_secret
  razorpay_key_id         = var.razorpay_key_id
  razorpay_key_secret     = var.razorpay_key_secret
  razorpay_webhook_secret = var.razorpay_webhook_secret
  grafana_api_token       = var.grafana_api_token
}

# ─── MODULE 6: EC2 ────────────────────────────────────────────────────────────
#
# Creates the virtual machine that runs JewelCart.
#
# Depends on everything above:
#   → ECR URI (to pull the Docker image)
#   → IAM role (to access ECR and Parameter Store)
#   → Security group (firewall rules)
#   → Parameter Store (secrets must exist before app starts)
#
# User data script runs on first boot:
#   → installs Docker
#   → logs into ECR using IAM role (no credentials needed!)
#   → reads secrets from Parameter Store
#   → pulls JewelCart Docker image
#   → starts the container
#   → Flyway runs migrations automatically on app startup

module "ec2" {
  source = "../../modules/ec2"

  project_name         = var.project_name
  environment          = var.environment
  instance_type        = var.ec2_instance_type
  key_name             = var.ec2_key_name
  security_group_id    = module.security_groups.ec2_security_group_id
  iam_instance_profile = module.iam.instance_profile_name
  ecr_registry         = module.ecr.registry_url
  ecr_repository       = module.ecr.repository_name
  aws_region           = var.aws_region
  subnet_id            = var.subnet_ids[0]

  # SSM parameter names — EC2 reads these at startup
  ssm_db_url_param              = module.ssm.db_url_param_name
  ssm_db_username_param         = module.ssm.db_username_param_name
  ssm_db_password_param         = module.ssm.db_password_param_name
  ssm_jwt_secret_param          = module.ssm.jwt_secret_param_name
  ssm_razorpay_key_id_param     = module.ssm.razorpay_key_id_param_name
  ssm_razorpay_key_secret_param = module.ssm.razorpay_key_secret_param_name
  ssm_razorpay_webhook_param    = module.ssm.razorpay_webhook_param_name

  # Grafana Cloud
  grafana_remote_write_url      = var.grafana_remote_write_url
  grafana_username              = var.grafana_username
  ssm_grafana_api_token_param   = module.ssm.grafana_api_token_param_name
}
