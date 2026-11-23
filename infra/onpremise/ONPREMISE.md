<!-- CG-DOC: On-premise platform guide (host → compose cg-tools → k3d `cg` → verify). -->
# Infraestructura on-premise de CircleGuard

Esta carpeta es la **fuente operativa** del proyecto. Describe cómo levantar la plataforma completa
en el host on-prem (sin `sudo`, sin exponer puertos ni salir de ZeroTier) y qué archivos la definen.
Si el equipo sufre un reinicio total o el clúster se pierde, este es el orden para reconstruirla.

## Topología

```
host Ubuntu 24.04 (ZeroTier 10.147.20.67, usuario `docker` sin sudo)
├── Docker Compose `cg-tools`             → deploy/compose/tools/docker-compose.onpremise.yml
│     Jenkins :8080 · SonarQube :9000 · Nexus :8081 · registro circleguard-docker :5000 · smee-client
└── clúster k3d `cg`                      → deploy/k8s (Kustomize)
      namespaces cg-dev · cg-stage · cg-prod
      PostgreSQL · Neo4j · Kafka · Redis · OpenLDAP · 8 servicios · web · Ingress Traefik :30080 (Host)
```

Acceso: Jenkins/SonarQube/Nexus/registro por ZeroTier; las aplicaciones por
`curl -H "Host: <env>.circleguard.local" http://localhost:30080/`. Detalles y decisiones:
[`docs/infra/migracion-onpremise.md`](../../docs/infra/migracion-onpremise.md) y
[`deploy/k8s/K8S.md`](../../deploy/k8s/K8S.md).

## Requisitos

- Docker Engine accesible por el usuario `docker` (el host es compartido; sin `sudo`).
- ZeroTier instalado y el host unido a la red del equipo.
- Estos binarios en `PATH` del host (Jenkins los lleva **dentro** de su imagen: kubectl,
  kustomize, k3d — ver `deploy/compose/tools/jenkins/Dockerfile`):
  `docker`, `docker compose`, `k3d` (≥ v5.9.0), `kubectl`, `kustomize`, `git`, `curl`, `jq`.
- Un clon del repo (vía SSH o HTTPS) en el home del usuario `docker`.
- Credenciales:
  - **GitHub**: personal (uno de los cuatro integrantes) para Jenkins.
  - **`GITHUB_WEBHOOK_SECRET`**: el del hook `709307452` (no se puede leer, tiene que
    coincidir con el del hook; se pide una sola vez por bootstrap).
  - **`SONAR_TOKEN`**: creado **dentro** de SonarQube; ver `docs/infra/migracion-onpremise.md` §3.6.1.

## Orden de ejecución

```bash
git clone https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git
cd circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013

# 1) Herramientas: crea .env.onpremise + pide los secretos que no puede inventar
./infra/onpremise/bootstrap-onpremise.sh
#    El secreto del webhook y el SONAR_TOKEN NUNCA se generan al azar:
#    GITHUB_WEBHOOK_SECRET=<secreto> SONAR_TOKEN=squ_... ./infra/onpremise/bootstrap-onpremise.sh

# 2) Clúster y namespaces (crea cg si falta y copia ~/.config/k3d/registries.yaml)
./infra/onpremise/k3d-cluster-create.sh

# 3) Herramientas arriba; Jenkins lee su config desde deploy/compose/tools/jenkins/cac/jenkins.yml
#    Si el host tiene kubectl de k3d: ~/.kube/config existe tras el paso 2 (usa `k3d kubeconfig merge cg`)
docker compose --env-file deploy/compose/tools/.env.onpremise \
  -f deploy/compose/tools/docker-compose.onpremise.yml -p cg-tools up -d --build

# 4) Plataforma por ambiente (crea el namespace si falta; `kubectl apply` es idempotente)
kubectl apply -k deploy/k8s/overlays/dev
#    o stage / prod

# 5) Verificación (read-only)
./infra/onpremise/verify-onpremise.sh
```

