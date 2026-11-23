# CG-DOC: Non-secret parameter values for the dev environment.
env                   = "dev"
namespace             = "cg-dev"
obs_namespace         = "cg-obs-dev"
cluster_name          = "cg"
agents                = 1
k3s_version           = "v1.35.5-k3s1"
kube_context          = "k3d-cg"
file_bucket_name      = "circleguard-files-dev"
backup_bucket_name    = "circleguard-backups-dev"
pvc_size              = "10Gi"
registry_size         = "5Gi"
prometheus_version    = "61.7.0"
loki_version          = "2.10.3"
node_exporter_enabled = true
ingress_port          = 30080
force_destroy         = true
