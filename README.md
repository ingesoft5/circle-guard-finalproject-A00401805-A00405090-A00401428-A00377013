<!-- CG-DOC: Project overview, setup guide, and architecture summary. -->
# CircleGuard Monorepo

#### **Members:**
* **Daniela Castaño Moreno**
* **Santiago Alejandro Estrada**
* **Faiber Piedrahita Perlaza**
* **Alejandro Quiñones Caicedo**




**Absolute Privacy. High-Speed Containment. Secure Campus.**

CircleGuard is a state-of-the-art university contact tracing and fencing system designed to identify interconnected contact groups ("Circles") and apply rapid health fences while preserving individual anonymity.

---

## Deployment context: from AWS to on-premise

Part 1 was developed and deployed on AWS using the scripts in `infra/bootstrap/`. About half an hour before delivery, the AWS account was blocked and we lost access. Following the professor's suggestion, we rebuilt and completed the entire on-premise platform on the room 205M computer, with private address `10.147.20.67` (ZeroTier).

There we rebuilt and verified Jenkins, SonarQube, Nexus, the Docker registry, and the k3d cluster with the namespaces `cg-dev`, `cg-stage`, and `cg-prod`. The AWS scripts and evidence are kept as a historical record of Part 1; the current platform and all of Part 2 run on-premise. The technical detail is in [`docs/infra/migracion-onpremise.md`](docs/infra/migracion-onpremise.md).

---

## Repositorio público

El proyecto se entrega en un repositorio público de la organización `ingesoft5`.
El docente autorizó el uso de GitHub rulesets sin costo para proteger `main` en este
tipo de repositorio; la política vigente (una aprobación y el check obligatorio de
Jenkins) se describe en [`BRANCHING-STRATEGY.md`](BRANCHING-STRATEGY.md).

