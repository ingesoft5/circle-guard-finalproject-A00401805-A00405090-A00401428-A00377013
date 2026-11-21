# Anexo — Migración on-premise de CircleGuard

**Última actualización:** 17 de noviembre de 2026
**Alcance:** continuidad de la plataforma de la Parte 1 sobre infraestructura propia y base de
la Parte 2.
**Estado:** herramientas y clúster operativos; `dev` y `stage` desplegados y verificados;
`prod` aplicado y en estabilización.

## 1. Motivo de la migración

La infraestructura de la Parte 1 se aprovisionó en AWS con los scripts de `infra/bootstrap/`.
El acceso a la cuenta quedó bloqueado antes del cierre del curso: no fue posible iniciar
sesión, recuperar la clave ni volver a ejecutar `bootstrap-aws.sh`. Sin cuenta no hay forma de
demostrar el entorno en vivo, aunque el código, los scripts y la evidencia del pipeline sigan
en el repositorio.

La decisión fue **no depender de esa cuenta** y reproducir la plataforma en un equipo propio,
sin `sudo`, sin abrir puertos a Internet y sin repetir el modelo de cuatro hosts de AWS. La
Parte 2 se construye directamente sobre esa base.

## 2. Topología

Un solo host con dos capas:

| Capa | Componente | Publicación |
|---|---|---|
| Herramientas (Docker Compose) | Jenkins, SonarQube, Nexus y el registro Docker | Puertos fijos del host; acceso por ZeroTier |
| Plataforma (k3d) | Clúster `cg` con los namespaces `cg-dev`, `cg-stage` y `cg-prod` | Ingress de Traefik en `:30080`, ruteo por `Host` |

- **Host:** Ubuntu 24.04.5, 8 vCPU, 31 GiB de RAM y 777 GB libres.
- **Red privada:** ZeroTier, dirección `10.147.20.67`.
- **Usuario de operación:** `docker`, sin privilegios de `sudo`.
- **Orquestador:** k3d v5.9.0 sobre k3s v1.35.5. k3s es una distribución de Kubernetes
  certificada, así que la API y los manifiestos son los de Kubernetes estándar; k3d solo
  empaqueta el nodo dentro de un contenedor Docker. Se descartó instalar Kubernetes sobre el
  sistema operativo (kubeadm, k3s nativo, microk8s) porque esas opciones exigen `sudo` para
  crear servicios del sistema, escribir `/etc/kubernetes`, ajustar cgroups y abrir los puertos
  `6443` y `10250`, y el host es un equipo compartido donde el usuario `docker` no tiene
  privilegios. Se descartaron también minikube y kind por consumo y por no traer Ingress ni
  balanceador por defecto, y EKS porque la cuenta de AWS quedó inaccesible. La comparación
  completa y los límites asumidos están en `deploy/k8s/README.md`.
- **Namespaces:** `cg-dev`, `cg-stage` y `cg-prod` sustituyen los tres entornos que en AWS eran
  tres hosts separados. Un solo clúster con tres namespaces reduce el consumo de RAM y mantiene
  el aislamiento lógico.

