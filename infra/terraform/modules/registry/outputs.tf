output "registry_endpoint" {
  description = "CG-DOC: In-cluster DNS endpoint of the registry service."
  value       = "registry.${var.namespace}.svc:5000"
}

output "pvc_name" {
  description = "CG-DOC: Persistent volume claim used by the registry."
  value       = kubernetes_persistent_volume_claim.registry.metadata[0].name
}
