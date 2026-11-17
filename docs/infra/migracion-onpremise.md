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
│  Docker Compose · proyecto circle-guard-dasf                                │
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

- Carpeta de trabajo: `cac`.
- Archivos: `.env.onpremise` y `docker-compose.onpremise.yml`.
- Proyecto Compose: `circle-guard-dasf`.
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
| smee.io | Recepción del webhook de GitHub hacia Jenkins local | Planificado |

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

- **Jenkins → clúster:** sin clave SSH. El despliegue es local, con `kubectl` contra el
  clúster. Los `Jenkinsfile` todavía contienen `sshagent(['cg-envs-ssh'])`; la migración a
  `kubectl` es parte de la Parte 2.
- **Jenkins → GitHub:** token personal, guardado como credencial.
- **Jenkins → SonarQube:** token del proyecto.
- **Jenkins → Nexus:** usuario y contraseña del usuario `jenkins`.
- **Aplicación:** login por `username` (no por email), con usuarios semilla de desarrollo
  definidos en `V2__seed_test_users.sql`. Esas credenciales no deben llegar a producción.

### 3.5 Decisiones de configuración de los manifiestos

Tres ajustes del `base` explican por qué un despliegue nuevo arranca sin intervención manual:

| Componente | Ajuste | Causa que lo motiva |
|---|---|---|
| Neo4j | `enableServiceLinks: false` | Kubernetes inyecta `NEO4J_PORT_7687_TCP_PORT` y la imagen lee toda variable `NEO4J_*` como configuración |
| Kafka | `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093` | Con el nombre del `Service` el quórum espera endpoints que solo existen cuando el pod está `Ready` |
| `cg-config` | `SPRING_NEO4J_URI` (antes `NEO4J_URI`) | Spring resuelve `spring.neo4j.uri` por nombre relajado; `NEO4J_URI` no se enlazaba |

Los formatos de los secretos también son parte del diseño: `QR_SECRET` ≥ 32 bytes, `VAULT_SALT`
hex de longitud par, `JWT_SECRET` ≥ 32 bytes y `NEO4J_AUTH` igual a `neo4j/<NEO4J_PASSWORD>`.

## 4. Reglas de operación

1. No detener, reiniciar ni borrar los contenedores ajenos `k8s-worker` e `icesi-inn`.
2. No ejecutar `docker prune`.
3. No usar `docker compose down -v`: los volúmenes contienen las bases de datos.
4. Un solo `kubectl apply` a la vez por namespace para evitar carreras entre integrantes.
5. Los secretos viven en `.env.onpremise` y en los `Secret` del clúster; nunca en el
   repositorio.
6. El namespace se crea a mano antes del primer `apply` (`kubectl create namespace cg-<env>`):
   los manifiestos no incluyen un `kind: Namespace`.
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

El cambio de SSH a `kubectl` obliga a actualizar `Jenkinsfile` y `jenkins/vars/ci.groovy`, que
todavía contienen `sshagent(['cg-envs-ssh'])`. Esa migración es parte de la Parte 2 y hasta que
se complete el pipeline on-prem no se declara operativo. El webhook de GitHub todavía apunta a
la IP de AWS; el plan es recibirlo por `smee.io` mientras no exista un dominio propio.

## 7. Relación con los entregables

- **P1.2 (infraestructura):** el anexo documenta una infraestructura reproducible alternativa y
  explica por qué la de AWS no puede demostrarse en vivo.
- **P2.2 (Kubernetes):** namespaces, ConfigMaps, Secrets, Ingress y sondas Actuator están
  desplegados y verificados. Quedan pendientes HPA, TLS y RBAC, declarados en
  `deploy/k8s/README.md`.
- **P2.4 (observabilidad) y P2.8 (pipeline con IaC y `kubectl`):** se apoyan en esta topología;
  comparten host, registro y clúster.