```
┌──────────────────────────── host (10.147.20.67) ────────────────────────────┐
│                                                                             │
│  Docker Compose · proyecto cg-tools                                         │
│  ┌──────────┐  ┌────────────┐  ┌──────────┐  ┌───────────────────────────┐ │
│  │ Jenkins  │  │ SonarQube  │  │  Nexus   │  │ Registro Docker :5000     │ │
│  │ :8080    │  │ :9000      │  │ :8081    │  │ repo circleguard-docker   │ │
│  └────┬─────┘  └────────────┘  └──────────┘  └─────────────┬─────────────┘ │
│       │ publica imágenes                                   │               │
│       └────────────────────────────────────────────────────┘               │
│                          │ pull por HTTP                                   │
│  k3d cluster cg          ▼                                                 │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │ cg-dev      cg-stage      cg-prod                                     │ │
│  │ gateway · auth · identity · form · promotion · file · notification ·  │ │
│  │ dashboard · web + PostgreSQL, Neo4j, Kafka, Redis y OpenLDAP          │ │
│  │ Traefik Ingress :30080 → Host `<env>.circleguard.local`               │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 3. Componentes y configuración

### 3.1 Stack de herramientas

- Archivos versionados: `deploy/compose/tools/docker-compose.onpremise.yml`, la plantilla
  `deploy/compose/tools/.env.onpremise.example` y `infra/onpremise/bootstrap-onpremise.sh`.
- El archivo real `.env.onpremise` se genera en el host con el bootstrap y nunca se sube al
  repositorio (regla 5 de la sección 4).
- Proyecto Compose: `cg-tools` (el bootstrap lo pasa con `-p cg-tools`).
- **Jenkins** corre con `network_mode: host` para alcanzar el registro en `localhost:5000` y
  el API del clúster sin saltos de red. Su configuración se carga como código desde
  `/var/jenkins_home/cac/jenkins.yml`; la variable `CASC_JENKINS_CONFIG` conserva ese nombre y
  apunta al archivo.
- **Nexus** aloja el repositorio `circleguard-docker` en el puerto `5000`, además de los
  repositorios Maven de snapshots y releases. El usuario de publicación es `jenkins`.
- **SonarQube** se mantiene con su propia base de datos y recibe los reportes de JaCoCo.

### 3.2 Registro de imágenes

Docker solo confía en el rango `127.0.0.0/8`, y el archivo `/etc/docker/daemon.json` no se
modifica porque el equipo es compartido. Por eso:

1. Jenkins publica en `localhost:5000` desde el propio host.
2. Los nodos de k3d jalan desde `10.147.20.67:5000`, que no es un registro con TLS.
3. El clúster declara ese registro como HTTP en `registries.yaml` y usa `imagePullSecrets`
   para autenticarse.

Esta combinación evita abrir el puerto `5000` a la red y evita tocar la configuración global de
Docker. Mientras el pipeline no publique, las imágenes se construyen en el host y se importan
con `k3d image import -c cg`.

### 3.3 Acceso

| Vía | Uso | Estado |
|---|---|---|
| ZeroTier (`10.147.20.67`) | Jenkins `:8080`, SonarQube `:9000`, Nexus `:8081`, registro `:5000` | Operativo |
| Ingress de Traefik `:30080` con header `Host: <env>.circleguard.local` | Aplicaciones de `dev`, `stage` y `prod` | Operativo |
| Cloudflare Tunnel + Access | Acceso público con autenticación | **No disponible**: requiere un dominio propio, que no se obtuvo |
| smee.io | Recepción del webhook de GitHub hacia Jenkins local | Configurado en el repo; pendiente aplicar y verificar en el host (ver 3.6) |

Detalles que importan:

- Traefik rutea **solo** por el header `Host` y la regla no incluye puerto. El navegador
  siempre añade el puerto (`dev.circleguard.local:30080`), así que la comprobación canónica es
  `curl -H "Host: dev.circleguard.local" http://localhost:30080/`.
- `port-forward` sirve la interfaz, pero rompe las llamadas `/api/v1/*` porque no pasan por
  Traefik.
- Un quick tunnel de Cloudflare reenvía el Host del túnel, no el del Ingress, de modo que no
  sustituye al DNS.
- Durante la sustentación se puede anteponer un proxy local que inyecte el header `Host`. Es un
  recurso de demostración, no parte del diseño.

No se exponen SSH, el API de k3s (`6443`), el registro (`5000`), Kafka ni las bases de datos.

### 3.4 Autenticación

- **Jenkins → clúster:** sin clave SSH. El despliegue es local con `kubectl` contra el
  clúster; el kubeconfig de k3d (~/.kube/config) se monta read-only en `/var/jenkins_home/.kube/config`
  y el pipeline despliega con `kustomize edit set image` + `kubectl apply -k` + `rollout status`.
  La credencial `cg-envs-ssh` y la clave `.ssh/cg-envs-key` se eliminaron de JCasC e imagen; el
  warning `FileStringLookup NoSuchFileException` que aparecía al arranque Jenkins ya no existe.
