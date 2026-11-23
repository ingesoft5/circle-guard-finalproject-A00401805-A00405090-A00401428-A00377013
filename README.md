<!-- CG-DOC: Project overview, setup guide, and architecture summary. -->
# CircleGuard Monorepo

#### **Integrantes:**
* **Daniela Castaño Moreno**
* **Santiago Alejandro Estrada**
* **Faiber Piedrahita Perlaza**
* **Alejandro Quiñones Caicedo**




**Absolute Privacy. High-Speed Containment. Secure Campus.**

CircleGuard is a state-of-the-art university contact tracing and fencing system designed to identify interconnected contact groups ("Circles") and apply rapid health fences while preserving individual anonymity.

---

## Vision & Mission

Our vision is a university campus where health containment speed outpaces lab confirmation timelines without compromising student privacy. CircleGuard leverages campus-native intelligence—class schedules and WiFi infrastructure—to deliver a human-validated, graph-based protection ecosystem.

### Key Differentiators
- **Privacy-as-Code**: Zero real-name exposure outside a secure Health Center vault.
- **Recursive Containment**: Status promotion cascades (Suspect → Probable → Confirmed) that trigger in milliseconds.
- **Campus Integration**: Smart check-ins using existing WiFi AP triangulation and Bluetooth Low Energy (BLE).

---

## Success Metrics

| Metric | Target | Measurement |
|:---|:---|:---|
| **Containment Speed** | < 60 Seconds | Automated test of promotion engine cascade |
| **Privacy Compliance** | 100% Anonymity | Penetration test on graph database (Zero real names) |
| **Check-in Adoption** | > 70% | Analytics on scheduled class contact validation |
| **False Positive Rate** | < 15% | Post-fence surveys of actual vs. suspected contact |
| **System Uptime** | 99.5% | 7:00 AM – 10:00 PM (Academic Peak Hours) |

---

## Architecture Overview

CircleGuard follows a **Microservice Architecture** built on a **Hybrid Data Model**.

### Core Engine
1. **Status Promotion Machine**: Uses **Neo4j** for recursive graph traversals to identify contacts within a 14-day temporal window.
2. **Anonymization Vault**: A segregated **PostgreSQL** vault handles salted-hash identity mapping, compliant with **FERPA** regulations.
3. **Event-Driven Core**: **Apache Kafka** manages asynchronous status changes, audit logs, and notification dispatches.

### Services Directory
- **Auth Service**: Dual-chain LDAP (University) / Local (Guest) auth with Dynamic RBAC.
- **Identity Service**: Cryptographic vault for anonymizing real identities.
- **Promotion Service**: The status engine (Recursive Graph Processing).
- **Notification Service**: Multi-channel dispatcher (Push/Email/SMS).
- **Form Service**: Dynamic health questionnaire engine.
- **Gateway Service**: Campus entry validation via signed, time-limited QR tokens.
- **Dashboard Service**: Geospatial hotspot analytics (Privacy-preserving).
- **File Service**: Secure certificate and document storage (S3-compatible).

---

## Technical Stack

