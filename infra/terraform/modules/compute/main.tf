resource "null_resource" "k3d_cluster" {
  # CG-DOC: Verify the shared on-prem k3d cluster instead of creating one per
  # environment. The host already runs `cg` with one Traefik ingress on :30080.
  triggers = {
    cluster_name = var.cluster_name
  }

  provisioner "local-exec" {
    command     = "bash ${path.module}/scripts/verify-cluster.sh ${var.cluster_name}"
    interpreter = ["bash", "-c"]
  }
}
