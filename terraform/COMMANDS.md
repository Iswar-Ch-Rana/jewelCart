Good. Here's the complete command reference for your project:

---

## Daily Commands

```bash
# Navigate to terraform directory
cd ~/Desktop/JAVA/jewelcart/terraform/environments/production

# See current state — what exists in AWS
terraform state list

# Preview changes — nothing created yet
terraform plan

# Create everything
terraform apply

# Destroy everything
terraform destroy
```

---

## Selective Commands — Use These

**Start only what you need for development:**

```bash
# Create only database (when you need to test DB)
terraform apply -target=module.rds

# Create only EC2 (when you need to test app)
terraform apply -target=module.ec2

# Create only secrets
terraform apply -target=module.ssm
```

**Stop only expensive resources (save credits):**

```bash
# Destroy only EC2 (most expensive — stop when not needed)
terraform destroy -target=module.ec2

# Destroy only RDS (second most expensive)
terraform destroy -target=module.rds

# Destroy EC2 + RDS together (keep ECR, IAM, SSM)
terraform destroy -target=module.ec2 -target=module.rds
```

**Keep always (free or near-free):**

```bash
# These cost nothing — keep them always
# ECR    → free when empty
# IAM    → always free
# SSM    → free tier
# Security groups → always free
```

---

## Recommended Daily Workflow

```bash
# Morning — start work
terraform apply -target=module.rds   # takes 10 min
terraform apply -target=module.ec2   # takes 2 min

# Evening — stop work
terraform destroy -target=module.ec2  # immediate
terraform destroy -target=module.rds  # takes 5 min
```

---

## Replace Single Resource

```bash
# Recreate only EC2 (new deployment)
terraform apply -replace=module.ec2.aws_instance.app

# Recreate only RDS (if corrupted)
terraform apply -replace=module.rds.aws_db_instance.main
```

---

## Get Output Values

```bash
# Get EC2 public IP
terraform output ec2_public_ip

# Get RDS endpoint
terraform output rds_endpoint

# Get SSH command
terraform output ssh_command

# Get app URL
terraform output app_url

# Get all outputs
terraform output
```

---

## Check What Exists in AWS

```bash
# Quick verify — all resources
terraform state list

# Detailed state of one resource
terraform state show module.ec2.aws_instance.app
terraform state show module.rds.aws_db_instance.main
```

---

## Emergency Commands

```bash
# App not working — check logs on EC2
ssh -i ~/.ssh/jewelcart-key.pem ec2-user@$(terraform output -raw ec2_public_ip)
docker logs jewelcart

# Restart app without recreating EC2
ssh -i ~/.ssh/jewelcart-key.pem ec2-user@$(terraform output -raw ec2_public_ip)
docker restart jewelcart

# Force recreate EC2 keeping everything else
terraform apply -replace=module.ec2.aws_instance.app

# Remove resource from state (without deleting from AWS)
terraform state rm module.ecr.aws_ecr_repository.app

# Import existing AWS resource into state
terraform import module.ecr.aws_ecr_repository.app jewelcart-app
```

---

## Cost Control Commands

```bash
# Check what's running (costs money)
aws ec2 describe-instances \
  --filters "Name=instance-state-name,Values=running" \
  --query 'Reservations[*].Instances[*].[InstanceId,InstanceType]' \
  --output table \
  --region ap-south-2

aws rds describe-db-instances \
  --query 'DBInstances[*].[DBInstanceIdentifier,DBInstanceStatus]' \
  --output table \
  --region ap-south-2
```

---

## Save This as a Script

Create `terraform/scripts/dev.sh` in your project:

```bash
#!/bin/bash
# Usage: ./dev.sh start | stop | status

cd ~/Desktop/JAVA/jewelcart/terraform/environments/production

case $1 in
  start)
    echo "Starting JewelCart infrastructure..."
    terraform apply -target=module.rds -auto-approve
    terraform apply -target=module.ec2 -auto-approve
    echo "App URL: $(terraform output app_url)"
    ;;
  stop)
    echo "Stopping JewelCart infrastructure..."
    terraform destroy -target=module.ec2 -auto-approve
    terraform destroy -target=module.rds -auto-approve
    echo "Done. No more charges."
    ;;
  status)
    terraform output
    ;;
  *)
    echo "Usage: ./dev.sh start|stop|status"
    ;;
esac
```

```bash
chmod +x terraform/scripts/dev.sh

# Start everything
./terraform/scripts/dev.sh start

# Stop everything
./terraform/scripts/dev.sh stop

# Check status
./terraform/scripts/dev.sh status
```

---
