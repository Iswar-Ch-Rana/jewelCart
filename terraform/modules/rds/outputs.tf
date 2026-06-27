# modules/rds/outputs.tf
output "endpoint" {
  description = "RDS endpoint — used to build the JDBC URL"
  value       = aws_db_instance.main.address
  # .address = just the hostname (no port)
  # .endpoint = hostname:port
  # We use .address and add :5432 manually in the JDBC URL
}

output "port" {
  value = aws_db_instance.main.port
}
