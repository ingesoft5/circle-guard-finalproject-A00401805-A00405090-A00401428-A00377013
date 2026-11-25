<!-- CG-DOC: Manual de operaciones (runbook). -->
# Manual de operaciones (runbook)

## Parte 2 — Kubernetes on-premise (vigente)

### Datos del host

| Elemento | Valor |
|---|---|
| Host | `docker@205m07`, Ubuntu 24.04.5, 8 vCPU, 31 GiB de RAM |
| Dirección privada | `10.147.20.67` (ZeroTier) |
| Usuario de operación | `docker`, sin `sudo` |
| Clúster | k3d `cg` (k3d v5.9.0, k3s v1.35.5) |
| Ingress | Traefik publicado en el puerto `30080` del host |
| Herramientas | Jenkins `:8080`, SonarQube `:9000`, Nexus `:8081`, registro `:5000` |

### Arranque del clúster

```bash
infra/onpremise/k3d-cluster-create.sh
```

Copia `infra/onpremise/registries.yaml` a `~/.config/k3d/registries.yaml` si falta y crea el
clúster `cg` con Traefik publicado en `:30080`. Tras un reinicio de Docker, volver a levantar
con `k3d cluster start cg`.

### Verificar las herramientas

Versión rápida y read-only: `infra/onpremise/verify-onpremise.sh` (imprime OK/FALLA por cheque y sale !=0 si alguno falla). Comprobación manual:

```bash
curl -f http://localhost:8080/login
curl -f http://localhost:9000/api/system/status
curl -f http://localhost:8081/service/rest/v1/status
curl -f http://localhost:5000/v2/_catalog
```

### Verificar el clúster y los entornos

```bash
k3d cluster list
kubectl get ns
kubectl -n cg-dev get pods
kubectl -n cg-stage get pods
```

Cada entorno ejecuta 14 pods: los ocho servicios y el cliente web, más PostgreSQL, Redis,
Neo4j, Kafka y OpenLDAP.

### Habilitar el autoscaling (una sola vez por clúster)

`metrics-server` no viene con k3d y sin él los HPA quedan en `<unknown>`:

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl top nodes
kubectl -n cg-dev get hpa
```

`--kubelet-insecure-tls` es obligatorio porque los certificados de k3s son autofirmados.

### Levantar un entorno

```bash
cp deploy/k8s/overlays/dev/secrets.env deploy/k8s/overlays/stage/secrets.env
chmod 600 deploy/k8s/overlays/stage/secrets.env
kubectl apply -k deploy/k8s/overlays/stage     # también crea el namespace si falta
```

Reutilizar los valores validados de `dev` evita los fallos de formato de los secretos que se
listan más abajo. Para el informe esto queda declarado como deuda: los entornos de producción
deben usar secretos distintos.

### Arranque en frío: `CrashLoopBackOff` esperado

Un `apply` nuevo levanta los 14 pods al mismo tiempo. PostgreSQL necesita tiempo para crear
sus cinco bases y las aplicaciones usan Flyway con `failFast`, así que se caen hasta que
Postgres esté `Ready`. Es el orden de arranque, no un defecto del manifiesto:

```bash
kubectl -n cg-stage wait --for=condition=Ready pod/postgres-0 --timeout=300s
kubectl -n cg-stage rollout restart deployment/auth deployment/identity \
  deployment/dashboard deployment/form deployment/promotion
kubectl -n cg-stage get pods
```

### Verificar un entorno

```bash
curl -o /dev/null -s -w "web=%{http_code}\n" \
  -H "Host: stage.circleguard.local" http://localhost:30080/

curl -s -X POST -H "Host: stage.circleguard.local" \
  -H "Content-Type: application/json" \
  -d '{"username":"super_admin","password":"password"}' \
  http://localhost:30080/api/v1/auth/login
