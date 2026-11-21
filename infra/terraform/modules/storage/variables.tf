variable "env" {
  description = "CG-DOC: Environment name used to build resource names."
  type        = string
}

variable "namespace" {
  description = "CG-DOC: Namespace where the application PVC is created."
  type        = string
}

variable "file_bucket_name" {
  description = "CG-DOC: MinIO bucket for uploaded files."
  type        = string
}

variable "backup_bucket_name" {
  description = "CG-DOC: MinIO bucket for backups."
  type        = string
}

variable "pvc_size" {
  description = "CG-DOC: Size of the application persistent volume claim."
  type        = string
  default     = "10Gi"
}

variable "minio_endpoint" {
  description = "CG-DOC: MinIO endpoint reported as an output."
  type        = string
  default     = "http://127.0.0.1:9002"
}
