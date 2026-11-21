# Backlog CircleGuard

**Proyecto:** CircleGuard
**Tablero:** [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67?filter=&groupBy=none) · [GitHub Project `circle-guard-dasf`](https://github.com/orgs/ingesoft5/projects)
**Última actualización:** 20 de noviembre de 2026

Este archivo mantiene en el repositorio el backlog mínimo verificable. La fuente de verdad
operativa es el tablero (Jira para la Parte 1 y GitHub Project `circle-guard-dasf` para el
seguimiento integrado de P1 y P2); aquí se registran las historias, sus criterios de
aceptación, el estado que soporta la entrega y la evidencia enlazada.

## Parte 1 — Historias priorizadas

| ID | Historia | Criterios de aceptación | Prioridad | Pts | Sprint | Estado | Evidencia |
|---|---|---|---|---|---|---|---|
| DCG-1 | Backlog con historias de usuario | Cada HU tiene objetivo, actor, valor y AC verificables; priorizada en Jira | Alta | 1 | 0 | Done | Este archivo y tablero DCG |
| DCG-2 | Subtareas y criterios de aceptación | Cada HU se descompone en subtareas y sus AC son medibles | Alta | 2 | 0 | Done | Este archivo |
| DCG-4 | Roles Scrum | Responsables SM/DC/equipo asignados y visibles en el tablero | Alta | 1 | 0 | Done | `docs/agil/sprint-0/planning.md` |
| DCG-5 | CI/CD con GitHub Flow | `main` protegida, PR con revisión, checks obligatorios y Jenkinsfile | Alta | 3 | 0 | Done | `Jenkinsfile`, PR [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38), `docs/informe/evidencia/github/github-ruleset-main.png` |
| DCG-6 | Conformar equipo | Cuatro integrantes con disponibilidad y responsabilidades acordadas | Alta | 1 | 0 | Done | `docs/agil/sprint-0/planning.md` |
| DCG-7 | Marco ágil | Scrum operando con sprint, tablero y estados | Alta | 2 | 0 | Done | `docs/informe/evidencia/jira/jira-sprint-1.png` |
| DCG-8 | Estrategia de branching | GitHub Flow, convenciones de rama/commit y hooks documentados | Alta | 2 | 0 | Done | `docs/standards/documentation.md`, `.githooks/` |
| DCG-77 | HU-AUTH-01: diagnosticar `auth-service` | Estructura, endpoints, dependencias y compilación documentados | Alta | 5 | 1 | Done | `docs/arquitectura/diagnostico-auth-service.md`, PR [#5](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/5) |
| DCG-304 | HU-PROMO-01: diagnosticar `promotion-service` | Estructura, endpoints, dependencias, eventos y compilación documentados | Alta | 5 | 1 | Done | `docs/arquitectura/diagnostico-promotion-service.md`, PR [#16](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/16) |
| DCG-309 | HU-PROMO-04: desplegar `promotion-service` | Imagen inmutable, middleware PostgreSQL/Neo4j/Kafka/Redis, healthchecks y flujo E2E en dev/stage/prod | Alta | 8 | 1 | Done | PR [#31](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/31), `docs/informe/evidencia/HU-PROMO-04/` |
| HU-INFRA-02 | Habilitar servicios núcleo en Jenkins | Job `circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`, catálogo con `docker`/`deploy`, publicación de las 8 imágenes y despliegue de los 5 núcleos + web | Alta | 8 | 1 | Done | PRs [#34](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/34), [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38), [#39](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/39), [#40](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/40), [#41](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/41) |
| HU-AUTH-03/04 | Empaquetar y desplegar `auth-service` | Dockerfile multi-stage no-root, healthcheck, integración con PostgreSQL/OpenLDAP, compose y E2E de login | Alta | 8 | 1 | Done | `services/circleguard-auth-service/Dockerfile`, PRs [#29](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/29), [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38), `tests/e2e/login.spec.ts` |
| HU-FORM-03 | Empaquetar `form-service` | Dockerfile multi-stage no-root, healthcheck, compose con PostgreSQL/Kafka y pruebas E2E del cuestionario | Alta | 5 | 1 | Done | `services/circleguard-form-service/Dockerfile`, PRs [#3](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/3), [#34](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/34), `tests/e2e/health-form.spec.ts` |
| HU-ID-03/04 | Empaquetar y desplegar `identity-service` | Dockerfile multi-stage no-root, healthcheck, Sonar, JaCoCo ≥ 60 %, compose con PostgreSQL y validación E2E | Alta | 8 | 1 | Done | PRs [#30](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/30), [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38), `docs/informe/evidencia/HU-ID-03/` |
| HU-GATEWAY-04/05 | Empaquetar gateway y pipeline CI | Imagen no-root, Jenkins multibranch, Sonar + quality gate, integración Redis y despliegue | Alta | 8 | 1 | Done | PRs [#23](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/23), [#26](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/26), [#41](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/41) |
| HU-FILE-03 | Pipeline CI del file-service con quality gate e idempotencia | Multibranch, Shared Library, JaCoCo ≥ 60 %, gate bloqueante, selectivo, rerun idempotente y wrapper on-prem documentado; inmutabilidad solo para semver | Alta | 5 | 1 | En revisión | Issue [#135](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/issues/135), PR [#134](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/134), `docs/informe/HU-FILE-03.md` |
| E1.2 | Infraestructura con Bash | Scripts idempotentes, 4 hosts, Jenkins/Sonar/Nexus/registry/ingress y entornos aislados | Alta | 13 | 0–1 | Done | PRs [#15](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/15), [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38), [#43](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/43); `docs/informe/E1.2-Informe-Final.pdf` |
| E1.3 | Empaquetamiento inmutable | Dockerfiles multi-stage, usuario no root, tag semver, imágenes en Nexus y compose con healthchecks | Alta | 8 | 1 | Done | PRs [#18](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/18), [#30](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/30), [#38](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/38); `services/*/Dockerfile` |
| E1.5 | CD y promoción | Deploy dev, E2E, stage, aprobación manual, prod, tag Git y rollback | Alta | 8 | 1 | Done | PRs [#31](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/31), [#39](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/39); `Jenkinsfile` (etapas stage, prod y `ROLLBACK_TAG`) |
| E1.6 | Pruebas | Unitarias ≥ 60 % en 5 núcleos, 3 integraciones Testcontainers y 3 flujos E2E Playwright | Alta | 13 | 1 | Done | PRs [#17](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/17), [#19](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/19), [#22](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/22), [#47](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/47), [#52](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/52), [#53](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/53); `tests/e2e/` |
| E1.7 | Change management y release notes | Plantilla de cambio, política de aprobación, release notes automáticas y etiquetado | Media | 5 | 1 | Done | PR [#42](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/42); `docs/change-management.md`, `docs/release-notes/` |
| E1.8 | Documentación | README operativo, arquitectura, pipeline y bitácora IAG | Media | 5 | 1 | Done | PRs [#43](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/43), [#44](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/44), [#45](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/45); `IAG_REGISTRO.md`, `docs/informe/E1.2-Informe-Final.pdf` |

> **Nota de cierre P1 (16 de noviembre de 2026):** los ítems que en la entrega del 8 de
> noviembre figuraban “En revisión” quedaron integrados en `main` mediante los PR listados.
> La cobertura de `promotion-service` se elevó de 62.01 % a 76.13 % de líneas con
> `SpatialServiceTest`, `BuildingServiceTest` y `AccessPointServiceTest`, y la de los demás
> núcleos supera el 60 % tras excluir el código generado por Lombok (PR #53).

## Parte 2 — Backlog preliminar (on-prem)

La Parte 2 migra la plataforma a infraestructura on-premise con Kubernetes, añade
resiliencia, observabilidad y seguridad, y automatiza el aprovisionamiento. Los ítems se
gestionan en el GitHub Project `circle-guard-dasf`.

| ID | Historia | Criterios de aceptación | Prioridad | Pts | Sprint | Estado |
|---|---|---|---|---|---|---|
| P2-01 | Infraestructura como código (Terraform) | Recursos de los tres ambientes declarados, backend remoto y `terraform plan` reproducible | Alta | 13 | 2 | Pendiente |
| P2-02 | Despliegue en Kubernetes (k3d) | Manifiestos para 8 servicios, namespaces `cg-dev`/`cg-stage`/`cg-prod`, secrets e ingress | Alta | 13 | 2 | Pendiente |
| P2-03 | Patrones de resiliencia | Tres patrones implementados (p. ej. circuit breaker, retry, bulkhead) con configuración externalizada | Alta | 8 | 2 | Pendiente |
| P2-04 | Observabilidad | Prometheus, Grafana con dashboards y al menos tres alertas operativas | Alta | 8 | 2 | Pendiente |
| P2-05 | Trazas y logs centralizados | Jaeger y Loki integrados con los servicios desplegados | Media | 5 | 2 | Pendiente |
| P2-06 | Seguridad y calidad | Trivy sobre imágenes y ZAP sobre el flujo principal, sin hallazgos críticos abiertos | Alta | 8 | 2 | Pendiente |
| P2-07 | Pruebas de carga | Locust con escenario documentado y resultados contra `cg-stage` | Media | 5 | 2 | Pendiente |
| P2-08 | Pipeline con IaC y Kubernetes | Jenkins aplica Terraform y despliega con `kubectl`, con rollback verificable | Alta | 8 | 2 | Pendiente |
| P2-09 | Documentación y Sprint 2 | Sprint 2 planning/review/retrospective, runbook on-prem, costos y resumen en inglés | Media | 8 | 2 | Pendiente |
| P2-10 | Demostración final | Guion de sustentación, evidencias enlazadas y ensayo completo del flujo | Alta | 5 | 2 | Pendiente |

## Definition of Done

Una historia se considera Done cuando:

1. El código está integrado en `main` mediante PR con al menos una aprobación (política actualizada para el Sprint 2: esperar dos revisores demoraba demasiado los PRs; ver CHANGELOG).
2. Los checks de Jenkins (build, pruebas, Sonar, quality gate) están verdes.
3. La imagen, si aplica, está publicada en Nexus con tag semver y usuario no root.
4. El despliegue en dev está sano y el flujo E2E correspondiente pasa.
5. La evidencia queda enlazada desde `docs/informe/evidencia/` o desde el informe.
6. La documentación operativa y la bitácora IAG se actualizan.

## Estados

- **Done:** cumple el DoD y la evidencia está enlazada.
- **En revisión:** el código está en una rama o PR, pero falta merge, check o evidencia.
- **En progreso:** hay trabajo iniciado sin PR verificable.
- **Pendiente:** no iniciado.
