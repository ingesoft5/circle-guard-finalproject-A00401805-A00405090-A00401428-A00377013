# Kubernetes on-premise (k3d)

Manifiestos Kustomize para los ocho servicios de CircleGuard, el cliente web y su
middleware, sobre el clúster `cg` que corre en el host on-premise. Es la base del
entregable **E2.2 (Orquestación en Kubernetes)** de la Parte 2.

## Estructura

```
deploy/k8s/
├── base/
│   ├── kustomization.yml
│   ├── middleware.yml   # postgres, redis, neo4j, kafka, openldap + ConfigMaps
│   ├── apps.yml         # 8 servicios + web
│   ├── ingress.yml      # rutas del API y del cliente web (Traefik)
│   └── hpa.yml          # autoscaling de gateway, web y auth
└── overlays/
    ├── dev/              # namespace cg-dev, host dev.circleguard.local
    ├── stage/            # namespace cg-stage, host stage.circleguard.local
    └── prod/             # namespace cg-prod, host prod.circleguard.local
```

Cada overlay trae `secrets.env.example`. El archivo real (`secrets.env`) **no se versiona**
(`.gitignore`, regla `deploy/k8s/overlays/*/secrets.env`).

Cada overlay solo cambia tres cosas: el namespace, el `host` del Ingress y el `newTag` de
las nueve imágenes. Todo lo demás se hereda de `base`, de modo que un arreglo en `base`
llega a los tres entornos sin duplicar manifiestos.

## Namespaces

Cada overlay incluye su propio `namespace.yml`, así que `kubectl apply -k` crea el namespace
si no existe y el despliegue es autosuficiente:

```bash
kubectl apply -k deploy/k8s/overlays/dev     # crea cg-dev si falta
kubectl get ns | grep cg-
```

Si el namespace ya existe, el `apply` lo reconcilia sin efecto. No hace falta `kubectl create
namespace` manual (el runbook conserva el comando solo para verificar el estado).

## Por qué Kustomize y no Helm

La plataforma ya tiene un `docker-compose` por ambiente que sirve de referencia. Kustomize
permite reutilizar una sola base y cambiar solo el namespace, el host y el tag de imagen por
ambiente, sin mantener un chart ni una plantilla con valores por defecto. Para tres ambientes
y nueve aplicaciones, el costo de Helm no se justifica.

## Por qué k3d y no Kubernetes instalado en el host

Aclaración que conviene dejar por escrito porque es la pregunta obvia del jurado: **k3d no es
"otro Kubernetes"**. k3d ejecuta **k3s**, que es una distribución de Kubernetes con
certificación de conformidad de la CNCF. Los objetos, la API, `kubectl`, el Ingress, las
sondas, el HPA y el RBAC son los mismos que en un clúster gestionado. Lo único que cambia es
**cómo se empaqueta el nodo**: en vez de instalar kubelet y containerd como servicios del
sistema, k3s corre dentro de un contenedor Docker y k3d gestiona ese contenedor.

**La restricción que decide la elección:** el host es compartido, el usuario de operación es
`docker` y **no tiene `sudo`**. Instalar Kubernetes sobre el sistema operativo (kubeadm, k3s
nativo o microk8s) exige root para: crear el servicio de systemd, instalar containerd y los
binarios en `/usr/local/bin`, escribir `/etc/kubernetes` y `/var/lib/kubelet`, ajustar
cgroups, cargar módulos del kernel, añadir reglas de nftables e iptables, y abrir los puertos
`6443` y `10250`. Ninguna de esas operaciones es reversible sin afectar la red y los
contenedores de los otros proyectos del equipo.

### Comparación de las alternativas evaluadas

