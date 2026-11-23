output "namespace" {
  description = "CG-DOC: Namespace hosting the observability stack."
  value       = kubernetes_namespace.obs.metadata[0].name
}

output "prometheus_release" {
  description = "CG-DOC: Helm release name for Prometheus."
  value       = helm_release.prometheus.name
}

output "loki_release" {
  description = "CG-DOC: Helm release name for Loki."
  value       = helm_release.loki.name
}