```

El campo de login es `username`, no `email`. Los usuarios semilla de desarrollo
(`super_admin`, `staff_guard`, `health_user`, contraseña `password`) vienen de
`V2__seed_test_users.sql` y no deben llegar a producción.

### Verificar el escalado

```bash
kubectl -n cg-dev get hpa
kubectl -n cg-dev top pods
kubectl -n cg-dev run carga --image=busybox --restart=Never -- \
  sh -c 'while true; do wget -q -O- http://gateway:8087/actuator/health; done'
kubectl -n cg-dev get hpa -w          # gateway debe pasar de 1 a 2-3 réplicas
kubectl -n cg-dev delete pod carga
```

Los HPA son de `gateway` (1-3), `web` (1-2) y `auth` (1-2), con objetivo de CPU al 70 %.
Los Deployments escalados no declaran `replicas`: el dueño es el HPA.

### Reiniciar una aplicación

```bash
kubectl -n cg-dev rollout restart deployment/gateway
kubectl -n cg-dev rollout status deployment/gateway
```

### Revertir un despliegue

```bash
kubectl -n cg-prod rollout undo deployment/auth
kubectl -n cg-prod rollout status deployment/auth
```

Las imágenes son inmutables y los PVC no se tocan, así que revertir solo cambia la imagen de
la aplicación.

### Capturar evidencia

```bash
mkdir -p docs/informe/evidencia
kubectl -n cg-dev get pods > docs/informe/evidencia/k8s-dev-pods.txt
kubectl -n cg-stage get pods > docs/informe/evidencia/k8s-stage-pods.txt
kubectl -n cg-prod get pods > docs/informe/evidencia/k8s-prod-pods.txt
curl -o /dev/null -s -w "web=%{http_code}\n" \
  -H "Host: dev.circleguard.local" http://localhost:30080/ \
  > docs/informe/evidencia/k8s-dev-ingress.txt
```

Recorta los JWT (`eyJ...`) de cualquier captura o log antes de versionarlo.

### Bajar un entorno

```bash
kubectl delete namespace cg-stage
```

Elimina las cargas de trabajo y los volúmenes declarados en los manifiestos. No borres
`cg-dev` durante la demostración.

### Ver qué se está comiendo la memoria

Con los tres entornos arriba el host queda al límite, porque además corren Jenkins, SonarQube
y Nexus. Cuando un pod muere por `OOMKilled`:

```bash
free -h
kubectl -n cg-dev get pods -o custom-columns=NAME:.metadata.name,RESTARTS:.status.containerStatuses[0].restartCount
kubectl -n cg-stage scale deployment,statefulset --all --replicas=0    # libera el entorno que no se está demostrando
```

Volver a levantarlo:

```bash
kubectl -n cg-stage scale deployment,statefulset --all --replicas=1
kubectl -n cg-stage wait --for=condition=Ready pod/postgres-0 --timeout=300s
kubectl -n cg-stage rollout restart deployment/auth deployment/identity \
  deployment/dashboard deployment/form deployment/promotion