Notas:

- `k3d kubeconfig merge cg --kubeconfig-override cg --kubeconfig-switch-context` si tu
  `~/.kube/config` no apunta al clúster `cg`; Jenkins lo monta read-only para desplegar y hacer `rollout`.
- Imágenes: Jenkins publica el build en Nexus (`10.147.20.67:5000/circleguard-docker/<svc>:<TAG>`)
  y el pipeline hace `k3d image import -c cg` antes del deploy con Kustomize (ver
  `docs/infra/migracion-onpremise.md`). Mientras tanto, la importación manual funciona:

  ```bash
  TAG=1.4.0
  k3d image import -c cg \
    circleguard-docker/{auth,identity,promotion,form,gateway,dashboard,file,notification,web}:${TAG}
  ```

- Máximo dos entornos completos encendidos a la vez (RAM del host; ver `deploy/k8s/K8S.md`).

## Imágenes base desde Nexus (sin Docker Hub)

El build de `main` ya no descarga `gradle`, `eclipse-temurin`, `node` ni `nginx` de Docker Hub: el host compartido no tiene ruta IPv6 hacia el CDN de Docker. Las imágenes base se siembran una vez en `circleguard-base/` y los Dockerfiles reciben `ARG` desde el pipeline:

```bash
./infra/onpremise/seed-base-images.sh
```

El script lee `NEXUS_REGISTRY`, `NEXUS_USER` y `NEXUS_PASSWORD` de `deploy/compose/tools/.env.onpremise`, hace pull por IPv4, etiqueta como `$NEXUS_REGISTRY/circleguard-base/<nombre>:<tag>` y hace push. Verificar con:

```bash
curl -sS "http://$NEXUS_REGISTRY/v2/_catalog" | jq -r '.repositories[]' | grep circleguard-base
```

Si la etapa `Docker` falla con `network is unreachable` hacia `production.cloudfront.docker.com`, faltó este paso. El pin por digest de `circleguard-form-service` se retiró porque el mirror publica sus propios digests.

## Qué verificar al final

`./infra/onpremise/verify-onpremise.sh` cubre, en orden:

1. Jenkins `/login` (HTTP 200) y estado de arranque.
2. SonarQube `api/system/status` (`UP`) y API autenticada con `$SONAR_TOKEN` (no `Not authorized`).
3. Nexus `service/rest/v1/status` (HTTP 200) y registro `v2/_catalog` con las imágenes `circleguard-docker/*`.
4. Clúster: `k3d cluster list` con `cg`, `kubectl get ns` con `cg-dev|cg-stage|cg-prod`.
5. Pods `Ready` en los namespaces activos (`kubectl get pods -n <ns>`).
6. Ingress: `curl -H "Host: <env>.circleguard.local" http://localhost:30080/` devuelve 200.
7. Login JWT de demostración, solo si `DEMO_USER`/`DEMO_PASSWORD` están definidos
   (el script los lee de `${SEED_ENV:-.env.onpremise}`; no crea ni borra nada).

## Reglas del host compartido

1. No tocar `k8s-worker` ni `icesi-inn` (son de otros proyectos del equipo).
2. Sin `docker prune`.
3. Sin `docker compose down -v` ni borrado de volúmenes (bases de datos).
4. Secretos: `.env.onpremise` y los `Secret` del clúster; nunca en el repo.
5. El webhook y el clúster se administran a mano a propósito; `manageHooks: false`.

## Historial

La migración desde AWS y su evidencia (cuenta bloqueada, `bootstrap-*`, costo de referencia)
están en [`docs/infra/migracion-onpremise.md`](../../docs/infra/migracion-onpremise.md) y en
`docs/informe/`. Los scripts `infra/bootstrap/*` y `infra/state/*` **no se ejecutan ni se
mantienen**; se conservan solo como parte de la evidencia de la entrega.
