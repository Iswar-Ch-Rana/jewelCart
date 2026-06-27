# modules/ecr/outputs.tf
output "registry_url"    { value = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.name}.amazonaws.com" }
output "repository_name" { value = aws_ecr_repository.app.name }
output "repository_url"  { value = aws_ecr_repository.app.repository_url }

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
