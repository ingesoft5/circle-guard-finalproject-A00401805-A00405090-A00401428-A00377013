variable "minio_endpoint" {
  description = "CG-DOC: MinIO S3 API endpoint published on the on-prem host."
  type        = string
  default     = "http://127.0.0.1:9002"
}

variable "dynamodb_endpoint" {
  description = "CG-DOC: DynamoDB Local endpoint used by the Terraform state lock."
  type        = string
  default     = "http://127.0.0.1:8000"
}

variable "aws_region" {
  description = "CG-DOC: Region reported to the AWS SDK; MinIO accepts any region."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket_name" {
  description = "CG-DOC: Bucket that will hold the root Terraform state."
  type        = string
  default     = "circleguard-tfstate"
}

variable "lock_table_name" {
  description = "CG-DOC: DynamoDB table used for state locking."
  type        = string
  default     = "circleguard-tf-lock"
}

variable "minio_user" {
  description = "CG-DOC: MinIO root user, used as AWS access key."
  type        = string
  sensitive   = true
}

variable "minio_password" {
  description = "CG-DOC: MinIO root password, used as AWS secret key."
  type        = string
  sensitive   = true
}