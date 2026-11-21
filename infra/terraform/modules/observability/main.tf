resource "kubernetes_namespace" "obs" {
  # CG-DOC: Isolate the monitoring stack from application namespaces.
  metadata {
    name = var.namespace
  }
}

resource "helm_release" "prometheus" {
  # CG-DOC: Deploy Prometheus, Grafana and Alertmanager for the environment.
  name       = "cg-${var.env}-prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = var.prometheus_version
  namespace  = kubernetes_namespace.obs.metadata[0].name

  set {
    name  = "grafana.adminPassword"
    value = var.grafana_admin_password
  }

  set {
    name  = "prometheus.persistence.enabled"
    value = "false"
  }
}

resource "helm_release" "loki" {
  # CG-DOC: Deploy Loki and Promtail for centralized logs.
  name       = "cg-${var.env}-loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki-stack"
  version    = var.loki_version
  namespace  = kubernetes_namespace.obs.metadata[0].name

  set {
    name  = "loki.persistence.enabled"
    value = "false"
  }
}
