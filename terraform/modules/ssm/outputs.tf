# modules/ssm/outputs.tf
# Parameter names — EC2 uses these to read secrets at startup
output "db_url_param_name"              { value = aws_ssm_parameter.db_url.name }
output "db_username_param_name"         { value = aws_ssm_parameter.db_username.name }
output "db_password_param_name"         { value = aws_ssm_parameter.db_password.name }
output "jwt_secret_param_name"          { value = aws_ssm_parameter.jwt_secret.name }
output "razorpay_key_id_param_name"     { value = aws_ssm_parameter.razorpay_key_id.name }
output "razorpay_key_secret_param_name" { value = aws_ssm_parameter.razorpay_key_secret.name }
output "razorpay_webhook_param_name"    { value = aws_ssm_parameter.razorpay_webhook_secret.name }
output "grafana_api_token_param_name"   { value = aws_ssm_parameter.grafana_api_token.name }
