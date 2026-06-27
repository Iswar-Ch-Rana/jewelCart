# ─── modules/rds/main.tf ──────────────────────────────────────────────────────
#
# Creates AWS RDS PostgreSQL database.
# AWS manages: installation, patching, backups, storage scaling.
# You just define what you want — AWS handles the rest.
#
# Takes ~10 minutes to create (normal — AWS is provisioning hardware).

# SUBNET GROUP
# RDS needs to know which subnets it can use.
# Requires subnets in at least 2 different Availability Zones.
# This is for high availability — if one AZ fails, the other is ready.
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group-${var.environment}"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

# RDS INSTANCE
resource "aws_db_instance" "main" {
  identifier = "${var.project_name}-db-${var.environment}"

  # DATABASE ENGINE
  engine         = "postgres"
  engine_version = "16"            # PostgreSQL 16 — latest stable

  # INSTANCE SIZE
  instance_class = var.instance_class
  # db.t3.micro = free tier eligible (750 hours/month)
  # db.t3.small = 2GB RAM, ~$30/month — use when app has real traffic

  # STORAGE
  allocated_storage     = 20        # 20 GB = minimum and free tier max
  storage_type          = "gp2"     # general purpose SSD
  storage_encrypted     = true      # encrypt data at rest — always do this
  max_allocated_storage = 0         # 0 = disable auto-scaling (prevents surprise bills)

  # DATABASE CREDENTIALS
  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  # NETWORKING
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.security_group_id]
  publicly_accessible    = true     # needed so EC2 can reach it
                                    # security group still restricts access to EC2 only

  # BACKUPS
  # AWS automatically takes daily snapshots
  backup_retention_period = 1       # keep 1 day of backups (minimum)
  backup_window          = "03:00-04:00"   # 3-4 AM UTC (low traffic time)
  maintenance_window     = "Mon:04:00-Mon:05:00"  # patches applied Monday 4-5 AM UTC

  # UPGRADES
  auto_minor_version_upgrade = true  # automatically apply minor patches (security fixes)
  apply_immediately          = false  # major changes wait for maintenance window

  # COST CONTROLS
  # If terraform destroy fails and RDS is deleted, don't create a final snapshot.
  # In production: set to false to keep a backup before deletion.
  skip_final_snapshot = true

  # DELETION PROTECTION
  # Set to true in production to prevent accidental deletion.
  # Must set to false before you can run terraform destroy.
  deletion_protection = false

  # MONITORING
  # Both disabled to stay within free tier.
  # Enable in production for detailed DB performance metrics.
  performance_insights_enabled = false
  monitoring_interval          = 0    # 0 = disabled

  tags = {
    Name = "${var.project_name}-db-${var.environment}"
  }
}
