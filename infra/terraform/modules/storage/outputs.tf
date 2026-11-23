output "file_bucket" {
  description = "CG-DOC: Name of the MinIO bucket used for files."
  value       = aws_s3_bucket.files.bucket
}

output "backup_bucket" {
  description = "CG-DOC: Name of the MinIO bucket used for backups."
  value       = aws_s3_bucket.backups.bucket
}

output "pvc_name" {
  description = "CG-DOC: Application persistent volume claim name."
  value       = kubernetes_persistent_volume_claim.app_data.metadata[0].name
}

output "minio_endpoint" {
  description = "CG-DOC: MinIO endpoint used by this environment."
  value       = var.minio_endpoint
}