```

### Límites operativos conocidos

- No tocar los contenedores ajenos `k8s-worker` e `icesi-inn`.
- Nunca ejecutar `docker prune`.
- Nunca ejecutar `docker compose down -v`: los volúmenes guardan el estado de Jenkins y SonarQube.
- Mantener `secrets.env` fuera de git; `.gitignore` cubre `deploy/k8s/overlays/*/secrets.env`.
- El clúster depende de Docker: si se reinicia el servicio, usar `k3d cluster start cg`.
- Jenkins: 4 executors y `mem_limit: 8g` — los builds de gradle/sonar/trivy corren dentro del mismo cgroup del controller; revisar `free -h` antes de recrear el contenedor.
- Recrear Jenkins solo con `docker compose -p cg-tools -f deploy/compose/tools/docker-compose.onpremise.yml --env-file deploy/compose/tools/.env.onpremise up -d jenkins`, en ventana quieta y tras `git pull --ff-only`: el CASC va bind-mounted (sin pull no suben los executors) y un `-p` distinto duplicaría el contenedor.

### Terraform: plataforma y backend (E2.1)

Terraform declara la infraestructura completa con módulos reutilizables
(`network`, `compute`, `registry`, `storage`, `observability`) y tres ambientes
parametrizados (`dev`, `stage`, `prod`). El backend remoto es MinIO `:9002` +
DynamoDB Local `:8000`, levantados por `cg-tools`.

#### 1. Bootstrap del backend (una sola vez)

`infra/terraform/state-backend/` crea el bucket `circleguard-tfstate` con versioning y la
tabla `circleguard-tf-lock` con `hash_key = "LockID"`. Su estado es local y está ignorado
por git (el bucket no puede guardar el estado que lo crea).

```bash
set -a
source deploy/compose/tools/.env.onpremise
set +a
export TF_VAR_minio_access_key="$TF_STATE_MINIO_USER"
export TF_VAR_minio_secret_key="$TF_STATE_MINIO_PASSWORD"
export TF_VAR_state_bucket="$TF_STATE_BUCKET"
export TF_VAR_lock_table="$TF_LOCK_TABLE"
export TF_VAR_aws_region="$AWS_REGION"
export TF_VAR_s3_endpoint="$TF_MINIO_ENDPOINT"
export TF_VAR_dynamodb_endpoint="$TF_DYNAMODB_ENDPOINT"

cd infra/terraform/state-backend
terraform init
terraform validate
terraform plan
terraform apply
```

Sin salida a internet: `terraform init -plugin-dir=../bootstrap/.terraform/providers`.
Si el bucket/tabla ya existen del bootstrap anterior, impórtalos antes de aplicar:

```bash
terraform import aws_s3_bucket.tfstate "$TF_STATE_BUCKET"
terraform import aws_s3_bucket_versioning.tfstate "$TF_STATE_BUCKET"
terraform import aws_dynamodb_table.state_lock "$TF_LOCK_TABLE"
```

#### 2. Ambientes (dev, stage, prod)

Cada entorno tiene `backend.hcl.example` con ambos endpoints y su clave
`platform/<env>/terraform.tfstate`. Los secretos se inyectan por `TF_VAR_*` y las
credenciales del backend por `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`.

```bash
set -a
source deploy/compose/tools/.env.onpremise
set +a
export AWS_ACCESS_KEY_ID="$TF_STATE_MINIO_USER"
export AWS_SECRET_ACCESS_KEY="$TF_STATE_MINIO_PASSWORD"
export TF_VAR_minio_access_key="$TF_STATE_MINIO_USER"
export TF_VAR_minio_secret_key="$TF_STATE_MINIO_PASSWORD"
export TF_VAR_grafana_admin_password="$TF_GRAFANA_ADMIN_PASSWORD"
export TF_VAR_kubeconfig="$HOME/.kube/config"

for env in dev stage prod; do
  cd "$OLDPWD/infra/terraform/envs/$env"
  cp -f backend.hcl.example backend.hcl
  terraform init -backend-config=backend.hcl
  terraform validate
  # El provider de Kubernetes necesita el contexto del clúster antes del plan completo.
  terraform apply -auto-approve -target=module.compute
  terraform plan -detailed-exitcode
done
```

`plan -detailed-exitcode`: `0` = sin cambios, `2` = cambios detectados, `1` = error.
Para aplicar un ambiente: `terraform apply` dentro de `infra/terraform/envs/<env>`.

Notas de observabilidad en el host on-prem:

- `node_exporter_enabled` debe ser `true` en un solo ambiente del clúster compartido: el
  DaemonSet fija `hostPort 9100` y un segundo ambiente deja los pods `Pending`.
- `loki.isDefault=false` es obligatorio porque `kube-prometheus-stack` ya registra
  Prometheus como datasource `default`; dos defaults impiden que Grafana arranque.
- `grafana_slow_start=true` (usado en prod) relaja `livenessProbe.initialDelaySeconds`
  a 600 y los umbrales de fallo de las sondas. En este host las migraciones SQLite de
  Grafana pueden tardar ~10 min y la sonda por defecto reinicia el contenedor a mitad
  de la migración, dejando el `helm_release` en `pending-install`.

El apply es **en dos fases**: primero `terraform apply -target=module.compute` verifica el
clúster compartido `cg` y su kubeconfig; después el plan/apply completo puede cargar el
contexto `k3d-cg`. Si se omite la primera fase, el plan falla con
`context "k3d-cg" does not exist`. No se crean clústeres por ambiente: los tres ambientes
son namespaces `cg-dev`, `cg-stage` y `cg-prod` dentro de `cg`, con Traefik `:30080` y
ruteo por header `Host`. Esto evita el conflicto del puerto `30080` y el límite
`fs.inotify.max_user_instances=128` del host.

#### 3. Pipeline (E2.3)

Jenkins ejecuta `init → validate → verificación compute → plan → Trivy IaC → apply` para los tres ambientes, con
`Approval prod` (aprobación automática de compuertas) antes del apply de producción. Las credenciales se leen de los credentials
`terraform-minio-creds` y `terraform-grafana-password`, definidos en `jenkins/cac/jenkins.yml`
y alimentados por `.env.onpremise`.

La checklist de evidencia y las validaciones estáticas están en
[`docs/infra/evidencia/E2.1-terraform.md`](evidencia/E2.1-terraform.md).

#### 4. Verificación y precauciones

```bash
terraform fmt -check -recursive infra/terraform
terraform plan -detailed-exitcode   # 0 = idempotente
terraform output
```

> `dynamodb-local` corre con `-inMemory`: si el contenedor se reinicia, la tabla desaparece y
> hay que repetir el apply del bootstrap antes de usar los ambientes. Los providers pueden
> reutilizarse desde `infra/terraform/bootstrap/.terraform/providers` con `-plugin-dir` si no
> hay internet. No commitear `backend.hcl`, `*.tfstate` ni `.terraform/`.

## Parte 1 — Hosts de AWS (evidencia histórica)

Estos comandos documentan la infraestructura original del pipeline. La cuenta de AWS ya no es
accesible, así que se conservan para la trazabilidad del entregable de la Parte 1, no para
operar.

### Verificar

```bash
source infra/state/aws.env
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
curl -f "http://${TOOLS_IP}/actuator/health"
```

### Direcciones IP

```bash
./infra/bootstrap/bootstrap-ips.sh
```

### Detener y arrancar

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
```

Las IP privadas se mantienen; las públicas de los entornos cambian tras un reinicio.

### Reiniciar las herramientas

```bash
ssh -i infra/state/circleguard-e12-key.pem ubuntu@${TOOLS_IP}
cd /opt/circleguard/deploy/compose/tools
docker compose --env-file .env -p circleguard-tools restart jenkins
```

### Reiniciar un entorno

```bash
ssh -i infra/state/circleguard-e12-key.pem ubuntu@${DEV_PUBLIC_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose restart gateway
```

### Actualizar los scripts de despliegue en un host

El pipeline ejecuta `deploy/scripts/*.sh` desde `/opt/circleguard` en el host destino, así que
un cambio en esos scripts solo aplica después de copiarlos:

```bash
source infra/state/aws.env
tar -C . -czf - infra deploy | ssh -i infra/state/circleguard-e12-key.pem \
  ubuntu@${DEV_PUBLIC_IP} "tar -xzf - -C /opt/circleguard"
```

### Inspeccionar el stack de promoción

```bash
ssh -i infra/state/circleguard-e12-key.pem ubuntu@${DEV_PUBLIC_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose ps
docker compose logs --tail=100 promotion
curl -fsS http://localhost:8088/actuator/health
curl -fsS http://localhost:8088/api/v1/health-status/stats
```

### Sembrar un visitante

`promotion-service` actualiza visitantes por `anonymousId` y nunca los crea, así que un
`anonymousId` nuevo necesita primero el nodo en el grafo. La suite de extremo a extremo lo
hace antes de cada ejecución:

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/seed-visitor.sh dev e2e-123-symptoms
```

### Revertir (Compose)

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <tag-anterior>
```

La reversión redespliega el tag anterior sobre todo el stack. Los volúmenes del middleware no
se tocan, así que la base y el grafo de promoción sobreviven; solo cambian las dos imágenes de
aplicación.

### Destruir

```bash
CONFIRM=yes ./infra/bootstrap/bootstrap-destroy.sh
```

## Problemas frecuentes

| Síntoma | Causa | Acción |
|---|---|---|
| `WeakKeyException ... 128 bits` (`auth`) | `QR_SECRET` de menos de 32 bytes | `openssl rand -hex 24` en `secrets.env` y re-aplicar |
| `Detected a Non-hex character at 1 or 2 position` (`identity`) | `VAULT_SALT` no es hexadecimal | `openssl rand -hex 16` (hex de longitud par) |
| `Unrecognized setting ... PORT.7687.TCP.PORT` (`neo4j-0`) | Las *service links* de Kubernetes inyectan variables `NEO4J_*` y la imagen las lee como configuración | `enableServiceLinks: false` en el StatefulSet |
| `sin registro en el quórum del controlador` (`kafka-0`) | El quórum apunta al `Service`, cuyos endpoints solo existen cuando el pod está `Ready` | `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093` |
| `sin conexión a localhost:7687` (`promotion`) | No está definido `SPRING_NEO4J_URI` y Spring usa `localhost` por defecto | Clave `SPRING_NEO4J_URI` en el ConfigMap `cg-config` |
| `Connection refused` a PostgreSQL | Arranque en frío: Flyway `failFast` antes de que Postgres esté `Ready` | Esperar `postgres-0` y reiniciar las aplicaciones |
| `OOMKilled` (salida 137) en varios pods | Los tres entornos completos no caben en la RAM del host | Bajar el entorno que no se está demostrando |
| `404 page not found` de Traefik | El header `Host` no coincide con la regla del Ingress | Usar `curl -H "Host: dev.circleguard.local"` |
| `Kafka` tarda y los consumidores reconectan | Readiness `tcpSocket` mientras arranca el broker | Esperar 1-2 minutos; es transitorio |
| `WeakKeyException` en un host Compose (Parte 1) | `JWT_SECRET` de menos de 32 bytes | Regenerarlo con `openssl rand -hex 24` en `.env` y redesplegar |
| Neo4j reinicia en un host Compose (Parte 1) | Una variable `NEO4J_*` extra llegó al contenedor | Mantener la contraseña dentro de `NEO4J_AUTH` |
| El cuestionario nunca cambia de estado (Parte 1) | El consumidor de Kafka rechaza todos los registros | Mantener `spring.json.value.default.type` en `application.yml` |
| Un visitante nuevo nunca cambia de estado (Parte 1) | El `anonymousId` no tiene nodo en Neo4j | Ejecutar `deploy/scripts/seed-visitor.sh` primero |
| `deploy.sh` falla en `--wait` (Parte 1) | Un contenedor nunca reporta salud | `docker compose ps` y `docker compose logs <servicio>` |
| La librería del pipeline falla | `LIBRARY_BRANCH` incorrecto | Revisar `deploy/compose/tools/.env` |
| Jenkins no abre | Contenedor arrancando o poca memoria | Revisar logs y swap |
| SonarQube se queda en «starting» | PostgreSQL o memoria | Revisar logs; ampliar swap |
| Nexus no abre | Puerto 8081 o memoria | Revisar logs |
| El *pull* del registro falla | Registro HTTP no confiable | Verificar `insecure-registries` en el host del entorno |
| Terraform: `AccessDenied` al crear el bucket | `TF_VAR_minio_*` no exportados o distintos de `.env.onpremise` | Repetir el `source` y los `export` del runbook; no usar credenciales de AWS |
| Terraform: `ResourceNotFoundException` en DynamoDB | `dynamodb-local` se reinició con `-inMemory` y perdió la tabla | Volver a ejecutar `terraform apply` en `infra/terraform/state-backend` |
| Gateway 401 (Parte 1) | Falta el JWT de personal | Enviar `Authorization: Bearer <jwt>` |
| Gateway 503 (Parte 1) | Redis no disponible | Revisar `docker compose logs redis` |
