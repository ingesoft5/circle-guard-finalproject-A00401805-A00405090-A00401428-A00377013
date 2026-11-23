output "namespace" {
  description = "CG-DOC: Application namespace."
  value       = module.network.namespace
}

output "registry_endpoint" {
  description = "CG-DOC: In-cluster registry endpoint."
  value       = module.registry.registry_endpoint
}

output "file_bucket" {
  description = "CG-DOC: MinIO bucket for files."
  value       = module.storage.file_bucket
}

output "backup_bucket" {
  description = "CG-DOC: MinIO bucket for backups."
  value       = module.storage.backup_bucket
}

output "observability_namespace" {
  description = "CG-DOC: Observability namespace."
  value       = module.observability.namespace
}
