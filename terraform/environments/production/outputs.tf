# ─── environments/production/outputs.tf ───────────────────────────────────────
#
# Values printed after "terraform apply" finishes.
# Like return values from a function.
#
# After apply you'll see:
#   ec2_public_ip = "13.235.xx.xx"
#   rds_endpoint  = "jewelcart-db.xxxxx.ap-south-2.rds.amazonaws.com"
#   app_url       = "http://13.235.xx.xx:8080/api/actuator/health"
#
# You can also get outputs later:
#   terraform output ec2_public_ip

output "ec2_public_ip" {
  description = "Public IP address of the EC2 instance — use this to SSH and test the app"
  value       = module.ec2.public_ip
}

output "rds_endpoint" {
  description = "RDS database endpoint — already stored in Parameter Store automatically"
  value       = module.rds.endpoint
}

output "ecr_registry_url" {
  description = "ECR registry URL — add this to GitHub Secrets as ECR_REGISTRY"
  value       = module.ecr.registry_url
}

output "ecr_repository_name" {
  description = "ECR repository name — add this to GitHub Secrets as ECR_REPOSITORY"
  value       = module.ecr.repository_name
}

output "app_url" {
  description = "URL to test the running application"
  value       = "http://${module.ec2.public_ip}:8080/api/actuator/health"
}

output "ssh_command" {
  description = "Command to SSH into EC2"
  value       = "ssh -i ~/.ssh/jewelcart-key.pem ec2-user@${module.ec2.public_ip}"
}

output "iam_role_name" {
  description = "IAM role name attached to EC2"
  value       = module.iam.role_name
}
