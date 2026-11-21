<!-- CG-DOC: On-premise infrastructure as the operational source (`infra/onpremise/`). -->
# Infrastructure — On-premise (operativa)

## Plataforma actual

La infraestructura vigente de CircleGuard es **on-premise** y vive en `infra/onpremise/` y `deploy/`:

| Capa | Componente | Definido en |
|---|---|---|
| Herramientas | Docker Compose proyecto `cg-tools` | `deploy/compose/tools/docker-compose.onpremise.yml` |
| Plataforma | Clúster k3d `cg` (namespaces `cg-dev`, `cg-stage`, `cg-prod`) | `deploy/k8s/` (Kustomize) |
| Backend IaC | Terraform `state-backend` (bucket S3 + tabla de lock) | `infra/terraform/state-backend/` |
| Hosts | Un servidor Ubuntu 24.04, ZeroTier `10.147.20.67`, usuario `docker` sin `sudo` | `docs/infra/migracion-onpremise.md` |

Herramientas en el host: Jenkins (`:8080`), SonarQube (`:9000`), Nexus (`:8081`), registro Docker (`:5000`) y el relay smee.io del webhook (GitHub → `https://smee.io/nk4nvlnyB0u6Fqnn` → Jenkins).

## Carpeta `infra/onpremise/`

| Archivo | Propósito |
|---|---|
| `README.md` | Guía de arranque rápido: requisitos, orden de ejecución y flujo de la plataforma. |
| `bootstrap-onpremise.sh` | Crea `deploy/compose/tools/.env.onpremise` desde la plantilla, genera los secretos, pregunta GitHub y el webhook (el secreto no se genera aleatoriamente). |
| `k3d-cluster-create.sh` | Crea el clúster `cg` con la plantilla `registries.yaml` (registro HTTP del host declarado al clúster). |
| `registries.yaml` | Espejo HTTP `10.147.20.67:5000` para el clúster k3d. |
| `verify-onpremise.sh` | Chequeo **read-only** de salud de herramientas, clúster y aplicaciones (códigos HTTP, namespaces, Ingress). |

## Flujo

1. `git clone` del repo en el host.
2. `./infra/onpremise/bootstrap-onpremise.sh` → `.env.onpremise` (nunca se commitea).
3. `./infra/onpremise/k3d-cluster-create.sh` → clúster `cg` + namespaces.
4. `docker compose --env-file deploy/compose/tools/.env.onpremise -f deploy/compose/tools/docker-compose.onpremise.yml -p cg-tools up -d`.
5. `kubectl apply -k deploy/k8s/overlays/dev` (y `stage`/`prod`).
6. `./infra/onpremise/verify-onpremise.sh` — debe devolver `OK` en cada cheque.

El motivo de la migración desde AWS y la evidencia histórica están documentados en
[`docs/infra/migracion-onpremise.md`](../docs/infra/migracion-onpremise.md) y en
[`docs/informe/`](../docs/informe/).

## Reglas de operación (host compartido)

- No tocar `k8s-worker` ni `icesi-inn`.
- Sin `docker prune`, sin `docker compose down -v`.
- Secretos solo en `.env.onpremise` y en los `Secret` del clúster (`cg-secrets`).
- Como máximo dos ambientes completos encendidos a la vez (RAM; ver `deploy/k8s/README.md`).

## Terraform: backend de estado (HU-INFRA-06)

`infra/terraform/state-backend/` es el **único** proyecto Terraform vigente. No despliega
aplicaciones, no crea clústeres y no gestiona namespaces: solo prepara el backend remoto para
futuros roots.

| Recurso | Servicio | Endpoint |
|---|---|---|
| Bucket `circleguard-tfstate` con versioning | `minio-iac` | `http://127.0.0.1:9002` |
| Tabla `circleguard-tf-lock` con `LockID` | `dynamodb-local` | `http://127.0.0.1:8000` |

El estado de este proyecto es **local** y está ignorado por git: es el caso huevo-huevo, el
bucket no puede alojar el estado que lo crea. Los futuros roots que usen backend remoto copian
`backend.hcl.example` y exportan `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` con las
credenciales de MinIO de `.env.onpremise`. Comandos y precauciones en
[`docs/infra/runbook.md`](../docs/infra/runbook.md) → «Backend de estado con Terraform».

## Legado AWS ( Parte 1 — histórico, no operativo )

Los scripts `infra/bootstrap/*` (migración a AWS) y su estado (`infra/state/`) se conservan **solo como evidencia** de la entrega Parte 1. No se ejecutan, no se operan y no se documentan como flujo vigente; si encuentras referencias a ellos fuera de `docs/informe/` o `CHANGELOG.md`, repórtalas.

- Resumen histórico: `docs/infra/ci-cd-setup.md` (sección marcada "Parte 1 — histórico"), `docs/infra/runbook.md`, `docs/infra/environments.md`.
- Informe y evidencias: `docs/informe/`.
- Anejo de migración: `docs/infra/migracion-onpremise.md`.
- Costo de referencia AWS: `docs/infra/aws-free-tier-validation.md`.
