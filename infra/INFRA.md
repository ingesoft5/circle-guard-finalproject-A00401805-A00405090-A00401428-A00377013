<!-- CG-DOC: On-premise infrastructure as the operational source (`infra/onpremise/`). -->
# Infrastructure — On-premise (operativa)

## Plataforma actual

La infraestructura vigente de CircleGuard es **on-premise** y vive en `infra/onpremise/` y `deploy/`:

| Capa | Componente | Definido en |
|---|---|---|
| Herramientas | Docker Compose proyecto `cg-tools` | `deploy/compose/tools/docker-compose.onpremise.yml` |
| Plataforma | Clúster k3d `cg` (namespaces `cg-dev`, `cg-stage`, `cg-prod`) | `deploy/k8s/` (Kustomize) |
| IaC (E2.1) | Terraform: módulos + 3 ambientes + backend remoto | `infra/terraform/` |
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
- Como máximo dos ambientes completos encendidos a la vez (RAM; ver `deploy/k8s/K8S.md`).

## Terraform: infraestructura como código (E2.1)

La plataforma se declara en `infra/terraform/` con módulos reutilizables y tres ambientes:

| Bloque | Contenido | Definido en |
|---|---|---|
| Backend remoto | Bucket `circleguard-tfstate` con versioning + tabla `circleguard-tf-lock` con `LockID` | `infra/terraform/state-backend/` |
| Módulos | `network`, `compute`, `registry`, `storage`, `observability` | `infra/terraform/modules/` |
| Ambientes | `dev`, `stage`, `prod` parametrizados, cada uno con su estado remoto | `infra/terraform/envs/` |

El backend remoto usa MinIO `:9002` y DynamoDB Local `:8000` del Compose `cg-tools`.
Cada ambiente guarda su estado en `platform/<env>/terraform.tfstate` y usa la tabla
`LockID` para bloquear aplicaciones concurrentes. El bootstrap `state-backend/` mantiene
estado local (es el caso huevo-huevo: el bucket no puede alojar el estado que lo crea) y
está ignorado por git.

El pipeline de Jenkins ejecuta `init`, `validate`, `plan`, escaneo Trivy de IaC y
`apply` por ambiente, con aprobación manual antes de producción (E2.3). Comandos,
credenciales y precauciones en
[`docs/infra/runbook.md`](../docs/infra/runbook.md) → «Terraform: plataforma y backend».

## Legado AWS ( Parte 1 — histórico, no operativo )

Los scripts `infra/bootstrap/*` (migración a AWS) y su estado (`infra/state/`) se conservan **solo como evidencia** de la entrega Parte 1. No se ejecutan, no se operan y no se documentan como flujo vigente; si encuentras referencias a ellos fuera de `docs/informe/` o `CHANGELOG.md`, repórtalas.

- Resumen histórico: `docs/infra/ci-cd-setup.md` (sección marcada "Parte 1 — histórico"), `docs/infra/runbook.md`, `docs/infra/environments.md`.
- Informe y evidencias: `docs/informe/`.
- Anejo de migración: `docs/infra/migracion-onpremise.md`.
- Costo de referencia AWS: `docs/infra/aws-free-tier-validation.md`.
