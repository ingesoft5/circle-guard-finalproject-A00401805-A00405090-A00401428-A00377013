provider "aws" {
  # CG-DOC: Use on-prem MinIO as the S3-compatible provider endpoint.
  access_key                  = var.minio_access_key
  secret_key                  = var.minio_secret_key
  region                      = var.aws_region
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_region_validation      = true
  skip_requesting_account_id  = true

  endpoints {
    s3       = var.minio_endpoint
    dynamodb = var.dynamodb_endpoint
  }
}

provider "kubernetes" {
  # CG-DOC: Target the existing k3d/k3s kubeconfig on the on-prem host.
  config_path    = var.kubeconfig
  config_context = var.kube_context
}

provider "helm" {
  # CG-DOC: Target the same Kubernetes context for Helm releases.
  kubernetes {
    config_path    = var.kubeconfig
    config_context = var.kube_context
  }
}
