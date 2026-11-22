# CG-DOC: Non-secret parameter values for the prod environment.
env                   = "prod"
namespace             = "cg-prod"
obs_namespace         = "cg-obs-prod"
cluster_name          = "cg"
agents                = 3
k3s_version           = "v1.35.5-k3s1"
kube_context          = "k3d-cg"
file_bucket_name      = "circleguard-files-prod"
backup_bucket_name    = "circleguard-backups-prod"
pvc_size              = "50Gi"
registry_size         = "20Gi"
prometheus_version    = "61.7.0"
loki_version          = "2.10.3"
node_exporter_enabled = false
ingress_port          = 30080
force_destroy         = false