| Opción | ¿Requiere root? | Ingress/LB incluido | Carga de imágenes locales | Ciclo crear/borrar | Veredicto |
|---|---|---|---|---|---|
| **k3d** | No (grupo `docker`) | Sí: Traefik + servicelb | `k3d image import` | segundos | **Elegido** |
| k3s nativo | Sí (unit de systemd) | Parcial (Traefik opcional) | `ctr images import` manual | servicio del sistema | Descartado |
| minikube | No (driver docker) | No (addon aparte) | `minikube image load` | lento, consume más RAM | Descartado |
| kind | No | No | `kind load docker-image` | rápido | Descartado: sin Ingress ni LB por defecto |
| microk8s | Sí (`snap install`) | Addon | `microk8s ctr` | snap | Descartado |
| kubeadm | Sí | No (instalar CNI e Ingress) | Registry propio | manual y lento | Descartado |
| EKS | Cuenta AWS (bloqueada) | ALB | ECR | minutos y costo | Descartado |

### Razones a favor

1. **No requiere privilegios.** Todo se ejecuta como el usuario `docker`; no se modifican
   `/etc`, ni el daemon de Docker, ni la configuración de red del host compartido.
2. **Ciclo de vida desechable.** `k3d cluster create/delete` tarda segundos, algo necesario
   porque `stage` y `prod` se levantan para la demostración y se bajan después para liberar
   RAM en un host de 31 GiB que también corre Jenkins, SonarQube y Nexus.
3. **Resuelve la distribución de imágenes sin registry con TLS.** `k3d image import` mete las
   nueve imágenes en los nodos sin necesidad de un registro seguro, y permite que el
   `imagePullPolicy: IfNotPresent` funcione desde el primer intento.
4. **Traefik y ServiceLB vienen incluidos.** El requisito de Ingress del entregable E2.2
   queda cubierto sin instalar controladores; además la red del clúster está aislada dentro
   de Docker, así que no interfiere con los contenedores ajenos `k8s-worker` e `icesi-inn`.
5. **Fidelidad con un clúster real.** Los manifiestos Kustomize y los comandos `kubectl` son
   los mismos que se usarían en EKS, GKE u OpenShift; migrar solo implicaría cambiar el
   `kubeconfig`, el `newName` de las imágenes y el `imagePullSecret`.

### Límites que se asumen

1. **Un solo nodo.** No hay alta disponibilidad, ni afinidad entre nodos, ni planificación
   multi-nodo; se puede demostrar escalado de *pods* (HPA), no de *nodos*.
2. **Contenedores dentro de contenedores.** El kubelet vive en un contenedor Docker, de modo
   que el almacenamiento de los pods pasa por overlayfs. Con 14 pods por entorno el
   rendimiento es suficiente, pero no es un entorno para medir capacidad.
3. **El clúster depende de Docker.** Si se reinicia el servicio Docker del host, los
   contenedores `k3d-server`/`k3d-agent` no vuelven necesariamente solos; hay que rearrancar
   con `k3d cluster start cg`. Es un riesgo operativo conocido y documentado en el runbook.
4. **No reproduce servicios de nube.** ServiceLB simula el `LoadBalancer`, no hay volúmenes de
   bloque del proveedor ni auto scaling de infraestructura. Esa diferencia con la Parte 1 se
   declara en `docs/infra/migracion-onpremise.md`.

**Conclusión para el informe:** la elección de k3d es de **empaquetado y operación**, no de
API. Al ser k3s un Kubernetes certificado, los criterios de E2.2 —namespaces, ConfigMaps y
Secrets, Ingress, sondas Actuator y HPA— se evalúan exactamente igual que en un clúster
gestionado, y el código de los manifiestos no tendría que cambiar para correr en la nube.

## Registro e imágenes

El registro Nexus vive en `10.147.20.67:5000` y se publica por HTTP, no por HTTPS. En el
clúster eso se declara en el `registries.yaml` de k3d (plantilla en
`infra/onpremise/registries.yaml`; `infra/onpremise/k3d-cluster-create.sh` la copia a
`~/.config/k3d/registries.yaml`) y con `imagePullSecrets`; el puerto no se expone a
Internet.

Mientras el pipeline no publique en Nexus, las imágenes se construyen en el host y se
importan al clúster. La compilación usa **la raíz del repositorio como contexto** y el
`Dockerfile` de cada servicio:

