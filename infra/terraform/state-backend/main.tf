resource "aws_s3_bucket" "tfstate" {
  # CG-DOC: Remote state backend for future roots. Versioning allows recovery
  # from a bad apply; force_destroy stays false so state cannot be wiped silently.
  bucket = var.state_bucket
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_dynamodb_table" "state_lock" {
  # CG-DOC: The S3 backend requires this table to prevent concurrent applies.
  name           = var.lock_table
  billing_mode   = "PROVISIONED"
  read_capacity  = 5
  write_capacity = 5
  hash_key       = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}
