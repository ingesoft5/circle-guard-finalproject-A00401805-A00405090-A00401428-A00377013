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
| **Infra** | Docker Compose on AWS EC2 | Four isolated hosts: tools, dev, stage, prod. |

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
Ensure Docker is installed. Copy the environment template, set `TAG` and `NEXUS_REGISTRY`, and replace every `change-me` placeholder before starting:

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

> `docker-compose.legacy.yml` is a legacy local stack kept for historical reference; the supported stack lives in `deploy/compose/`.

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
├── services/                         # 8 microservicios Spring Boot/Kotlin
│   ├── circleguard-auth-service/     # Dockerfile multi-stage no-root
│   ├── circleguard-identity-service/
│   ├── circleguard-promotion-service/
│   ├── circleguard-form-service/
│   ├── circleguard-gateway-service/
│   ├── circleguard-notification-service/
│   ├── circleguard-file-service/
│   └── circleguard-dashboard-service/
├── mobile/                           # Cliente Expo + Dockerfile Nginx no-root
├── Jenkinsfile                       # Pipeline multibranch
├── jenkins/vars/                     # Shared Library circleguard
│   ├── ci.groovy
│   ├── ciServices.groovy
│   ├── ciNotify.groovy
│   └── ciVersion.groovy
├── infra/bootstrap/                  # AWS, tools, entornos y credenciales
├── deploy/
│   ├── compose/{tools,dev,stage,prod}/docker-compose.yml
│   ├── compose/postgres/init-databases.sh
│   └── scripts/{deploy,rollback,smoke-test,seed-visitor}.sh
├── tests/e2e/                        # Playwright: login, formulario, acceso
├── docs/
│   ├── agil/                         # Sprint 0, Sprint 1, backlog
│   ├── arquitectura/                 # Arquitectura y pipeline
│   ├── infra/                        # Entornos, CI/CD, runbook
│   ├── informe/                      # Informe y evidencias
│   └── release-notes/                # Notas generadas por versión
├── .githooks/                        # commit-msg y pre-push
├── BRANCHING-STRATEGY.md
├── CHANGELOG.md
├── README.md
└── IAG_REGISTRO.md
```

## CI/CD Platform

La plataforma corre en cuatro instancias EC2:

- `cg-tools`: Jenkins, SonarQube, Nexus, registro privado e ingress.
- `cg-dev`, `cg-stage`, `cg-prod`: stack aislado con PostgreSQL, Neo4j, Kafka, Redis,
  OpenLDAP, los cinco servicios núcleo y el cliente web.

Reconstruir desde cero:

```bash
export AWS_REGION=us-east-2
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export GITHUB_USER=...
export GITHUB_TOKEN=...
./infra/bootstrap/bootstrap-all.sh
```

Rotar credenciales de GitHub y sincronizar `tools.env`:

```bash
./infra/bootstrap/update-credentials.sh --apply
```

Documentación:

- [Gestión ágil](docs/agil/README.md)
- [Arquitectura](docs/arquitectura/README.md)
- [Pipeline](docs/arquitectura/pipeline.md)
- [Entornos](docs/infra/environments.md)
- [CI/CD setup](docs/infra/ci-cd-setup.md)
- [Runbook](docs/infra/runbook.md)
- [Change management](docs/change-management.md)
- [Release notes](docs/release-notes/README.md)
- [Índice de documentación](docs/README.md)
- [Índice de archivos](docs/FILE_INDEX.md)
- Pipeline: [`Jenkinsfile`](Jenkinsfile) y [`jenkins/vars/ci.groovy`](jenkins/vars/ci.groovy)
