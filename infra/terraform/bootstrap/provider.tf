provider "aws" {
  # CG-DOC: Point the AWS provider at on-prem MinIO (S3) and DynamoDB Local.
  access_key                  = var.minio_user
  secret_key                  = var.minio_password
  region                      = var.aws_region
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_region_validation      = true
  skip_requesting_account_id  = true

  endpoints {
    s3       = var.minio_endpoint
    dynamodb = var.dynamodb_endpoint
  }
}