No hay credenciales versionadas: los secretos se guardan en archivos `.env` fuera
del control de versiones, en variables del pipeline y en los `Secret` del clúster.
En el repositorio solo se incluyen plantillas `.env.example` con marcadores de
posición.

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
| **Infra** | Docker Compose on-prem (`cg-tools`) + k3d cluster (`cg`) | k3d runs k3s (a CNCF-certified Kubernetes distribution): the API is identical to K8s, but it is packaged over Docker to operate on a shared host without `sudo` or exposing ports; see [CI/CD](#cicd-platform) and `infra/onpremise/ONPREMISE.md`. |

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

The `deploy/compose/{dev,stage,prod}` stacks are kept as a **local legacy reference** from Part 1 (they define the same services + middleware for a single machine); they are no longer the delivery target.

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
│   ├── pull_request_template.md      # Change and review template
│   └── workflows/enforce-pr.yml      # Blocks direct pushes to main
├── .githooks/                        # commit-msg (Conventional Commits) and pre-push
├── services/                         # 8 Spring Boot/Kotlin microservices
│   ├── circleguard-auth-service/     # Multi-stage non-root Dockerfile
│   ├── circleguard-identity-service/
│   ├── circleguard-promotion-service/
│   ├── circleguard-form-service/
│   ├── circleguard-gateway-service/
│   ├── circleguard-notification-service/
│   ├── circleguard-file-service/
│   └── circleguard-dashboard-service/
├── mobile/                           # Expo client (iOS/Android/web) + Nginx Dockerfile
├── Jenkinsfile                       # Multibranch pipeline
├── jenkins/
│   ├── vars/                         # Shared Library: ci, ciServices, ciNotify, ciVersion
│   ├── test/                         # Shared Library unit tests
│   └── JENKINS.md
├── infra/
│   ├── onpremise/                    # Bootstrap and verification of cg-tools + k3d (Part 2)
│   ├── terraform/                    # Modular IaC: modules/{compute,network,observability,registry,storage}
│   │                                 # and envs/{dev,stage,prod}
│   ├── bootstrap/                    # HISTORICAL ONLY (Part 1, AWS) — see docs/informe/
│   └── INFRA.md
├── deploy/
│   ├── compose/tools/                # cg-tools: Jenkins, SonarQube, Nexus, registry (on-prem)
│   ├── compose/{dev,stage,prod}/     # Part 1 stacks: local reference, not a delivery target
│   ├── k8s/                          # Kustomize: base, components, overlays, monitoring, form06-dev
│   ├── finops/                       # OpenCost, KEDA and cost dashboard (bonus)
│   ├── mesh/istio/                   # Service mesh (bonus)
│   ├── chaos/                        # Chaos Mesh (bonus)
│   ├── scripts/                      # deploy, rollback, smoke-test, seed (legacy Part 1)
│   └── DEPLOY.md
├── tests/
│   ├── e2e/                          # Playwright: login, form, access, promotions
│   ├── performance-locust/           # Load tests
│   ├── security-zap/                 # OWASP ZAP baseline
│   └── TESTS.md
├── docs/
│   ├── agil/                         # Sprint 0-2, backlog, metrics and Jira traceability
│   ├── arquitectura/                 # Architecture, pipeline and diagnostics
│   ├── bonificaciones/               # chaos, finops, mesh
│   ├── costos/                       # Spot policy and costs
│   ├── diagramas/, infra/, informe/, operaciones/, patrones/
│   ├── release-notes/, standards/
│   └── change-management.md
├── scripts/                          # Bonus checks and Jira sync
├── gradle/                           # JaCoCo conventions and Gradle wrapper
├── build.gradle.kts, settings.gradle.kts, gradlew
├── BRANCHING-STRATEGY.md
├── README.md
├── CHANGELOG.md
├── IAG_REGISTRO.md
├── Proyecto_Final_CircleGuard.{tex,pdf}
└── sonar-project.properties
```

**Mapping to the structure suggested in the assignment.** The text proposes `tests/e2e-playwright/` and
`observability/{prometheus,grafana,alerts}/` as a *suggested structure* (`Proyecto_Final_CircleGuard.tex:287`).
In this repository those responsibilities live at:

| Suggested structure | Actual path |
|:---|:---|
| `tests/e2e-playwright/` | `tests/e2e/` (Playwright suite) |
| `observability/prometheus/` | `deploy/k8s/monitoring/prometheus.yml` |
| `observability/grafana/` | `deploy/k8s/monitoring/grafana.yml` and `deploy/finops/grafana-cost-dashboard.yml` |
| `observability/alerts/` | `deploy/k8s/monitoring/alertmanager.yml`, `deploy/k8s/base/*-alerts.yml`, and `deploy/k8s/components/form-observability/monitoring/form-alerts.yml` |

## Repository conventions

- **Documentation:** every maintained source file, script, configuration, and document carries an English
  `CG-DOC:` header describing the file's purpose. Historical records and course deliverables are exempt;
  syntax and examples are in [`docs/standards/documentation.md`](docs/standards/documentation.md).
- **Change control:** strict GitHub Flow, protected `main`, squash merge, and at least one approval.
  The canonical specification is in [`BRANCHING-STRATEGY.md`](BRANCHING-STRATEGY.md).
- **Traceability:** when a Jira item applies, the PR title includes the `DCG-*` key from the
  [DCG 67 board](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67).
  PR states are not modified as part of documentation tasks.
- **Historical records:** Scrum minutes, release notes, `CHANGELOG.md`, `IAG_REGISTRO.md`, evidence,
  and the final report are cutoff documents: they are not rewritten to normalize them.

---

## CI/CD Platform

The platform runs on-premise on a single host (reachable via ZeroTier):

- `cg-tools` (Docker Compose): Jenkins, SonarQube, Nexus, the `circleguard-docker` registry, and the smee.io webhook relay.
- k3d cluster `cg`: namespaces `cg-dev`, `cg-stage`, and `cg-prod` with PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP, the five core services, the three reports, and the web client; Traefik Ingress on `:30080` routed by `Host` (`<env>.circleguard.local`).

**Why k3d and not Kubernetes installed on the host.** This is not a choice between "k3s and k8s": k3d runs k3s, a CNCF-certified Kubernetes distribution, so the API and manifests are the same. The decision is about packaging and operation. The host is shared with other projects and the `docker` user has no `sudo`; installing `kubelet`/`containerd` or a `kubeadm` cluster would require privileges and additional ports. k3d runs the `controlplane` and `agents` as containers, exposes only the Ingress on `:30080`, and reuses the registry HTTP mirror (`10.147.20.67:5000`).

| Alternative | Outcome | Reason |
|:---|:---|:---|
| **k3d (k3s in Docker)** | **Selected** | No `sudo`, no exposed API ports, registry mirror already configured. |
| Native k3s on the host | Rejected | Requires `systemd`/root and ports `6443`/`10250` on a shared host. |
| minikube / kind | Rejected | Add a VM or an extra Docker node with its own networking; no advantage over k3d here. |
| kubeadm / external cluster (EKS) | Rejected | Require privileges, extra IaC automation, or cloud egress; the API is the same. |

Full details: [`deploy/k8s/K8S.md`](deploy/k8s/K8S.md).

To bring it up from scratch on the on-premise host:

```bash
git clone https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git
cd circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013
./infra/onpremise/bootstrap-onpremise.sh   # .env.onpremise + Jenkins + SonarQube + Nexus
./infra/onpremise/k3d-cluster-create.sh    # cluster cg + namespaces cg-dev/cg-stage/cg-prod
docker compose --env-file deploy/compose/tools/.env.onpremise \
  -f deploy/compose/tools/docker-compose.onpremise.yml -p cg-tools up -d
kubectl apply -k deploy/k8s/overlays/dev   # and stage/prod as appropriate
./infra/onpremise/verify-onpremise.sh      # read-only health check
```

The host HTTP registry (`10.147.20.67:5000`) is used by the cluster to pull images; `k3d image import -c cg` imports the images published by Jenkins.

The Part 1 infrastructure (four EC2 instances, `infra/bootstrap/*` scripts) is **frozen as historical evidence**; it is not operational and is documented in [`docs/informe/`](docs/informe/) and `CHANGELOG.md`.

Documentation:

- [On-premise guide](infra/onpremise/ONPREMISE.md)
- [Deployment layout](deploy/DEPLOY.md)
- [Tests](tests/TESTS.md)
- [Terraform](infra/terraform/TERRAFORM.md)
- [Observability components](deploy/k8s/components/form-observability/FORM-OBSERVABILITY.md)
- [Costs](docs/costos/README.md)
- Bonuses: [FinOps](docs/bonificaciones/finops/README.md), [Service Mesh](docs/bonificaciones/mesh/README.md), [Chaos](docs/bonificaciones/chaos/README.md)
- [Agile management](docs/agil/AGIL.md)
- [Architecture](docs/arquitectura/ARQUITECTURA.md)
- [Pipeline](docs/arquitectura/pipeline.md)
- [On-prem pipeline (kubectl/Kustomize, no SSH)](docs/infra/migracion-onpremise.md)
- [Environments](docs/infra/environments.md)
- [CI/CD setup](docs/infra/ci-cd-setup.md)
- [Runbook](docs/infra/runbook.md)
- [Change management](docs/change-management.md)
- [Release notes](docs/release-notes/RELEASE-NOTES.md)
- [Operations](docs/operaciones/OPERACIONES.md)
- [Documentation standards](docs/standards/documentation.md)
- Pipeline: [`Jenkinsfile`](Jenkinsfile) and [`jenkins/vars/ci.groovy`](jenkins/vars/ci.groovy)

### Final delivery artifacts

- [Final report (DOCX)](docs/informe/CircleGuard_INFORME_FINAL.docx) and [PDF](docs/informe/CircleGuard_INFORME_FINAL.pdf) — consolidated Part 1 + Part 2, ordered and with justified raw body text.
- [Final presentation (PPTX)](docs/informe/CircleGuard_PRESENTACION_FINAL.pptx) — 20 slides with speaker notes.
- [IAG registry](IAG_REGISTRO.md) — individual AI-usage traceability, including the rewritten gateway/file-service section.
- [Coverage index](docs/informe/evidencia/trazabilidad/coverage-reports.md) — JaCoCo artifacts for the core services, including `promotion`.
- [On-prem cluster status](docs/informe/evidencia/validacion-final/) — final validated cluster, pipeline, and observability evidence.
