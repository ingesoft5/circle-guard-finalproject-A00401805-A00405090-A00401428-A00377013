resource "aws_s3_bucket" "tfstate" {
  # CG-DOC: Create the remote state bucket before the root module configures its S3 backend.
  bucket        = var.state_bucket_name
  force_destroy = true
}

resource "aws_s3_bucket_versioning" "tfstate" {
  # CG-DOC: Keep previous state versions so a bad apply can be recovered.
  bucket = aws_s3_bucket.tfstate.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_dynamodb_table" "tf_lock" {
  # CG-DOC: Lock table required by the S3 backend to prevent concurrent applies.
  name           = var.lock_table_name
  billing_mode   = "PROVISIONED"
  read_capacity  = 5
  write_capacity = 5
  hash_key       = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}