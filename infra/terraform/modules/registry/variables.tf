variable "env" {
  description = "CG-DOC: Environment name used to build resource names."
  type        = string
}

variable "namespace" {
  description = "CG-DOC: Namespace where the registry is deployed."
  type        = string
}

variable "image" {
  description = "CG-DOC: Container image used by the registry deployment."
  type        = string
  default     = "registry:2"
}

variable "storage_size" {
  description = "CG-DOC: Size of the persistent volume claim for images."
  type        = string
  default     = "5Gi"
}
