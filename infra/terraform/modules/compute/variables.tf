variable "cluster_name" {
  description = "CG-DOC: Name of the shared k3d cluster verified by Terraform."
  type        = string
}

variable "agents" {
  description = "CG-DOC: Documented agent count of the shared cluster; not applied by Terraform."
  type        = number
  default     = 1
}

variable "k3s_version" {
  description = "CG-DOC: Documented k3s image tag of the shared cluster."
  type        = string
  default     = "v1.35.5-k3s1"
}

variable "ingress_port" {
  description = "CG-DOC: Host port already published by the shared cluster for Traefik."
  type        = number
  default     = 30080
}
