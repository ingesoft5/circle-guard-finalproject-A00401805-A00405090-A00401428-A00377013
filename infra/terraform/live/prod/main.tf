module "compute" {
  # CG-DOC: Provision the environment cluster.
  source       = "../../modules/compute"
  cluster_name = var.cluster_name
  agents       = var.agents
  k3s_version  = var.k3s_version
}

module "network" {
  # CG-DOC: Provision namespace, quota and network policies.
  source     = "../../modules/network"
  env        = var.env
  namespace  = var.namespace
  depends_on = [module.compute]
}

module "registry" {
  # CG-DOC: Provision the in-cluster image registry.
  source       = "../../modules/registry"
  env          = var.env
  namespace    = module.network.namespace
  storage_size = var.registry_size
}

module "storage" {
  # CG-DOC: Provision MinIO buckets and the application PVC.
  source             = "../../modules/storage"
  env                = var.env
  namespace          = module.network.namespace
  file_bucket_name   = var.file_bucket_name
  backup_bucket_name = var.backup_bucket_name
  pvc_size           = var.pvc_size
  minio_endpoint     = var.minio_endpoint
}

module "observability" {
  # CG-DOC: Provision Prometheus, Grafana and Loki.
  source                 = "../../modules/observability"
  env                    = var.env
  namespace              = var.obs_namespace
  grafana_admin_password = var.grafana_admin_password
  prometheus_version     = var.prometheus_version
  loki_version           = var.loki_version
  depends_on             = [module.network]
}