- **Jenkins → GitHub:** token personal, guardado como credencial.
- **Jenkins → SonarQube:** token de API creado en SonarQube; nunca se genera aleatoriamente (ver 3.6). Jenkins va con `network_mode: host`, así que habla con `localhost:9000`, no con `sonarqube:9000`.
- **Jenkins → Nexus:** usuario y contraseña del usuario `jenkins`.
- **Aplicación:** login por `username` (no por email), con usuarios semilla de desarrollo
  definidos en `V2__seed_test_users.sql`. Esas credenciales no deben llegar a producción.

### 3.5 Decisiones de configuración de los manifiestos

Cuatro ajustes del `base` explican por qué un despliegue nuevo arranca sin intervención manual:

| Componente | Ajuste | Causa que lo motiva |
|---|---|---|
| Neo4j | `enableServiceLinks: false` | Kubernetes inyecta `NEO4J_PORT_7687_TCP_PORT` y la imagen lee toda variable `NEO4J_*` como configuración |
| Kafka | `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093` | Con el nombre del `Service` el quórum espera endpoints que solo existen cuando el pod está `Ready` |
| `cg-config` | `SPRING_NEO4J_URI` (antes `NEO4J_URI`) | Spring resuelve `spring.neo4j.uri` por nombre relajado; `NEO4J_URI` no se enlazaba |
| PostgreSQL | `startupProbe` de 10 s × 30 y liveness con 60 s de gracia | Sin esa ventana el liveness mataba el contenedor a mitad de `initdb` (exit 137) y el clúster quedaba sin las cinco bases ni `pg_hba` remoto |

Los formatos de los secretos también son parte del diseño: `QR_SECRET` ≥ 32 bytes, `VAULT_SALT`
hex de longitud par, `JWT_SECRET` ≥ 32 bytes y `NEO4J_AUTH` igual a `neo4j/<NEO4J_PASSWORD>`.

### 3.6 Receptor del webhook (smee.io)

El host no tiene IP pública ni dominio, así que GitHub no puede llamar a Jenkins
directamente. El webhook del repo (id `709307452`, eventos `push` y
`pull_request`) apunta al canal `https://smee.io/nk4nvlnyB0u6Fqnn`; un cliente
`smee-client` corre como servicio de Compose y reenvía cada evento a Jenkins.

- El canal **no** es secreto; el secreto de firma sigue siendo
  `GITHUB_WEBHOOK_SECRET` (el mismo que `infra/state/tools.env`). Si no coinciden,
  Jenkins responde `403` aunque el relay esté conectado.
- `deploy/compose/tools/jenkins/cac/jenkins.yml` ya trae `hookUrl: "${SMEE_URL}"` y
  `manageHooks: false`: el hook se administra a mano en GitHub.
- El relay está versionado en `deploy/compose/tools/docker-compose.yml` (red `tools-net`) y
  en `docker-compose.onpremise.yml` (`network_mode: host`); la imagen se construye desde
  `deploy/compose/tools/smee-client/Dockerfile`.

**Incidente (2026-11-18):** el multibranch no disparaba nunca, y el síntoma visible eran
`405 Method POST required` en los logs de `smee-client`. Hay **dos bugs simultáneos**,
corregidos en el mismo PR:

1. **405 (causa real, independiente del secreto).** `smee-client` v5 (`trimTrailingSlash`)
   recorta la barra final del target antes de enviar: con `SMEE_TARGET=http://localhost:8080/github-webhook/`
   el POST llega a `/github-webhook` sin barra, el Stapler de Jenkins responde `302 → /github-webhook/`
   y la librería `fetch` de smee-client **degrada POST a GET en un 302** (comportamiento del estándar
   fetch). Jenkins recibe un `GET` → `405 Method POST required`. GitHub siempre mostraba `status=200`
   porque smee.io no propaga el status del destino. Fix: `SMEE_TARGET=http://localhost:8080/github-webhook//`
   (doble barra; smee recorta una y llega el POST directo, sin redirect). Comprobado desde fuera:
   `POST /github-webhook` → `302`, `GET /github-webhook/` → `405`, `POST /github-webhook//` → `400 No valid signature`
   (llega al plugin).