```bash
TAG=1.4.0
for svc in auth identity promotion form gateway dashboard file notification; do
  docker build -f "services/circleguard-$svc-service/Dockerfile" \
    -t "circleguard-docker/$svc:$TAG" .
done
docker build -t "circleguard-docker/web:$TAG" mobile

k3d image import -c cg \
  circleguard-docker/auth:$TAG \
  circleguard-docker/identity:$TAG \
  circleguard-docker/promotion:$TAG \
  circleguard-docker/form:$TAG \
  circleguard-docker/gateway:$TAG \
  circleguard-docker/dashboard:$TAG \
  circleguard-docker/file:$TAG \
  circleguard-docker/notification:$TAG \
  circleguard-docker/web:$TAG
```

Los manifiestos usan `imagePullPolicy: IfNotPresent`, así que el clúster toma la imagen local
y no intenta descargarla. Cuando el pipeline publique en Nexus, el overlay cambiará el
`newName` a `10.147.20.67:5000/circleguard-docker/<servicio>`.

> Los Dockerfiles de los servicios copian `lombok.config` desde la raíz del repositorio. Si
> se omite, Gradle falla con *"property 'lombokConfig' specifies file '/workspace/lombok.config'
> which doesn't exist"*.

## Secretos

```bash
cd deploy/k8s/overlays/dev
cp secrets.env.example secrets.env
chmod 600 secrets.env
```

`secrets.env` se carga con `secretGenerator` y se convierte en el `Secret cg-secrets`. Las
aplicaciones lo consumen por `envFrom`, igual que en Compose.

Formatos **obligatorios**; si se incumplen, el pod entra en `CrashLoopBackOff`:

| Variable | Formato válido | Error si se incumple |
|---|---|---|
| `QR_SECRET` | ≥ 32 bytes; `openssl rand -hex 24` (48 hex) | `WeakKeyException: ... 128 bits` en `auth` |
| `VAULT_SALT` | hex de longitud par; `openssl rand -hex 16` (32 hex) | `Detected a Non-hex character at 1 or 2 position` en `identity` |
| `JWT_SECRET` | ≥ 32 bytes; `openssl rand -base64 48` | Firma de tokens inválida |
| `NEO4J_AUTH` | `neo4j/<NEO4J_PASSWORD>`, con el mismo valor exacto | `Neo.ClientError.Security.Unauthorized` |
| `POSTGRES_PASSWORD` | texto libre | `password authentication failed` |

`VAULT_HASH_SALT` es de texto libre (es la sal de SHA-256, no de cifrado). Verificar formatos
sin imprimir los valores:

```bash
awk -F= '/^(QR_SECRET|VAULT_SALT|JWT_SECRET)=/{print $1" len="length($2)}' \
  deploy/k8s/overlays/dev/secrets.env
```

## Despliegue

```bash
# Validar sin aplicar
kubectl kustomize deploy/k8s/overlays/dev

# Aplicar
kubectl apply -k deploy/k8s/overlays/dev

# Verificar
kubectl -n cg-dev get pods,svc,ingress
```

## Arranque en frío

Un `apply` en un namespace nuevo levanta los 14 pods (9 aplicaciones + 5 middleware) al mismo
tiempo. `postgres` tarda en inicializar sus cinco bases y las aplicaciones usan Flyway con
`failFast`, por lo que **entran en `CrashLoopBackOff` hasta que Postgres esté `Ready`**. No es
un fallo del manifiesto: es el orden de arranque. Secuencia correcta:

```bash
kubectl -n cg-stage wait --for=condition=Ready pod/postgres-0 --timeout=300s
kubectl -n cg-stage rollout restart deployment/auth deployment/identity \
  deployment/dashboard deployment/form deployment/promotion
```

Con `prod` se repite igual. El `startupProbe` de PostgreSQL (5 minutos) impide que el liveness
mate el contenedor durante el `initdb`, que fue lo que dejó a `cg-prod` sin bases ni acceso
remoto. Deuda registrada: sustituir los reinicios manuales de las cinco aplicaciones por un
`initContainer` de espera contra PostgreSQL.

## Acceso

