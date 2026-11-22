resource "kubernetes_namespace" "obs" {
  # CG-DOC: Isolate the monitoring stack from application namespaces.
  metadata {
    name = var.namespace
  }
}

resource "helm_release" "prometheus" {
  # CG-DOC: Deploy Prometheus, Grafana and Alertmanager for the environment.
  # CG-DOC: A longer timeout is needed because Grafana may restart once before
  # its readiness probe succeeds on a freshly started single-node cluster.
  name       = "cg-${var.env}-prometheus"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  version    = var.prometheus_version
  namespace  = kubernetes_namespace.obs.metadata[0].name
  timeout    = 1800

  set {
    name  = "grafana.adminPassword"
    value = var.grafana_admin_password
  }

  set {
    name  = "prometheus.persistence.enabled"
    value = "false"
  }

  set {
    # CG-DOC: node-exporter binds hostPort 9100 on every node. With a single
    # shared k3d cluster only one environment may run it; stage and prod
    # disable it so Helm --wait is not blocked by unschedulable DaemonSet pods.
    name  = "nodeExporter.enabled"
    value = var.node_exporter_enabled ? "true" : "false"
  }
}

resource "helm_release" "loki" {
  # CG-DOC: Deploy Loki and Promtail for centralized logs.
  name       = "cg-${var.env}-loki"
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki-stack"
  version    = var.loki_version
  namespace  = kubernetes_namespace.obs.metadata[0].name
  timeout    = 1800

  set {
    # CG-DOC: Prometheus and Grafana already come from kube-prometheus-stack.
    name  = "grafana.enabled"
    value = "false"
  }

  set {
    # CG-DOC: The Loki chart registers a Grafana datasource with isDefault=true
    # by default. kube-prometheus-stack also registers Prometheus as default,
    # so Grafana aborts with "Only one datasource per organization can be
    # marked as default" and never becomes ready. Keep Loki as a regular
    # datasource so the install is independent of Helm release ordering.
    name  = "loki.isDefault"
    value = "false"
  }

  set {
    name  = "prometheus.enabled"
    value = "false"
  }

  set {
    # CG-DOC: Promtail exhausts the host's fs.inotify.max_user_instances=128
    # and crashes with "too many open files"; Loki stays available for logs.
    name  = "promtail.enabled"
    value = "false"
  }

  set {
    name  = "loki.persistence.enabled"
    value = "false"
  }
}
