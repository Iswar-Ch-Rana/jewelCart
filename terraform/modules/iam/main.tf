# ─── modules/iam/main.tf ──────────────────────────────────────────────────────
#
# Creates IAM Role for EC2.
#
# WHY ROLE NOT USER:
#   IAM User → permanent access keys stored on server → security risk
#   IAM Role → temporary rotating credentials → much more secure
#
# EC2 assumes this role → gets temporary credentials automatically
# If server is compromised → no permanent keys to steal
# AWS rotates credentials every hour → attacker's window is tiny

# THE ROLE ITSELF
# Defines WHO can assume this role.
# "ec2.amazonaws.com" means EC2 instances can use this role.
resource "aws_iam_role" "ec2_role" {
  name = var.role_name

  # Trust policy: "ec2 service is allowed to assume this role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Name        = var.role_name
    Environment = var.environment
  }
}

# PERMISSION 1: Read-only access to ECR
# EC2 can pull Docker images from ECR.
# Read-only — EC2 can't push or delete images.
resource "aws_iam_role_policy_attachment" "ecr_readonly" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# PERMISSION 2: Read-only access to Parameter Store (SSM)
# EC2 can read secrets at startup.
# Read-only — EC2 can't modify or delete secrets.
resource "aws_iam_role_policy_attachment" "ssm_readonly" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMReadOnlyAccess"
}

# PERMISSION 3: CloudWatch Agent
# Allows EC2 to send logs and metrics to CloudWatch.
# Useful for log analysis and alerting.
resource "aws_iam_role_policy_attachment" "cloudwatch" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# INSTANCE PROFILE
# Wrapper that lets you attach an IAM Role to an EC2 instance.
# EC2 needs instance profile, not role directly.
# Think of it as: Role → what permissions. Profile → attach to EC2.
resource "aws_iam_instance_profile" "ec2_profile" {
  name = "${var.role_name}-profile"
  role = aws_iam_role.ec2_role.name
}
