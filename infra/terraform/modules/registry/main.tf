resource "kubernetes_persistent_volume_claim" "registry" {
  # CG-DOC: Persist container images for the environment registry.
  # CG-DOC: The shared k3d cluster uses a WaitForFirstConsumer storage class,
  # so the PVC stays Pending until a pod mounts it; do not wait for Bound.
  metadata {
    name      = "cg-${var.env}-registry-pvc"
    namespace = var.namespace
  }

  spec {
    access_modes = ["ReadWriteOnce"]

    resources {
      requests = {
        storage = var.storage_size
      }
    }
  }

  wait_until_bound = false
}

resource "kubernetes_deployment" "registry" {
  # CG-DOC: Run a Docker registry v2 instance inside the environment namespace.
  metadata {
    name      = "cg-${var.env}-registry"
    namespace = var.namespace
  }

  spec {
    replicas = 1

    selector {
      match_labels = {
        app = "cg-${var.env}-registry"
      }
    }

    template {
      metadata {
        labels = {
          app = "cg-${var.env}-registry"
        }
      }

      spec {
        container {
          name  = "registry"
          image = var.image

          port {
            container_port = 5000
          }

          volume_mount {
            name       = "registry-data"
            mount_path = "/var/lib/registry"
          }
        }

        volume {
          name = "registry-data"
          persistent_volume_claim {
            claim_name = kubernetes_persistent_volume_claim.registry.metadata[0].name
          }
        }
      }
    }
  }
}

resource "kubernetes_service" "registry" {
  # CG-DOC: Expose the registry inside the cluster on port 5000.
  metadata {
    name      = "registry"
    namespace = var.namespace
  }

  spec {
    type = "ClusterIP"

    selector = {
      app = "cg-${var.env}-registry"
    }

    port {
      port        = 5000
      target_port = 5000
    }
  }
}
