# JewelCart — Terraform Infrastructure

## What This Does

One command creates everything on AWS:
```bash
terraform apply
```

One command destroys everything (stops all charges):
```bash
terraform destroy
```

---

## Folder Structure

```
terraform/
├── README.md                    ← you are here
├── environments/
│   └── production/
│       ├── main.tf              ← entry point — calls all modules
│       ├── variables.tf         ← input values (region, names, etc.)
│       ├── outputs.tf           ← values printed after apply (EC2 IP, RDS endpoint)
│       └── terraform.tfvars     ← your actual values (DO NOT COMMIT)
└── modules/
    ├── ecr/                     ← Docker image registry
    ├── iam/                     ← IAM role for EC2
    ├── security-groups/         ← firewall rules for EC2 and RDS
    ├── rds/                     ← PostgreSQL database
    ├── ssm/                     ← Parameter Store secrets
    └── ec2/                     ← virtual machine that runs the app
```

---

## How Modules Work

Think of modules like functions in Java:
```
module "rds" {           // like calling a function
  source = "../modules/rds"
  db_name = "jewelcart_db"
}
```

Each module creates one type of resource. The root `main.tf` calls all modules in the right order.

---

## Prerequisites

Install Terraform:
```bash
# Ubuntu/Debian
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt update && sudo apt install terraform

# Verify
terraform version
```

Configure AWS credentials:
```bash
aws configure
# AWS Access Key ID: your key
# AWS Secret Access Key: your secret
# Default region: ap-south-2
# Default output format: json
```

---

## First Time Setup

```bash
cd terraform/environments/production

# Copy example vars and fill in your values
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your actual values

# Initialize (downloads AWS provider)
terraform init

# Preview what will be created (dry run)
terraform plan

# Create everything
terraform apply
```

---

## What Gets Created

```
1. ECR repository       → stores Docker images
2. IAM role             → lets EC2 access ECR and Parameter Store
3. Security groups      → firewall rules
4. Parameter Store      → stores secrets (DB URL, JWT, Razorpay keys)
5. RDS PostgreSQL       → managed database
6. EC2 instance         → runs JewelCart Docker container
```

Order matters — Terraform figures it out automatically.

---

## After Apply — What You Get

```
Outputs:

ec2_public_ip  = "13.235.xx.xx"
rds_endpoint   = "jewelcart-db.xxxxx.ap-south-2.rds.amazonaws.com"
ecr_uri        = "966579633568.dkr.ecr.ap-south-2.amazonaws.com/jewelcart-app"
app_url        = "http://13.235.xx.xx:8080/api/actuator/health"
```

Test immediately:
```bash
curl http://13.235.xx.xx:8080/api/actuator/health
# → {"status":"UP"}
```

---

## Stop All Charges (Before Sleeping / Going On Holiday)

```bash
terraform destroy
# Destroys everything → no charges
# Takes 10-15 minutes
```

Recreate later:
```bash
terraform apply
# Everything back → takes 10-15 minutes
```

---

## DO NOT COMMIT

Add to .gitignore:
```
terraform.tfvars          # contains real secrets
.terraform/               # downloaded providers (large)
*.tfstate                 # current infrastructure state
*.tfstate.backup          # state backups
```
