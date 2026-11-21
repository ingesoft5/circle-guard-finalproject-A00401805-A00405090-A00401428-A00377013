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
