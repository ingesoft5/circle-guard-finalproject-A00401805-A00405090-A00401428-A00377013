output "state_bucket" {
  description = "CG-DOC: Name of the bucket created for the root Terraform state."
  value       = aws_s3_bucket.tfstate.bucket
}

output "lock_table" {
  description = "CG-DOC: Name of the DynamoDB lock table created for the root backend."
  value       = aws_dynamodb_table.tf_lock.name
}

output "minio_endpoint" {
  description = "CG-DOC: Endpoint to reuse in the root backend configuration."
  value       = var.minio_endpoint
}

output "dynamodb_endpoint" {
  description = "CG-DOC: Endpoint to reuse in the root backend configuration."
  value       = var.dynamodb_endpoint
}