El Ingress usa Traefik, que ya viene con k3d, publicado en el puerto `30080` del host.
Traefik rutea **estrictamente por el header `Host`**, y la regla es
`Host(`dev.circleguard.local`)` — sin puerto.

```bash
curl -i -H "Host: dev.circleguard.local" http://10.147.20.67:30080/
```

Como `*.circleguard.local` no resuelve en DNS, el navegador no puede reproducir ese header:
envía la IP o el nombre con el puerto (`dev.circleguard.local:30080`), que no coincide con la
regla. Por eso:

- `curl -H "Host: ..."` es la verificación canónica del Ingress.
- `kubectl -n cg-dev port-forward svc/web 8081:80` sirve **solo la UI**: las llamadas
  `/api/v1/*` del cliente se resuelven contra el mismo origen y no pasan por Traefik, así que
  el login falla.
- Para la demostración visual se puede anteponer un proxy que inyecte el header `Host`. Es un
  recurso de demo, **no** parte del diseño: la solución real es DNS + ingress público.

Un quick tunnel de Cloudflare **no** resuelve esto: reenvía el Host del túnel
(`*.trycloudflare.com`), no el del Ingress. Cloudflare nombrado + Access requiere un dominio
propio, que el equipo no tiene.

## Decisiones de configuración del middleware

Tres valores no son obvios y sin ellos el stack no arranca. Están en `base` para que los tres
entornos los hereden:

| Componente | Ajuste | Por qué |
|---|---|---|
| `neo4j` | `enableServiceLinks: false` | Kubernetes inyecta `NEO4J_PORT_7687_TCP_PORT`, etc.; la imagen de Neo4j lee **toda** variable `NEO4J_*` como configuración y muere con *Unrecognized setting* |
| `kafka` | `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093` | Con `1@kafka:9093` el quórum apunta al `Service`, cuyos endpoints solo existen cuando el pod está `Ready` → interbloqueo con la readiness `tcpSocket:9092` |
| `cg-config` | `SPRING_NEO4J_URI: bolt://neo4j:7687` | `promotion` no define la URI en su Deployment; el default de Spring es `bolt://localhost:7687`. La clave debe llamarse como la propiedad (`spring.neo4j.uri`) |

Documentar esto evita que un despliegue nuevo repita el mismo diagnóstico.

## Sondas y salud

Cada aplicación declara `livenessProbe` y `readinessProbe` HTTP contra
`/actuator/health`, con periodos de 10 s y umbrales conservadores. El middleware usa sondas
`tcpSocket` (Postgres, Redis, Kafka, OpenLDAP) y HTTP en Neo4j. Esto cubre el requisito de
sondas alimentadas por Actuator del entregable E2.2.

## Autoscaling (HPA)

`base/hpa.yml` define cuatro `HorizontalPodAutoscaler` sobre CPU al 70 %:

| Servicio | Mínimo | Máximo | Motivo |
|---|---|---|---|
| `gateway` | 1 | 3 | Es la puerta de entrada del API y el que recibe la carga de la demostración |
| `web` | 1 | 2 | Cliente estático; su límite de memoria es el más bajo del stack |
| `auth` | 1 | 2 | Emite los JWT de todas las peticiones autenticadas |
| `form` | 1 | 2 | Recibe cuestionarios y adjuntos; las réplicas comparten el PVC local del mismo nodo |

Los cuatro Deployments escalados **no declaran `spec.replicas` en la base**: el dueño del número de
réplicas es el HPA. Si el Deployment fijara `replicas`, cada `kubectl apply -k` lo devolvería
a 1 y pelearía con el autoscaler.

El overlay de stage excluye **solo** el HPA de Form y fija una réplica para su
validación durante una promoción autorizada. Después, el operador puede escalar Form
a cero; seguirá en cero hasta una nueva promoción o escalado explícito. Excluirlo del
render no elimina un HPA que se hubiera creado previamente: comprobar su ausencia antes
de dormir stage. Esta HU no cambia la política de los otros HPA ni ejecuta cambios remotos.

Form monta `form-uploads` en `/tmp/circleguard-uploads`, con PVC independiente por
namespace. La migración inicial de archivos, las limitaciones de `local-path` y los
pendientes de ejecución están en [HU-FORM-05](../../docs/informe/HU-FORM-05.md).

