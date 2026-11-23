terraform {
  # CG-DOC: Pin Terraform and provider versions for the dev environment.
  required_version = ">= 1.5.0, < 2.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.37"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.15"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.2"
    }
  }

  backend "s3" {}
}
