<!-- CG-DOC: Deployment layout — on-prem (compose cg-tools + k3s/k3d namespaces). -->
# Deployment

## Purpose

The `deploy/` directory defines how the platform runs **on-premise**:

- `compose/tools/`: Jenkins, SonarQube, PostgreSQL and Nexus as the `cg-tools`
  Compose project (`docker-compose.onpremise.yml`, documented in
  `infra/onpremise/README.md` and `docs/infra/migracion-onpremise.md`).
- `k8s/`: Kustomize manifests for the `cg` k3d cluster. Namespaces `cg-dev`,
  `cg-stage`, `cg-prod` host the eight services, the web client and their
  middleware (PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP), an Traefik `Ingress` on
  `:30080` matched by the `Host` header, plus HPA for `gateway`, `web` and `auth`.
- `compose/postgres/init-databases.sh`: creates the per-service databases in the
  shared PostgreSQL instance (legacy compose stacks).
- `scripts/`: deploy, rollback, seed, and smoke-test operations (**legacy Part 1
  flows**, kept because `ci.groovy`'s deploy path was rewritten to kubectl/Kustomize).

`notification`, `file` and `dashboard` are built, tested, packaged and published
by the pipeline, and they are deployed in Part 2 as part of the on-prem cluster.

## Environment isolation (on-premise)

The three environments are **namespaces of one k3d cluster** (`cg`), not separate
compose stacks:

| Environment | Namespace | Host | Project |
|---|---|---:|---|
| `dev` | `cg-dev` | `dev.circleguard.local` | Kustomize overlay `deploy/k8s/overlays/dev` |
| `stage` | `cg-stage` | `stage.circleguard.local` | Kustomize overlay `deploy/k8s/overlays/stage` |
| `prod` | `cg-prod` | `prod.circleguard.local` | Kustomize overlay `deploy/k8s/overlays/prod` |

Each overlay sets its namespace, the `Host` of its Ingress and the `newTag` of the
eight service images and `web`. `kubectl apply -k deploy/k8s/overlays/<env>` is
idempotent and creates the namespace if missing.

The legacy compose stacks under `compose/{dev,stage,prod}/` remain in the tree as
the Part 1 reference (they defined the same services for single-machine deploys);
they are no longer the delivery target and are not documented as the live platform.

## Images

The pipeline publishes semantic-versioned images to the on-prem HTTP registry:

```text
10.147.20.67:5000/circleguard-docker/{gateway,auth,identity,promotion,form,notification,file,dashboard,web}:<TAG>
```

The cluster resolves the `circleguard-docker/<svc>:<TAG>` names from Docker because
the pipeline runs `k3d image import -c cg` after each build, so overlays don't
change `newName`. If the track `"imagePullSecrets → 10.147.20.67:5000"` is later
preferred, update `kustomization.yml` files to use `newName: 10.147.20.67:5000/circleguard-docker/...`
(documented in `deploy/k8s/README.md`).

## Pipelines use `kubectl` + `kustomize`, not ssh

The on-prem `deploy` uses Kustomize from the CI workspace:

```bash
cd deploy/k8s/overlays/<env>
kustomize edit set image circleguard-docker/<svc>=circleguard-docker/<svc>:<TAG>   # for each of the nine images
kubectl --namespace cg-<env> apply -k .
for svc in ...; do k3d image import -c cg circleguard-docker/<svc>:<TAG>; done
kubectl --namespace cg-<env> rollout status deployment/<svc> --timeout=180s
```

The Jenkins image carries `kubectl` v1.31.2, `kustomize` v5.4.3 and `k3d` v5.9.0
(pinned in `deploy/compose/tools/jenkins/Dockerfile`) and reads the kubeconfig
read-only from `${HOME}/.kube/config` (mounted at `/var/jenkins_home/.kube/config`).
No `sshagent`, no SSH key, no credentials for AWS hosts are used anywhere in the
pipeline or in JCasC.

## Scripts

| Script | Responsibility |
|---|---|
| `deploy.sh` | **Legacy Part 1**: pulled the requested tag over ssh to the AWS hosts. Superseded by `ci.deploy()` (kubectl/kustomize) on-prem. |
| `rollback.sh` | **Legacy Part 1**: re-deployed a previous tag. On-prem equivalent: re-run the pipeline with `ROLLBACK_TAG`; `ci.deploy('prod', ROLLBACK_TAG)` re-applies the overlay with that tag and rolls out. |
| `seed-status.sh` | Legacy: wrote `user:status:<anonymousId>` in Redis. Part 2 seeding uses `kubectl exec -n cg-dev deploy/neo4j -- cypher-shell`. |
| `smoke-test.sh` | Legacy: checks health and the unauthenticated validation response (used over ssh). Superseded by `verify-onpremise.sh` and by the E2E suite in `tests/e2e/`. |

## Required environment variables (host)

- `TAG`: image tag.
- `NEXUS_REGISTRY`: `10.147.20.67:5000`.
- `POSTGRES_PASSWORD`, `NEO4J_PASSWORD`, `OPENLDAP_ADMIN_PASSWORD`.
- `QR_SECRET`, `JWT_SECRET`, `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`.

## Jenkins interface

The pipeline calls (`jenkins/vars/ci.groovy:deploy`):

```bash
# 1) pull the freshly published tag from the host registry, then import into k3d
docker pull "${NEXUS_REGISTRY}/circleguard-docker/<svc>:${TAG}"
docker tag  "${NEXUS_REGISTRY}/circleguard-docker/<svc>:${TAG}" "circleguard-docker/<svc>:${TAG}"
k3d image import -c cg "circleguard-docker/<svc>:${TAG}"
# 2) set the newTag in the overlay inside the CI workspace (nothing is committed)
cd deploy/k8s/overlays/<env> && kustomize edit set image "circleguard-docker/<svc>=circleguard-docker/<svc>:${TAG}"
kubectl --namespace cg-<env> apply -k .
kubectl --namespace cg-<env> rollout status deployment/<svc> --timeout=180s
```

with the kubeconfig read-only-mounted from `${HOME}/.kube/config` and the image
list provided by `kustomize edit set image` inside the CI workspace. No SSH key,
no `cg-envs-ssh` credential. Images land in the k3d nodes because the manifests
use `imagePullPolicy: IfNotPresent` with plain in-cluster image names; the
registry over HTTP only needs to serve the CI host's own dockerd.

Kubernetes secrets live in `deploy/k8s/overlays/*/secrets.env` (not versioned);
the pipeline does not touch them.
