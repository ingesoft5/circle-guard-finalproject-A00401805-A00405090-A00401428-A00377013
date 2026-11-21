resource "null_resource" "k3d_cluster" {
  # CG-DOC: Create the k3d cluster idempotently through the on-prem helper script.
  triggers = {
    cluster_name = var.cluster_name
    agents       = var.agents
    k3s_version  = var.k3s_version
    ingress_port = var.ingress_port
  }

  provisioner "local-exec" {
    command     = "bash ${path.module}/scripts/ensure-cluster.sh ${var.cluster_name} ${var.agents} ${var.k3s_version} ${var.ingress_port}"
    interpreter = ["bash", "-c"]
  }
}
