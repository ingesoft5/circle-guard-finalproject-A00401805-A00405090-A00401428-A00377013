provider "aws" {
  region = var.aws_region

  # CG-DOC: MinIO and DynamoDB Local do not implement the AWS metadata,
  # credential or account APIs, so those lookups must be skipped.
  access_key                  = var.minio_access_key
  secret_key                  = var.minio_secret_key
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_region_validation      = true
  skip_requesting_account_id  = true
  s3_use_path_style           = true

  endpoints {
    s3       = var.s3_endpoint
    dynamodb = var.dynamodb_endpoint
  }
}
