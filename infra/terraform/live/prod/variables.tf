variable "env" {
  description = "CG-DOC: Environment identifier."
  type        = string
  default     = "dev"
}

variable "namespace" {
  description = "CG-DOC: Application namespace for this environment."
  type        = string
}

variable "obs_namespace" {
  description = "CG-DOC: Observability namespace for this environment."
  type        = string
}

variable "cluster_name" {
  description = "CG-DOC: k3d cluster name."
  type        = string
}

variable "agents" {
  description = "CG-DOC: Number of k3d agent nodes."
  type        = number
}

variable "k3s_version" {
  description = "CG-DOC: k3s image tag."
  type        = string
}

variable "kubeconfig" {
  description = "CG-DOC: Path to the kubeconfig file on the on-prem host."
  type        = string
  default     = "~/.kube/config"
}

variable "kube_context" {
  description = "CG-DOC: Kubernetes context for this environment."
  type        = string
}

variable "minio_endpoint" {
  description = "CG-DOC: MinIO S3 API endpoint."
  type        = string
  default     = "http://127.0.0.1:9002"
}

variable "aws_region" {
  description = "CG-DOC: Region reported to the AWS SDK."
  type        = string
  default     = "us-east-1"
}

variable "minio_user" {
  description = "CG-DOC: MinIO root user supplied via TF_VAR."
  type        = string
  sensitive   = true
}

variable "minio_password" {
  description = "CG-DOC: MinIO root password supplied via TF_VAR."
  type        = string
  sensitive   = true
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
  description = "CG-DOC: Application PVC size."
  type        = string
}

variable "registry_size" {
  description = "CG-DOC: Registry PVC size."
  type        = string
}

variable "grafana_admin_password" {
  description = "CG-DOC: Grafana admin password supplied via TF_VAR."
  type        = string
  sensitive   = true
}

variable "prometheus_version" {
  description = "CG-DOC: Pinned Prometheus chart version."
  type        = string
}

variable "loki_version" {
  description = "CG-DOC: Pinned Loki chart version."
  type        = string
}
