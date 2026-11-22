variable "env" {
  description = "CG-DOC: Environment name used to build resource names."
  type        = string
}

variable "namespace" {
  description = "CG-DOC: Namespace where the observability stack is deployed."
  type        = string
}

variable "grafana_admin_password" {
  description = "CG-DOC: Grafana admin password supplied at runtime."
  type        = string
  sensitive   = true
}

variable "prometheus_version" {
  description = "CG-DOC: Pinned kube-prometheus-stack chart version."
  type        = string
  default     = "61.7.0"
}

variable "loki_version" {
  description = "CG-DOC: Pinned loki-stack chart version (latest published)."
  type        = string
  default     = "2.10.3"
}

variable "node_exporter_enabled" {
  description = "CG-DOC: Deploy the node-exporter DaemonSet for this environment."
  type        = bool
  default     = true
}
