output "namespace" {
  description = "CG-DOC: Namespace created for the environment."
  value       = kubernetes_namespace.env.metadata[0].name
}

output "resource_quota" {
  description = "CG-DOC: Resource quota applied to the environment."
  value       = kubernetes_resource_quota.env.metadata[0].name
}

output "network_policy" {
  description = "CG-DOC: Default network policy applied to the environment."
  value       = kubernetes_network_policy.default_deny.metadata[0].name
}
