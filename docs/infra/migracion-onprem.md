# Anexo — Migración on-premise de CircleGuard

**Última actualización:** 16 de noviembre de 2026
**Alcance:** continuidad de la plataforma de la Parte 1 sobre infraestructura propia y base
para la Parte 2.
**Estado:** herramientas y clúster operativos; despliegue de los ocho servicios en curso.

## 1. Motivo de la migración

La infraestructura de la Parte 1 se aprovisionó en AWS con los scripts de `infra/bootstrap/`.
El acceso a la cuenta quedó bloqueado antes del cierre del curso: no fue posible iniciar
sesión, recuperar la clave ni volver a ejecutar `bootstrap-aws.sh`. Sin cuenta no hay forma
de demostrar el entorno en vivo, aunque el código, los scripts y la evidencia del pipeline
sigan en el repositorio.

La decisión fue **no depender de esa cuenta** y reproducir la plataforma en un equipo propio,
sin `sudo`, sin abrir puertos a Internet y sin repetir el modelo de cuatro hosts de AWS. La
Parte 2 se construye directamente sobre esa base.

## 2. Topología

Un solo host con dos capas:

| Capa | Componente | Publicación |
|---|---|---|
| Herramientas (Docker Compose) | Jenkins, SonarQube, Nexus y el registro Docker | Solo en `localhost`; acceso remoto por túnel |
| Plataforma (k3d) | Clúster `cg` con los namespaces `cg-dev`, `cg-stage` y `cg-prod` | Servicios expuestos por ingress interno |

- **Host:** Ubuntu 24.04.5, 8 vCPU, 31 GiB de RAM y 777 GB libres.
- **Red privada:** ZeroTier, dirección `10.147.20.67`.
- **Usuario de operación:** `docker`, sin privilegios de `sudo`.
- **Orquestador:** k3d v5.9.0 sobre k3s v1.35.5. Se eligió k3d y no k3s nativo porque corre
  dentro de Docker y no requiere instalar un servicio del sistema.
- **Namespaces:** `cg-dev`, `cg-stage` y `cg-prod` sustituyen los tres entornos que en AWS
  eran tres hosts separados. Un solo clúster con tres namespaces reduce el consumo de RAM y
  mantiene el aislamiento lógico.

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
  `/var/jenkins_home/cac/jenkins.yml`; la variable `CASC_JENKINS_CONFIG` conserva ese nombre
  y apunta al archivo.
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

Esta combinación evita abrir el puerto `5000` a la red y evita tocar la configuración global
de Docker.

### 3.3 Acceso

| Vía | Uso |
|---|---|
| Cloudflare Tunnel + Access | Acceso principal a Jenkins, Sonar y Nexus, con autenticación por correo |
| ZeroTier | Respaldo para el equipo y acceso directo durante la sustentación |
| smee.io o polling | Recepción del webhook de GitHub cuando el túnel no está disponible |

No se exponen SSH, el API de k3s (`6443`), el registro (`5000`), Kafka ni las bases de datos.

### 3.4 Autenticación

- **Jenkins → hosts:** sin clave SSH. El despliegue es local, con `kubectl` contra el clúster
  y sin `sshagent`.
- **Jenkins → GitHub:** token personal, guardado como credencial.
- **Jenkins → SonarQube:** token del proyecto.
- **Jenkins → Nexus:** usuario y contraseña del usuario `jenkins`.

## 4. Reglas de operación

1. No detener, reiniciar ni borrar los contenedores ajenos `k8s-worker` e `icesi-inn`.
2. No ejecutar `docker prune`.
3. No usar `docker compose down -v`: los volúmenes contienen las bases de datos.
4. Un solo `kubectl apply` a la vez por namespace para evitar carreras entre integrantes.
5. Los secretos viven en `.env.onpremise` y en los `Secret` del clúster; nunca en el
   repositorio.

## 5. Evidencia de la migración

| Evidencia | Cómo se verifica | Estado |
|---|---|---|
| Docker operativo sin `sudo` | `docker version` y `docker ps` con el usuario `docker` | Verificado |
| Herramientas arriba | `curl -f http://localhost:8080/login`, `:9000/api/system/status`, `:8081/service/rest/v1/status` | Verificado |
| Registro con el repositorio | `curl -u jenkins http://localhost:5000/v2/_catalog` | Verificado |
| Clúster y namespaces | `k3d cluster list` y `kubectl get ns` con `cg-dev`, `cg-stage`, `cg-prod` | Verificado |
| Servicios desplegados en `cg-dev` | `kubectl -n cg-dev get deploy,pods` y `curl` al healthcheck de cada servicio | En curso |
| Promoción a `cg-stage` y `cg-prod` | Mismo manifiesto aplicado por namespace y E2E por ambiente | Pendiente |

> Las capturas de esta tabla se incorporan a `docs/informe/evidencia/` a medida que se
> cierran las etapas. No se enlazan imágenes que no existan en el repositorio.

## 6. Diferencias frente a la infraestructura de AWS

| Aspecto | AWS (Parte 1) | On-premise |
|---|---|---|
| Hosts | Cuatro instancias EC2 | Un host con ZeroTier |
| Entornos | Tres hosts con Compose | Tres namespaces en un clúster k3d |
| Registro | Nexus en el host de herramientas | Nexus en el mismo host, repo `circleguard-docker` |
| Despliegue | `deploy/scripts/*.sh` por SSH | `kubectl apply` desde Jenkins local |
| Acceso | IP pública e ingress | Cloudflare Tunnel + Access, respaldo ZeroTier |
| Costo | Crédito de AWS | Energía del equipo |

El cambio de SSH a `kubectl` obliga a actualizar `Jenkinsfile` y `jenkins/vars/ci.groovy`,
que todavía contienen `sshagent(['cg-envs-ssh'])`. Esa migración es parte de la Parte 2 y
hasta que se complete el pipeline on-prem no se declara operativo.

## 7. Relación con los entregables

- **P1.2 (infraestructura):** el anexo documenta una infraestructura reproducible alternativa
  y explica por qué la de AWS no puede demostrarse en vivo.
- **P2:** la topología de este anexo es la base de los ítems P2-01 (Terraform), P2-02
  (Kubernetes), P2-04 (observabilidad) y P2-08 (pipeline con IaC y `kubectl`).