2. **Secret en blanco.** En `docker-compose.onpremise.yml` (y en la variante `docker-compose.yml` de `tools-net`) el
   servicio `jenkins` redeclaraba `GITHUB_WEBHOOK_SECRET: ${GITHUB_WEBHOOK_SECRET}` en `environment:`.
   `docker compose` da prioridad a `environment:` sobre `env_file:`, y la interpolación usaba el `.env`
   por defecto del directorio (valor stale/ausente), no `.env.onpremise`. Resultado tras el fix 1:
   Jenkins firmaría con un secreto vacío ⇒ `400 No valid signature found`. Regla: el servicio `jenkins`
   **no debe** redeclarar `GITHUB_WEBHOOK_SECRET` en `environment:`; la única fuente es `env_file: .env.onpremise`.

Reglas resultantes: fix 1 vive en `SMEE_TARGET` del `.env.onpremise` y en el valor por defecto del compose
(doble barra); fix 2 exige recrear Jenkins (`up -d --force-recreate jenkins`) para que JCasC relea la
credencial `github-webhook-secret`. `smee-client` queda fijado a `smee-client@5.0.0` en su `Dockerfile`;
si se sube la versión, revalidar el doble slash (el trim es de esa versión).

### 3.6.1 Token de SonarQube

El `SONAR_TOKEN` del bootstrap **no se genera aleatoriamente**: un `openssl rand` no es un
token válido de SonarQube y `sonar-scanner` responde `Not authorized`, igual que el paso de
quality gate. Hay que crearlo dentro de SonarQube:

```bash
# SonarQube fresco arranca con admin/admin (cambiar si ya se cambió la clave)
TOKEN=$(curl -s -u admin:admin -X POST http://localhost:9000/api/user_tokens/generate \
  --data-urlencode 'name=jenkins-ci' | jq -r .token)   # se muestra una sola vez
```

Se pasa al bootstrap (`SONAR_TOKEN=$TOKEN infra/onpremise/bootstrap-onpremise.sh`) o se edita
`.env.onpremise` y se recrea Jenkins para que JCasC relea el secreto:

```bash
docker compose --env-file .env.onpremise -f docker-compose.onpremise.yml -p cg-tools \
  up -d --force-recreate jenkins
docker compose --env-file .env.onpremise -f docker-compose.onpremise.yml -p cg-tools up -d smee-client
curl -s -u "$(grep '^SONAR_TOKEN=' .env.onpremise | cut -d= -f2):" \
  http://localhost:9000/api/authentication/validate   # debe devolver login, no 401
```

Mismo cuidado con las URLs: Jenkins va en `network_mode: host`, así que
`SONAR_HOST_URL` y `NEXUS_URL` son `http://localhost:…`; los nombres de servicio de
`tools-net` (`sonarqube`, `nexus`) **no resuelven** desde Jenkins.

Receta para este host (proyecto `cg-tools`):

1. Traer lo versionado: `git pull` (el compose, la plantilla `.env.onpremise.example` y
   `infra/onpremise/bootstrap-onpremise.sh` ya incluyen el relay).
2. Ejecutar `infra/onpremise/bootstrap-onpremise.sh`: crea `.env.onpremise` desde la
   plantilla, genera los secretos, detecta la IP de ZeroTier, pide usuario y token de
   GitHub, y **no** genera el secreto del webhook: lo toma de `GITHUB_WEBHOOK_SECRET`
   (del entorno o del archivo) o lo pide, porque debe coincidir exactamente con el del
   hook de GitHub (id `709307452`); si no, Jenkins responde `403`.
   `SMEE_TARGET` es `localhost` porque el relay va con `network_mode: host`, igual que
   Jenkins, y debe llevar **doble barra final** (`http://localhost:8080/github-webhook//`,
   ver 3.6); si el relay corre en la red bridge del Compose, usar
   `http://10.147.20.67:8080/github-webhook//`.
