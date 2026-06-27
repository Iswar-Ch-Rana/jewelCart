# ─── modules/ecr/main.tf ──────────────────────────────────────────────────────
#
# Creates AWS ECR (Elastic Container Registry).
# This is where Docker images are stored.
# GitHub Actions pushes images here.
# EC2 pulls images from here.

resource "aws_ecr_repository" "app" {
  name                 = var.repository_name
  image_tag_mutability = "MUTABLE"   # allows overwriting :latest tag on each push

  # Encrypt images at rest using AWS-managed key
  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "${var.repository_name}-${var.environment}"
  }
}

# LIFECYCLE POLICY
# Automatically deletes old images to stay within 500MB free tier.
# Keeps only the 2 most recent images.
# Old images: automatically deleted → storage stays low → cost stays ~$0
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep only last 2 images — stays within ECR free tier"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 2
      }
      action = { type = "expire" }
    }]
  })
}