| Layer | Technology | Rationale |
|:---|:---|:---|
| **Backend** | Spring Boot 3.2.4 / Kotlin 1.9.24 / Java 21 | Typed, reactive JVM services. |
| **Graph DB** | Neo4j 5.26 | High-performance recursive traversals unreachable with SQL. |
| **Relational DB**| PostgreSQL 16 | ACID compliant storage for identity and configuration. |
| **Message Bus** | Apache Kafka 3.7.0 (KRaft) | Persistent, audit-trailed event log for status dispatches. |
| **Caching** | Redis 7.2 | L2 distributed cache for rapid entry-gate status validation. |
| **Mobile/Web** | Expo (React Native) | Unified codebase across iOS, Android, and Browser. |
| **Infra** | Docker Compose on-prem (`cg-tools`) + k3d cluster (`cg`) | k3d ejecuta k3s (Kubernetes certificado por la CNCF): la API es idéntica a K8s, pero se empaqueta sobre Docker para operar un host compartido sin `sudo` ni exponer puertos; ver [CI/CD](#cicd-platform) y `infra/onpremise/ONPREMISE.md`. |

---

## Roadmap

### Phase 1: MVP — The Intelligence Core (Current)
- [x] Status Promotion Machine (Suspect → Probable → Confirmed).
- [x] Temporal graph with 14-day TTL edges.
- [x] Multi-channel fence notifications (Push/Email/SMS).
- [ ] Health Center de-identification console.

### Phase 2: Growth — Spatial Intelligence
- [ ] WiFi AP triangulation integration.
- [ ] Campus entry validation (Gatekeeper) QR integration.
- [ ] LMS integration for "Remote Attendance" status automation.

### Phase 3: Vision — Full Ecosystem
- [ ] Off-campus circle detection via P2P Bluetooth.
- [ ] Global Health Dashboard with hotspot visualization.
- [ ] Lab API bridge for automated test result ingestion.

---

## Local Development

### 1. Infrastructure
For the current on-premise platform (Compose `cg-tools` + k3d `cg`, namespaces `cg-dev`/`cg-stage`/`cg-prod`) follow [`infra/onpremise/ONPREMISE.md`](infra/onpremise/ONPREMISE.md) and [`docs/infra/migracion-onpremise.md`](docs/infra/migracion-onpremise.md).

The `deploy/compose/{dev,stage,prod}` stacks are kept as a **local legacy reference** from Parte 1 (they define the same services + middleware for a single machine); they are no longer the delivery target.

To run the legacy local stack, copy the environment template, set `TAG` and `NEXUS_REGISTRY`, and replace every `change-me` placeholder before starting:

- `POSTGRES_PASSWORD`
- `NEO4J_PASSWORD`
- `OPENLDAP_ADMIN_PASSWORD`
- `QR_SECRET`
- `JWT_SECRET`
- `VAULT_SECRET` (use a real value of at least 32 characters)
- `VAULT_SALT`
- `VAULT_HASH_SALT`

Set `ENV_HOST_IP` if the stack must be reachable from outside the host.

```bash
cp deploy/compose/dev/.env.example deploy/compose/dev/.env
docker compose --env-file deploy/compose/dev/.env -f deploy/compose/dev/docker-compose.yml up -d
```
*Middleware includes: PostgreSQL, Neo4j, Kafka (KRaft), Redis, and OpenLDAP.*

**Manual seed (Promotion/Neo4j).** `services/circleguard-promotion-service/src/main/resources/seed_faculties.cypher`
is a manual script: the application does not run it at startup. Execute it from Neo4j Browser
or `cypher-shell` against the local instance when you need faculty test data.

### 2. Build & Run
CircleGuard uses Gradle for parallel builds across services:
```bash
# Start all microservices in parallel
./gradlew bootRun --parallel

# Start a specific service
./gradlew :services:<service-name>:bootRun
```

### 3. API Exploration
Every service exposes an OpenAPI 3.0 interface. Once running, visit:
`http://localhost:<service-port>/swagger-ui/index.html`

---

## Frontend Development

The frontend is built using **Expo (React Native)**, supporting iOS, Android, and Web from a single codebase located in `/mobile`.

### 1. Prerequisites
Ensure you have Node.js installed and dependencies loaded:
```bash
cd mobile
npm install
```

### 2. Run the Application
You can run the app in various modes depending on your target platform:

| Platform | Command | Notes |
|:---|:---|:---|
| **Development Menu** | `npm run start` | Opens the Expo Go start-up menu. |
| **Android** | `npm run android` | Requires Android Studio / Emulator or a connected device. |
| **iOS** | `npm run ios` | Requires macOS with Xcode / Simulator installed. |
| **Web Browser** | `npm run web` | Launches the dashboard/app in your default browser. |

### 3. Testing
To run frontend unit and component tests:
```bash
npm run test
```

---

## Testing

We maintain high system integrity via multi-level testing:

| Command | Scope |
|:---|:---|
| `./gradlew test` | Full unit suite (all services) |
| `./gradlew :services:<name>:test` | Single service unit tests |
| `./gradlew :services:circleguard-gateway-service:integrationTest` | Gateway tests against an ephemeral Redis container |
| `tests/e2e` (Playwright) | Browser flows against the deployed web client and gateway — see [`tests/TESTS.md`](tests/TESTS.md) |
| `tests/security-zap/` | OWASP ZAP baseline — see [`SECURITY-ZAP.md`](tests/security-zap/SECURITY-ZAP.md) |
| `tests/performance-locust/` | Load tests against the deployed environments |

**Note**: Integration tests use **Testcontainers** to spawn ephemeral Neo4j, PostgreSQL, and Redis instances for zero-side-effect validation. The gateway integration suite requires Docker and is excluded from `test` so unit builds do not depend on it.

**WSL (repo on `/mnt/c`)**: the 9p mount makes the classpath too slow for the 3-minute task timeout. Move the build outputs to ext4 with the local init script:

```bash
./gradlew -I gradle/wsl-ext4.init.gradle.kts \
  :services:circleguard-gateway-service:integrationTest
```

---

## Privacy & Compliance

- **FERPA Compliance**: Student identities are never stored in the contact graph.
- **Right to be Forgotten**: Users can trigger complete data purging via the Identity Vault.
- **Temporal Privacy**: All contact edges are automatically purged after 14 days.

---

## Project Structure

```text
circleguard/
├── .github/
│   ├── pull_request_template.md      # Plantilla de cambio y revisión
│   └── workflows/enforce-pr.yml      # Bloquea pushes directos a main
├── .githooks/                        # commit-msg (Conventional Commits) y pre-push
├── services/                         # 8 microservicios Spring Boot/Kotlin
│   ├── circleguard-auth-service/     # Dockerfile multi-stage no-root
│   ├── circleguard-identity-service/
│   ├── circleguard-promotion-service/
│   ├── circleguard-form-service/
│   ├── circleguard-gateway-service/
│   ├── circleguard-notification-service/
│   ├── circleguard-file-service/
│   └── circleguard-dashboard-service/
├── mobile/                           # Cliente Expo (iOS/Android/web) + Dockerfile Nginx
├── Jenkinsfile                       # Pipeline multibranch
├── jenkins/
│   ├── vars/                         # Shared Library: ci, ciServices, ciNotify, ciVersion
│   ├── test/                         # Pruebas unitarias de la Shared Library
│   └── JENKINS.md
├── infra/
│   ├── onpremise/                    # Bootstrap y verificación de cg-tools + k3d (Parte 2)
│   ├── terraform/                    # IaC modular: modules/{compute,network,observability,registry,storage}
│   │                                 # y envs/{dev,stage,prod}
│   ├── bootstrap/                    # SOLO HISTÓRICO (Parte 1, AWS) — ver docs/informe/
│   └── INFRA.md
├── deploy/
│   ├── compose/tools/                # cg-tools: Jenkins, SonarQube, Nexus, registro (on-prem)
│   ├── compose/{dev,stage,prod}/     # Stacks Parte 1: referencia local, no destino de entrega
│   ├── k8s/                          # Kustomize: base, components, overlays, monitoring, form06-dev
│   ├── finops/                       # OpenCost, KEDA y dashboard de costos (bonificación)
│   ├── mesh/istio/                   # Service mesh (bonificación)
│   ├── chaos/                        # Chaos Mesh (bonificación)
│   ├── scripts/                      # deploy, rollback, smoke-test, seed (legacy Parte 1)
│   └── DEPLOY.md
├── tests/
│   ├── e2e/                          # Playwright: login, formulario, acceso, promociones
│   ├── performance-locust/           # Pruebas de carga
│   ├── security-zap/                 # OWASP ZAP baseline
│   └── TESTS.md
├── docs/
│   ├── agil/                         # Sprint 0-2, backlog, métricas y trazabilidad Jira
│   ├── arquitectura/                 # Arquitectura, pipeline y diagnósticos
│   ├── bonificaciones/               # chaos, finops, mesh
│   ├── costos/                       # Política spot y costos
│   ├── diagramas/, infra/, informe/, operaciones/, patrones/
│   ├── release-notes/, standards/
│   └── change-management.md
├── scripts/                          # Verificación de bonificaciones y sincronía Jira
├── gradle/                           # Convenciones JaCoCo y wrapper de Gradle
├── build.gradle.kts, settings.gradle.kts, gradlew
├── BRANCHING-STRATEGY.md
├── README.md
├── CHANGELOG.md
├── IAG_REGISTRO.md
├── Proyecto_Final_CircleGuard.{tex,pdf}
└── sonar-project.properties
```

**Correspondencia con el árbol sugerido del enunciado.** El texto propone `tests/e2e-playwright/` y
`observability/{prometheus,grafana,alerts}/` como *estructura sugerida* (`Proyecto_Final_CircleGuard.tex:287`).
En este repositorio esas responsabilidades están en:

| Enunciado | Ruta real |
|:---|:---|
| `tests/e2e-playwright/` | `tests/e2e/` (suite Playwright) |
| `observability/prometheus/` | `deploy/k8s/monitoring/prometheus.yml` |
| `observability/grafana/` | `deploy/k8s/monitoring/grafana.yml` y `deploy/finops/grafana-cost-dashboard.yml` |
| `observability/alerts/` | `deploy/k8s/monitoring/alertmanager.yml`, `deploy/k8s/base/*-alerts.yml` y `deploy/k8s/components/form-observability/monitoring/form-alerts.yml` |

## Repository conventions

- **Documentación:** todo archivo fuente, script, configuración y documento mantenido lleva un encabezado
  inglés `CG-DOC:` con el propósito del archivo. Los registros históricos y entregables del curso están
  exentos; la sintaxis y los casos están en [`docs/standards/documentation.md`](docs/standards/documentation.md).
- **Control de cambios:** GitHub Flow estricto, `main` protegida, squash merge y al menos una aprobación.
  La especificación canónica está en [`BRANCHING-STRATEGY.md`](BRANCHING-STRATEGY.md).
- **Trazabilidad:** cuando existe correspondencia con Jira, el título del PR incluye la clave `DCG-*`
  del tablero [DCG 67](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67).
  Los estados de los PR no se modifican como parte de tareas de documentación.
- **Registros históricos:** actas Scrum, release notes, `CHANGELOG.md`, `IAG_REGISTRO.md`, evidencias
  y el informe final son documentos de corte: no se reescriben para normalizarlos.

---

## CI/CD Platform

La plataforma corre on-premise en un único host (accesible por ZeroTier):

- `cg-tools` (Docker Compose): Jenkins, SonarQube, Nexus, registro `circleguard-docker` y el relay smee.io del webhook.
- Clúster k3d `cg`: namespaces `cg-dev`, `cg-stage` y `cg-prod` con PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP, los cinco servicios núcleo, los tres reportes y el cliente web; Ingress de Traefik en `:30080` ruteado por `Host` (`<env>.circleguard.local`).

**Por qué k3d y no Kubernetes instalado en el host.** No es una decisión entre «k3s y k8s»: k3d ejecuta k3s, una distribución de Kubernetes certificada por la CNCF, así que la API y los manifiestos son los mismos. La elección es de empaquetado y operación. El host es compartido con otros proyectos y el usuario `docker` no tiene `sudo`; instalar `kubelet`/`containerd` o un clúster `kubeadm` exigiría privilegios y puertos adicionales. k3d corre el `controlplane` y los `agents` como contenedores, expone solo el Ingress en `:30080` y reutiliza el mirror HTTP del registro (`10.147.20.67:5000`).

| Alternativa | Resultado | Motivo |
|:---|:---|:---|
| **k3d (k3s en Docker)** | **Elegida** | Sin `sudo`, sin puertos de API expuestos, mirror de registro ya configurado. |
| k3s nativo en el host | Descartada | Requiere `systemd`/root y puertos `6443`/`10250` en un host compartido. |
| minikube / kind | Descartadas | Añaden una VM o un nodo Docker extra con su propio networking; no aportan sobre k3d aquí. |
| kubeadm / clúster externo (EKS) | Descartadas | Requieren privilegios, automatización IaC adicional o salida a nube; la API es la misma. |

Detalle completo: [`deploy/k8s/K8S.md`](deploy/k8s/K8S.md).

Levantar desde cero en el host on-premise:

```bash
git clone https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git
cd circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
./infra/onpremise/bootstrap-onpremise.sh   # .env.onpremise + Jenkins + SonarQube + Nexus
./infra/onpremise/k3d-cluster-create.sh    # clúster cg + namespaces cg-dev/cg-stage/cg-prod
docker compose --env-file deploy/compose/tools/.env.onpremise \
  -f deploy/compose/tools/docker-compose.onpremise.yml -p cg-tools up -d
kubectl apply -k deploy/k8s/overlays/dev   # y stage/prod según el entorno
./infra/onpremise/verify-onpremise.sh      # chequeo read-only de salud
```

El registro HTTP del host (`10.147.20.67:5000`) lo usa el clúster para jalar imágenes; `k3d image import -c cg` importa las que Jenkins publica.

La infraestructura de la Parte 1 (cuatro instancias EC2, scripts `infra/bootstrap/*`) queda **congelada como evidencia histórica**; no es operativa y se documenta en [`docs/informe/`](docs/informe/) y en el `CHANGELOG.md`.

Documentación:

- [Guía on-premise](infra/onpremise/ONPREMISE.md)
- [Layout de despliegue](deploy/DEPLOY.md)
- [Pruebas](tests/TESTS.md)
- [Terraform](infra/terraform/TERRAFORM.md)
- [Componentes de observabilidad](deploy/k8s/components/form-observability/FORM-OBSERVABILITY.md)
- [Costos](docs/costos/README.md)
- Bonificaciones: [FinOps](docs/bonificaciones/finops/README.md), [Service Mesh](docs/bonificaciones/mesh/README.md), [Chaos](docs/bonificaciones/chaos/README.md)
- [Gestión ágil](docs/agil/AGIL.md)
- [Arquitectura](docs/arquitectura/ARQUITECTURA.md)
- [Pipeline](docs/arquitectura/pipeline.md)
- [Pipeline on-prem (kubectl/Kustomize, sin SSH)](docs/infra/migracion-onpremise.md)
- [Entornos](docs/infra/environments.md)
- [CI/CD setup](docs/infra/ci-cd-setup.md)
- [Runbook](docs/infra/runbook.md)
- [Change management](docs/change-management.md)
- [Release notes](docs/release-notes/RELEASE-NOTES.md)
- [Operaciones](docs/operaciones/OPERACIONES.md)
- [Estándares de documentación](docs/standards/documentation.md)
- Pipeline: [`Jenkinsfile`](Jenkinsfile) y [`jenkins/vars/ci.groovy`](jenkins/vars/ci.groovy)