3. Verificar:

   ```bash
   docker logs -f cg-smee-client        # debe decir "Connected" al canal
   ```

   En GitHub: Settings > Webhooks > última entrega > **Redeliver**; en Jenkins debe
   aparecer el scan de la multibranch y el build del PR. El check que exige la
   protección de rama es `continuous-integration/jenkins/pr-merge`.

Ojo: el `status=OK` de GitHub solo significa que smee.io aceptó el evento; sin el
relay conectado el evento se pierde y el PR no compila.

### 3.7 Reconstrucción on-premise en un comando

Los scripts Bash ya eran idempotentes por construcción (`ensure_env_*`, `ensure_secret`,
existence checks y `docker compose up -d`), pero la frase literal de P1.2 —*“scripts
idempotentes que reconstruyen Jenkins, SonarQube y registro desde cero con un comando”*—
no tenía un entry point único en este host: `bootstrap-onpremise.sh` levanta Compose y
`configure-sonar-identity.sh` asegura proyectos/gate, pero el primero no llamaba al segundo.

Ese cierre se versiona en `infra/onpremise/rebuild-onpremise.sh`. Desde la raíz del repo,
en el host y con el usuario `docker`:

```bash
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-1.log
bash infra/onpremise/rebuild-onpremise.sh 2>&1 | tee /tmp/rebuild-run-2.log
```

El wrapper valida el token de SonarQube, ejecuta el bootstrap, recorre todos los
`projectKey` del catálogo de la Shared Library —incluido `circleguard-file-service`, que
no está en la lista histórica de `bootstrap-tools.sh`— y termina con `docker compose
config -q` + `up -d --remove-orphans`. La segunda corrida es la prueba de convergencia:
no debe crear duplicados, pedir secretos nuevos ni cambiar credenciales.

Además, el wrapper re-registra el webhook y la branch protection ejecutando
`infra/bootstrap/bootstrap-github.sh`. Ese script acepta ahora tres orígenes de
entorno, en orden: `infra/state/tools.env`, `deploy/compose/tools/.env` y
`deploy/compose/tools/.env.onpremise`. En este host el válido es el tercero, por
lo que ya no hace falta el archivo puente temporal `infra/state/tools.env`; si
llega a crearse, debe borrarse para evitar que un valor antiguo tape al actual.

La inmutabilidad de tags, si se activa en el repositorio Docker hosted de Nexus, se aplica
solo al patrón `^\d+\.\d+\.\d+$`. `latest`, `dev`, `staging` y los tags de PR siguen siendo
mutables. El pipeline conserva el skip de publicación: sin él, un rerun con el mismo tag
semver intentaría republicar y Nexus respondería `400/409`, haciendo fallar P1.4. Esta
política queda así documentada conforme a E1.2 (*no se acepta configuración manual no
documentada*).

### 3.8 Limpieza dinámica de ramas en Jenkins

El job multibranch **no filtra ramas por convención de nombre**. GitHub es la única fuente
de verdad: se indexan todas las ramas y PRs existentes, y la rama que se mergea en `main`
y se borra en GitHub deja de analizarse en el siguiente index. Para que la limpieza sea
inmediata, `infra/bootstrap/bootstrap-github.sh` registra el evento `delete` además de
`push` y `pull_request`; el script actualiza el hook existente de forma idempotente
(GET → PATCH/POST). La estrategia de huérfanos usa `daysToKeep(0)` y `numToKeep(0)`, y el
escaneo de 20 minutos queda solo como respaldo.

`main` es la rama de integración; `dev` es un entorno del pipeline, no una rama del flujo
actual (ver `docs/informe/P1.1-Branching-GitHub-Flow.md`). Por eso el criterio es la
existencia de la rama en GitHub, no su prefijo ni su merge en un branch `dev`.

## 4. Reglas de operación