Requisito previo, `metrics-server` (no viene con k3d):

```bash
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl -n kube-system patch deployment metrics-server --type=json \
  -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl top nodes
kubectl -n cg-dev get hpa
```

`--kubelet-insecure-tls` es necesario porque los certificados de k3s son autofirmados; sin
ese argumento `metrics-server` no puede leer los kubelets y el HPA muestra `<unknown>`.

Para ver el escalado en la demostración:

```bash
kubectl -n cg-dev get hpa -w
kubectl -n cg-dev run carga --image=busybox --restart=Never -- \
  sh -c 'while true; do wget -q -O- http://gateway:8087/actuator/health; done'
# al terminar
kubectl -n cg-dev delete pod carga
```

Decisión declarada: el máximo se limita a 2-3 réplicas porque el host tiene 31 GiB y comparte
RAM con Jenkins, SonarQube y Nexus. Un clúster de producción usaría máximos mayores; aquí se
demuestra el mecanismo, no la capacidad.

## Recursos y capacidad

El host tiene 31 GiB de RAM y ya ejecuta Jenkins, SonarQube y Nexus. La suma de límites de
los nueve servicios y el middleware supera la RAM disponible si se levantan los tres
namespaces a la vez, por eso:

- `dev` corre completo de forma permanente.
- `stage` y `prod` se levantan para la demostración y se bajan después.
- Las réplicas son 1 y los límites están ajustados; el objetivo de P2-02 es demostrar el
  despliegue, no soportar carga real.

Cada entorno completo son 14 pods. Con `dev` y `stage` arriba, el host queda alrededor de
8-9 GiB de RAM disponibles: suficiente, pero es la razón por la que no se dejan los tres
encendidos indefinidamente.

## Rollback

```bash
kubectl -n cg-dev rollout undo deploy/auth
kubectl -n cg-dev rollout status deploy/auth
```

Como las imágenes son inmutables y los PVC no se tocan, el rollback solo cambia la imagen de
la aplicación. Es el mismo criterio del `rollback.sh` de Compose.

## Verificación rápida

```bash
# 14 pods Ready por entorno
kubectl -n cg-dev get pods

# El Ingress responde
curl -o /dev/null -s -w "web=%{http_code}\n" \
  -H "Host: dev.circleguard.local" http://localhost:30080/

# El login emite un JWT
curl -s -X POST -H "Host: dev.circleguard.local" \
  -H "Content-Type: application/json" \
  -d '{"username":"super_admin","password":"password"}' \
  http://localhost:30080/api/v1/auth/login
```

El campo de login es `username`, no `email` (`LoginController` resuelve por
`findByUsername`). Los usuarios semilla de desarrollo están en
`services/circleguard-auth-service/src/main/resources/db/migration/V2__seed_test_users.sql`
y **no deben llegar a producción**.

## Trazabilidad con el enunciado (E2.2)

| Criterio del enunciado | Estado | Evidencia |
|---|---|---|
| Namespaces por ambiente | Cumplido | `cg-dev`, `cg-stage`, `cg-prod` |
| ConfigMaps y Secrets | Cumplido | `cg-config`, `postgres-init`, `cg-secrets` |
| Ingress | Cumplido | Traefik `:30080`, tres hosts |
| Sondas liveness/readiness con Actuator | Cumplido | `apps.yml`, `/actuator/health` |
| Clúster ligero (k3s/k3d) | Cumplido | k3d v5.9.0 / k3s v1.35.5 |
| HPA | Cumplido | `base/hpa.yml` + `metrics-server`; gateway 1-3, web y auth 1-2 |
| TLS en servicios expuestos | **Pendiente** | depende de DNS propio; hoy es HTTP interno |
| RBAC de Kubernetes | **Pendiente** | hoy la operación es con el `kubeconfig` de k3d |

Las brechas están declaradas a propósito: el enunciado penaliza configuraciones que no se
pueden explicar, no deudas documentadas con su plan de cierre.
