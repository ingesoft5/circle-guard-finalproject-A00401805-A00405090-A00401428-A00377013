resource "kubernetes_namespace" "env" {
  # CG-DOC: Create one isolated namespace per environment.
  metadata {
    name = var.namespace
  }
}

resource "kubernetes_resource_quota" "env" {
  # CG-DOC: Bound the environment so dev cannot consume prod resources.
  metadata {
    name      = "cg-${var.env}-quota"
    namespace = kubernetes_namespace.env.metadata[0].name
  }

  spec {
    hard = {
      "pods"            = tostring(var.max_pods)
      "requests.cpu"    = tostring(var.cpu_request)
      "requests.memory" = var.memory_request
    }
  }
}

resource "kubernetes_network_policy" "default_deny" {
  # CG-DOC: Default-deny traffic. Allow same namespace, the ingress controller
  # namespace and the private CIDRs that host the on-prem registry and DNS.
  metadata {
    name      = "cg-${var.env}-default-deny"
    namespace = kubernetes_namespace.env.metadata[0].name
  }

  spec {
    pod_selector {}
    policy_types = ["Ingress", "Egress"]

    ingress {
      from {
        namespace_selector {
          match_labels = {
            "kubernetes.io/metadata.name" = kubernetes_namespace.env.metadata[0].name
          }
        }
      }
    }

    dynamic "ingress" {
      for_each = toset(var.ingress_namespaces)
      content {
        from {
          namespace_selector {
            match_labels = {
              "kubernetes.io/metadata.name" = ingress.value
            }
          }
        }
      }
    }

    egress {
      to {
        namespace_selector {
          match_labels = {
            "kubernetes.io/metadata.name" = kubernetes_namespace.env.metadata[0].name
          }
        }
      }
    }

    dynamic "egress" {
      for_each = toset(var.egress_cidrs)
      content {
        to {
          ip_block {
            cidr = egress.value
          }
        }
      }
    }
  }
}
