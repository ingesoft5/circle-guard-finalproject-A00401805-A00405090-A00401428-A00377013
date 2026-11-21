# Infrastructure diagrams

Terraform cubre únicamente el backend de estado (bucket S3 + tabla de lock). El despliegue de
aplicaciones sigue siendo Kustomize sobre el clúster k3d `cg`.

## 01 On-prem architecture

```mermaid
flowchart TB
  HOST["Host on-prem 10.147.20.67"]
  TOOLS["Docker Compose cg-tools"]
  JENKINS["Jenkins :8080"]
  SONAR["SonarQube :9000"]
  NEXUS["Nexus :8081 + registry :5000"]
  MINIO["MinIO IAC :9002 / consola :9003"]
  DDB["DynamoDB Local :8000"]
  K3D["k3d cg / Traefik :30080"]
  ENVS["cg-dev · cg-stage · cg-prod (Kustomize)"]
  TF["Terraform state-backend"]

  HOST --> TOOLS
  HOST --> K3D
  TOOLS --> JENKINS
  TOOLS --> SONAR
  TOOLS --> NEXUS
  TOOLS --> MINIO
  TOOLS --> DDB
  K3D --> ENVS
  JENKINS -->|"kubectl apply -k"| ENVS
  NEXUS -->|"imágenes"| ENVS
  TF -->|"crea bucket"| MINIO
  TF -->|"crea tabla"| DDB
```

Fuente: [`01-arquitectura-onprem.mmd`](01-arquitectura-onprem.mmd).

## 02 Terraform flow

```mermaid
flowchart LR
  ENV[".env.onpremise<br/>TF_STATE_* / TF_LOCK_TABLE"] --> VAR["TF_VAR_*"]
  VAR --> TF["terraform init · validate · plan · apply"]
  TF --> LOCAL["terraform.tfstate local (gitignored)"]
  TF --> S3["aws_s3_bucket<br/>circleguard-tfstate + versioning"]
  TF --> LOCK["aws_dynamodb_table<br/>circleguard-tf-lock (LockID)"]
  S3 --> BACKEND["backend s3 para futuros roots"]
  LOCK --> BACKEND
```

Fuente: [`02-flujo-terraform.mmd`](02-flujo-terraform.mmd).

## 03 Backend and endpoints

```mermaid
flowchart TB
  CREATOR["state-backend (este proyecto)"] -.->|"crea"| MINIO["MinIO :9002<br/>circleguard-tfstate"]
  CREATOR -.->|"crea"| DDB["DynamoDB Local :8000<br/>circleguard-tf-lock"]
  ROOT["Root futuro con backend s3"] -->|"estado"| MINIO
  ROOT -->|"lock"| DDB
  MINIO --> KEYS["onpremise/dev/terraform.tfstate<br/>onpremise/stage/terraform.tfstate<br/>onpremise/prod/terraform.tfstate"]
  DDB --> ITEM["item LockID = onpremise/&lt;env&gt;/terraform.tfstate"]
```

Fuente: [`03-backend-y-endpoints.mmd`](03-backend-y-endpoints.mmd). La plantilla de configuración
para esos roots es `infra/terraform/state-backend/backend.hcl.example`.
