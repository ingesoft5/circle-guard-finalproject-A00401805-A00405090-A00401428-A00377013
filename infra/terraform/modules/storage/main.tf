resource "aws_s3_bucket" "files" {
  # CG-DOC: Store uploaded certificates and documents in MinIO.
  bucket        = var.file_bucket_name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_versioning" "files" {
  # CG-DOC: Keep previous versions of uploaded files.
  bucket = aws_s3_bucket.files.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket" "backups" {
  # CG-DOC: Store environment backups in MinIO.
  bucket        = var.backup_bucket_name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_versioning" "backups" {
  # CG-DOC: Keep previous versions of backups.
  bucket = aws_s3_bucket.backups.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "kubernetes_persistent_volume_claim" "app_data" {
  # CG-DOC: Provide persistent storage for stateful workloads in the environment.
  metadata {
    name      = "cg-${var.env}-app-data"
    namespace = var.namespace
  }

  spec {
    access_modes = ["ReadWriteOnce"]

    resources {
      requests = {
        storage = var.pvc_size
      }
    }
  }
}
