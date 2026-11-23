variable "aws_region" {
  description = "Region simulated by the on-prem backend; us-east-1 is enough for MinIO and DynamoDB Local."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket" {
  description = "MinIO bucket that stores the state of future Terraform roots."
  type        = string
}

variable "lock_table" {
  description = "DynamoDB Local table used by the S3 backend to lock state."
  type        = string
  default     = "circleguard-tf-lock"
}

variable "s3_endpoint" {
  description = "MinIO S3 API endpoint published by the cg-tools Compose stack."
  type        = string
  default     = "http://127.0.0.1:9002"
}

variable "dynamodb_endpoint" {
  description = "DynamoDB Local endpoint published by the cg-tools Compose stack."
  type        = string
  default     = "http://127.0.0.1:8000"
}

variable "minio_access_key" {
  description = "MinIO access key, injected as TF_VAR_minio_access_key from .env.onpremise."
  type        = string
  sensitive   = true
}

variable "minio_secret_key" {
  description = "MinIO secret key, injected as TF_VAR_minio_secret_key from .env.onpremise."
  type        = string
  sensitive   = true
}
