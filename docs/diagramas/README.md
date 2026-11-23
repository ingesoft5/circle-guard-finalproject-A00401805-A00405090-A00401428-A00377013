# Infrastructure diagrams (Parte 2, E2.1)

Los diagramas describen la infraestructura declarada con Terraform: módulos
reutilizables, tres ambientes parametrizados y el backend remoto del estado.

## 01 On-prem architecture

```mermaid
flowchart TB
  HOST["Host on-prem 10.147.20.67"]
  TOOLS["Docker Compose cg-tools"]
  MINIO["MinIO IAC :9002"]
  DDB["DynamoDB Local :8000"]
  TF["Terraform E2.1"]
  MOD["network · compute · registry · storage · observability"]
  ENVS["envs dev · stage · prod"]
  K3D["k3d cg único + Traefik :30080 · ruteo por Host"]
  APPS["Namespaces cg-dev · cg-stage · cg-prod (Kustomize)"]

  HOST --> TOOLS --> MINIO
  TOOLS --> DDB
  TF --> MOD --> ENVS --> K3D --> APPS
  MINIO --> TF
  DDB --> TF
```

Fuente: [`01-arquitectura-onprem.mmd`](01-arquitectura-onprem.mmd).

## 02 Terraform flow

```mermaid
flowchart LR
  BOOT["state-backend"] --> S3["MinIO circleguard-tfstate"]
  BOOT --> LOCK["DynamoDB Local circleguard-tf-lock"]
  S3 --> BACK["backend remoto s3"]
  LOCK --> BACK
  BACK --> ENVS["envs/dev · stage · prod"]
  ENVS --> MODS["5 módulos"]
  MODS --> CI["Jenkins: init · validate · plan · Trivy · apply"]
```

Fuente: [`02-flujo-terraform.mmd`](02-flujo-terraform.mmd).

## 03 Red y ambientes

```mermaid
flowchart TB
  HOST["Host 10.147.20.67 :5000"] --> CLUSTER["k3d cg único · Traefik :30080"]
  CLUSTER --> DEV["dev: cg-dev"]
  CLUSTER --> STAGE["stage: cg-stage"]
  CLUSTER --> PROD["prod: cg-prod"]
  DEV --> OBS["cg-obs: Prometheus + Grafana + Loki"]
  STAGE --> OBS
  PROD --> OBS
```

Fuente: [`03-red-y-ambientes.mmd`](03-red-y-ambientes.mmd). Las claves de estado
son `platform/<env>/terraform.tfstate` y se configuran desde
`infra/terraform/envs/<env>/backend.hcl.example`.
