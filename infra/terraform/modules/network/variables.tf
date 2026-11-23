variable "env" {
  description = "CG-DOC: Environment name used to build resource names."
  type        = string
}

variable "namespace" {
  description = "CG-DOC: Kubernetes namespace dedicated to the environment."
  type        = string
}

variable "max_pods" {
  description = "CG-DOC: Maximum number of pods allowed in the environment."
  type        = number
  default     = 100
}

variable "cpu_request" {
  description = "CG-DOC: Aggregate CPU requests quota for the namespace."
  type        = string
  default     = "4"
}

variable "memory_request" {
  description = "CG-DOC: Aggregate memory requests quota for the namespace."
  type        = string
  default     = "8Gi"
}

variable "ingress_namespaces" {
  description = "CG-DOC: Namespaces allowed to reach application pods (Traefik lives in kube-system)."
  type        = list(string)
  default     = ["kube-system"]
}

variable "egress_cidrs" {
  description = "CG-DOC: CIDRs allowed for egress, including the host registry and cluster DNS."
  type        = list(string)
  default     = ["10.0.0.0/8", "10.147.20.67/32"]
}
