# CG-DOC: Non-secret parameter values for the stage environment.
env                = "stage"
namespace          = "cg-stage"
obs_namespace      = "cg-obs-stage"
cluster_name       = "cg"
agents             = 2
k3s_version        = "v1.35.5-k3s1"
kube_context       = "k3d-cg"
file_bucket_name   = "circleguard-files-stage"
backup_bucket_name = "circleguard-backups-stage"
pvc_size           = "20Gi"
registry_size      = "10Gi"
prometheus_version = "61.7.0"
loki_version       = "2.17.4"
ingress_port       = 30080
force_destroy      = false
