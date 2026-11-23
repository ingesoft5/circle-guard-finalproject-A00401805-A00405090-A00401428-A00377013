<!-- CG-DOC: Terraform de CircleGuard (Parte 2, E2.1). -->
# Terraform de CircleGuard (Parte 2, E2.1)

La infraestructura completa de la plataforma se declara en módulos
reutilizables y tres ambientes parametrizados, conforme al entregable E2.1 del
proyecto final.

```text
infra/terraform/
├── state-backend/            # bucket S3 + tabla DynamoDB con LockID
├── modules/
│   ├── network/              # red: namespace, quota y NetworkPolicy
│   ├── compute/              # cómputo/clúster k3s/k3d
│   ├── registry/             # registro de contenedores
│   ├── storage/              # buckets MinIO y PVC
│   └── observability/        # Prometheus, Grafana y Loki
└── envs/
    ├── dev/
    ├── stage/
    └── prod/
```

Cada ambiente tiene su propio `terraform.tfvars`, su `backend.hcl` (copiado de
`backend.hcl.example`) y su clave de estado `platform/<env>/terraform.tfstate`
en el backend remoto MinIO + DynamoDB Local creado por `state-backend/`.

El pipeline de Jenkins ejecuta `init`, `validate`, `plan` y `apply` por ambiente,
con aprobación manual antes de producción (E2.3). Ver
[`docs/infra/runbook.md`](../../docs/infra/runbook.md#terraform-plataforma-y-backend-e21)
para los comandos y las credenciales.
