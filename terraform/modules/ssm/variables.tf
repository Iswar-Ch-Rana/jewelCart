variable "project_name" { type = string }
variable "environment"  { type = string }
variable "db_url" {
  type      = string
  sensitive = true
}
variable "db_username" {
  type      = string
  sensitive = true
}
variable "db_password" {
  type      = string
  sensitive = true
}
variable "jwt_secret" {
  type      = string
  sensitive = true
}
variable "razorpay_key_id" {
  type      = string
  sensitive = true
}
variable "razorpay_key_secret" {
  type      = string
  sensitive = true
}
variable "razorpay_webhook_secret" {
  type      = string
  sensitive = true
}

variable "grafana_api_token" {
  type      = string
  sensitive = true
}
