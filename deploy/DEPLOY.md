<!-- CG-DOC: Estructura de despliegue — on-prem (compose cg-tools + namespaces k3s/k3d). -->
# Despliegue

## Propósito

El directorio `deploy/` define cómo se ejecuta la plataforma **on-premise**:

- `compose/tools/`: Jenkins, SonarQube, PostgreSQL y Nexus como el proyecto
  Compose `cg-tools` (`docker-compose.onpremise.yml`, documentado en
  `infra/onpremise/ONPREMISE.md` y `docs/infra/migracion-onpremise.md`).
- `k8s/`: manifiestos Kustomize para el clúster k3d `cg`. Los namespaces `cg-dev`,
  `cg-stage` y `cg-prod` alojan los ocho servicios, el cliente web y su
  middleware (PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP), un `Ingress` de Traefik en
  `:30080` enrutado por la cabecera `Host`, además de HPA para `gateway`, `web` y `auth`.
- `compose/postgres/init-databases.sh`: crea las bases de datos por servicio en la
  instancia compartida de PostgreSQL (stacks compose heredados).
- `scripts/`: operaciones de despliegue, reversión, carga de datos y pruebas de humo
  (**flujos heredados de la Parte 1**, conservados porque la ruta de despliegue de `ci.groovy` se reescribió con kubectl/Kustomize).

`notification`, `file` y `dashboard` son compilados, probados, empaquetados y publicados
por la canalización, y se despliegan en la Parte 2 como parte del clúster on-prem.

## Aislamiento de entornos (on-premise)

Los tres entornos son **namespaces de un único clúster k3d** (`cg`), no stacks compose
independientes:

| Entorno | Namespace | Host | Proyecto |
|---|---|---:|---|
| `dev` | `cg-dev` | `dev.circleguard.local` | overlay de Kustomize `deploy/k8s/overlays/dev` |
| `stage` | `cg-stage` | `stage.circleguard.local` | overlay de Kustomize `deploy/k8s/overlays/stage` |
| `prod` | `cg-prod` | `prod.circleguard.local` | overlay de Kustomize `deploy/k8s/overlays/prod` |

Cada overlay define su namespace, el `Host` de su Ingress y el `newTag` de
las ocho imágenes de servicio y `web`. `kubectl apply -k deploy/k8s/overlays/<env>` es
idempotente y crea el namespace si no existe.

Los stacks compose heredados en `compose/{dev,stage,prod}/` permanecen en el árbol como
referencia de la Parte 1 (definían los mismos servicios para despliegues en una sola máquina);
ya no son el destino de despliegue y no se documentan como la plataforma vigente.

## Imágenes

La canalización publica imágenes con versionado semántico en el registro HTTP on-prem:

```text
10.147.20.67:5000/circleguard-docker/{gateway,auth,identity,promotion,form,notification,file,dashboard,web}:<TAG>
```

El clúster resuelve los nombres `circleguard-docker/<svc>:<TAG>` desde Docker porque
la canalización ejecuta `k3d image import -c cg` tras cada compilación, de modo que los overlays no
cambian `newName`. Si más adelante se prefiere la vía `"imagePullSecrets → 10.147.20.67:5000"`,
actualice los archivos `kustomization.yml` para usar `newName: 10.147.20.67:5000/circleguard-docker/...`
(documentado en `deploy/k8s/K8S.md`).

## Las canalizaciones usan `kubectl` + `kustomize`, no ssh

El despliegue on-prem `deploy` usa Kustomize desde el espacio de trabajo de CI:

```bash
cd deploy/k8s/overlays/<env>
kustomize edit set image circleguard-docker/<svc>=circleguard-docker/<svc>:<TAG>   # for each of the nine images
kubectl --namespace cg-<env> apply -k .
for svc in ...; do k3d image import -c cg circleguard-docker/<svc>:<TAG>; done
kubectl --namespace cg-<env> rollout status deployment/<svc> --timeout=180s
```

La imagen de Jenkins incluye `kubectl` v1.31.2, `kustomize` v5.4.3 y `k3d` v5.9.0
(fijados en `deploy/compose/tools/jenkins/Dockerfile`) y lee el kubeconfig
en modo solo lectura desde `${HOME}/.kube/config` (montado en `/var/jenkins_home/.kube/config`).
No se usa `sshagent`, ni clave SSH, ni credenciales de hosts de AWS en ningún punto de la
canalización ni en JCasC.

## Scripts

| Script | Responsabilidad |
|---|---|
| `deploy.sh` | **Heredado de la Parte 1**: obtenía el tag solicitado por ssh en los hosts de AWS. Sustituido por `ci.deploy()` (kubectl/kustomize) on-prem. |
| `rollback.sh` | **Heredado de la Parte 1**: volvía a desplegar un tag anterior. Equivalente on-prem: volver a ejecutar la canalización con `ROLLBACK_TAG`; `ci.deploy('prod', ROLLBACK_TAG)` vuelve a aplicar el overlay con ese tag y realiza el rollout. |
| `seed-status.sh` | Heredado: escribía `user:status:<anonymousId>` en Redis. La carga de datos de la Parte 2 usa `kubectl exec -n cg-dev deploy/neo4j -- cypher-shell`. |
| `smoke-test.sh` | Heredado: comprobaba la salud y la respuesta de validación sin autenticación (se usaba por ssh). Sustituido por `verify-onpremise.sh` y por la suite E2E en `tests/e2e/`. |

## Variables de entorno requeridas (host)

- `TAG`: tag de la imagen.
- `NEXUS_REGISTRY`: `10.147.20.67:5000`.
- `POSTGRES_PASSWORD`, `NEO4J_PASSWORD`, `OPENLDAP_ADMIN_PASSWORD`.
- `QR_SECRET`, `JWT_SECRET`, `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`.

## Interfaz de Jenkins

La canalización invoca (`jenkins/vars/ci.groovy:deploy`):

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

con el kubeconfig montado en modo solo lectura desde `${HOME}/.kube/config` y la lista
de imágenes proporcionada por `kustomize edit set image` dentro del espacio de trabajo de CI. Sin clave SSH,
sin credencial `cg-envs-ssh`. Las imágenes llegan a los nodos k3d porque los manifiestos
usan `imagePullPolicy: IfNotPresent` con nombres de imagen planos del clúster; el
registro por HTTP solo debe atender al dockerd del propio host de CI.

Los secrets de Kubernetes están en `deploy/k8s/overlays/*/secrets.env` (sin versionar);
la canalización no los modifica.
