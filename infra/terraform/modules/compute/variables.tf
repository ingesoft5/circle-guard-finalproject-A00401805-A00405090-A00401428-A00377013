variable "cluster_name" {
  description = "CG-DOC: Name of the k3d cluster managed by Terraform."
  type        = string
}

variable "agents" {
  description = "CG-DOC: Number of k3d agent nodes for the environment."
  type        = number
  default     = 1
}

variable "k3s_version" {
  description = "CG-DOC: k3s image tag used by the k3d cluster."
  type        = string
  default     = "v1.35.5+k3s1"
}

variable "ingress_port" {
  description = "CG-DOC: Host port mapped to the Traefik load balancer inside k3d."
  type        = number
  default     = 30080
}
