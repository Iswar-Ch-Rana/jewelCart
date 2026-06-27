# ─── modules/ssm/main.tf ──────────────────────────────────────────────────────
#
# Stores all application secrets in AWS Parameter Store.
# Type: SecureString = encrypted with AWS KMS (free key).
#
# WHY NOT .env FILE ON SERVER:
#   .env on disk → anyone with server access can read it
#   Parameter Store → encrypted, access controlled by IAM, audit trail
#
# EC2 reads these at startup:
#   aws ssm get-parameter --name /jewelcart/db-url --with-decryption
#
# The RDS endpoint is passed in from the RDS module output.
# Terraform fills it automatically — no manual copy-paste.

locals {
  # Common prefix for all parameters — makes them easy to find in console
  prefix = "/${var.project_name}"
}

resource "aws_ssm_parameter" "db_url" {
  name        = "${local.prefix}/db-url"
  description = "JDBC URL for PostgreSQL RDS — auto-populated from RDS endpoint"
  type        = "SecureString"    # encrypted at rest
  value       = var.db_url        # built in main.tf using RDS endpoint output
}

resource "aws_ssm_parameter" "db_username" {
  name        = "${local.prefix}/db-username"
  description = "PostgreSQL master username"
  type        = "SecureString"
  value       = var.db_username
}

resource "aws_ssm_parameter" "db_password" {
  name        = "${local.prefix}/db-password"
  description = "PostgreSQL master password"
  type        = "SecureString"
  value       = var.db_password
}

resource "aws_ssm_parameter" "jwt_secret" {
  name        = "${local.prefix}/jwt-secret"
  description = "JWT signing secret key — used to sign and verify tokens"
  type        = "SecureString"
  value       = var.jwt_secret
}

resource "aws_ssm_parameter" "razorpay_key_id" {
  name        = "${local.prefix}/razorpay-key-id"
  description = "Razorpay API Key ID (rzp_test_xxx or rzp_live_xxx)"
  type        = "SecureString"
  value       = var.razorpay_key_id
}

resource "aws_ssm_parameter" "razorpay_key_secret" {
  name        = "${local.prefix}/razorpay-key-secret"
  description = "Razorpay API Key Secret"
  type        = "SecureString"
  value       = var.razorpay_key_secret
}

resource "aws_ssm_parameter" "razorpay_webhook_secret" {
  name        = "${local.prefix}/razorpay-webhook-secret"
  description = "Razorpay webhook verification secret — used to verify webhook signatures"
  type        = "SecureString"
  value       = var.razorpay_webhook_secret
}

# Grafana Cloud token — stored securely, injected into Alloy config
resource "aws_ssm_parameter" "grafana_api_token" {
  name        = "${local.prefix}/grafana-api-token"
  description = "Grafana Cloud API token for Prometheus remote write"
  type        = "SecureString"
  value       = var.grafana_api_token
}
