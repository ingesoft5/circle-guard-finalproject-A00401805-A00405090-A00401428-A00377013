output "cluster_name" {
  description = "CG-DOC: Name of the managed k3d cluster."
  value       = var.cluster_name
}

output "agents" {
  description = "CG-DOC: Number of configured agent nodes."
  value       = var.agents
}