1. No detener, reiniciar ni borrar los contenedores ajenos `k8s-worker` e `icesi-inn`.
2. No ejecutar `docker prune`.
3. No usar `docker compose down -v`: los volúmenes contienen las bases de datos.
4. Un solo `kubectl apply` a la vez por namespace para evitar carreras entre integrantes.
5. Los secretos viven en `.env.onpremise` y en los `Secret` del clúster; nunca en el
   repositorio.
6. Los manifiestos incluyen un `namespace.yml` por overlay: `kubectl apply -k` crea el
   namespace si falta. No se crea a mano.
7. Como máximo dos entornos completos encendidos a la vez: los tres superan la RAM del host
   cuando se suman a Jenkins, SonarQube y Nexus.

## 5. Evidencia de la migración

| Evidencia | Cómo se verifica | Estado |
|---|---|---|
| Docker operativo sin `sudo` | `docker version` y `docker ps` con el usuario `docker` | Verificado |
| Herramientas arriba | `curl -f http://localhost:8080/login`, `:9000/api/system/status`, `:8081/service/rest/v1/status` | Verificado |
| Registro con el repositorio | `curl -u jenkins http://localhost:5000/v2/_catalog` | Verificado |
| Clúster y namespaces | `k3d cluster list` y `kubectl get ns` con `cg-dev`, `cg-stage`, `cg-prod` | Verificado |
| Servicios desplegados en `cg-dev` | 14/14 pods `Ready`; Ingress responde `200`; `POST /api/v1/auth/login` emite un JWT | Verificado |
| Promoción a `cg-stage` | 14/14 pods `Ready`; Ingress responde `200`; login emite un JWT | Verificado |
| Promoción a `cg-prod` | Mismo manifiesto aplicado por namespace y verificación de extremo a extremo | En curso |

Los artefactos textuales (`kubectl get pods`, códigos HTTP del Ingress y del login con el JWT
recortado) se guardan en `docs/informe/evidencia/`. No se enlazan imágenes que no existan en el
repositorio.

## 6. Diferencias frente a la infraestructura de AWS

| Aspecto | AWS (Parte 1) | On-premise |
|---|---|---|
| Hosts | Cuatro instancias EC2 | Un host con ZeroTier |
| Entornos | Tres hosts con Compose | Tres namespaces en un clúster k3d |
| Registro | Nexus en el host de herramientas | Nexus en el mismo host, repo `circleguard-docker` |
| Despliegue | `deploy/scripts/*.sh` por SSH | `kubectl apply -k` desde el host, por Jenkins |
| Acceso | IP pública e ingress | ZeroTier + Ingress por header `Host`; Cloudflare pendiente de dominio |
| Costo | Crédito de AWS | Energía del equipo |

La migración SSH → `kubectl` está **implementada**: el pipeline despliega con Kustomize
(`kustomize edit set image` dentro del overlay del workspace, `kubectl apply -k` y
`rollout status` por servicio), importa las imágenes al clúster con `k3d image import -c cg`,
y el E2E siembra Neo4j con `kubectl exec ... cypher-shell` y publica `survey.submitted` con
`kubectl exec ... kafka-console-producer.sh`. No queda `sshagent` ni clave privada en JCasC,
en la imagen de Jenkins ni en el `Jenkinsfile`. Hasta que no exista un dominio propio, el
webhook de GitHub apunta al canal `smee.io` (`nk4nvlnyB0u6Fqnn`) con el relay versionado en
`deploy/compose/tools` (aplicar y verificar en este host, sección 3.6).

## 7. Relación con los entregables

- **P1.2 (infraestructura):** el anexo documenta una infraestructura reproducible alternativa y
  explica por qué la de AWS no puede demostrarse en vivo.
- **P2.2 (Kubernetes):** namespaces, ConfigMaps, Secrets, Ingress, sondas Actuator y HPA están
  desplegados y verificados. Quedan pendientes TLS y RBAC, declarados en
  `deploy/k8s/README.md`.
- **P2.4 (observabilidad) y P2.8 (pipeline con IaC y `kubectl`):** se apoyan en esta topología;
  comparten host, registro y clúster.
