# CG-DOC: Non-secret parameter values for the prod environment.
env                = "prod"
namespace          = "cg-prod"
obs_namespace      = "cg-obs-prod"
cluster_name       = "circleguard-prod"
agents             = 3
k3s_version        = "v1.35.5+k3s1"
kube_context       = "k3d-circleguard-prod"
file_bucket_name   = "circleguard-files-prod"
backup_bucket_name = "circleguard-backups-prod"
pvc_size           = "50Gi"
registry_size      = "20Gi"
prometheus_version = "61.7.0"
loki_version       = "2.17.4"
