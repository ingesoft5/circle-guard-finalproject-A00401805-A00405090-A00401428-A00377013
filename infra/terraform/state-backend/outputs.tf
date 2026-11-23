output "state_bucket" {
  description = "MinIO bucket configured as the remote state backend."
  value       = aws_s3_bucket.tfstate.bucket
}

output "lock_table" {
  description = "DynamoDB Local table used for state locking."
  value       = aws_dynamodb_table.state_lock.name
}
