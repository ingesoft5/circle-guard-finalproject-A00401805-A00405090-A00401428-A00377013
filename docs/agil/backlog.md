# Backlog CircleGuard — Parte 1

**Proyecto:** CircleGuard
**Tablero:** [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67?filter=&groupBy=none)
**Última actualización:** 9 de noviembre de 2026

Este archivo mantiene en el repositorio el backlog mínimo verificable de la Parte 1.
La fuente de verdad operativa sigue siendo Jira; aquí se registran las historias, sus
criterios de aceptación y el estado que soporta la entrega.

## Historias priorizadas

| ID | Historia | Criterios de aceptación | Prioridad | Pts | Sprint | Estado |
|---|---|---|---|---|---|---|
| DCG-1 | Backlog con historias de usuario | Cada HU tiene objetivo, actor, valor y AC verificables; priorizada en Jira | Alta | 1 | 0 | Done |
| DCG-2 | Subtareas y criterios de aceptación | Cada HU se descompone en subtareas y sus AC son medibles | Alta | 2 | 0 | Done |
| DCG-4 | Roles Scrum | Responsables SM/DC/equipo asignados y visibles en el tablero | Alta | 1 | 0 | Done |
| DCG-5 | CI/CD con GitHub Flow | `main` protegida, PR con revisión, checks obligatorios y Jenkinsfile | Alta | 3 | 0 | Done |
| DCG-6 | Conformar equipo | Cuatro integrantes con disponibilidad y responsabilidades acordadas | Alta | 1 | 0 | Done |
| DCG-7 | Marco ágil | Scrum operando en Jira con sprint, tablero y estados | Alta | 2 | 0 | Done |
| DCG-8 | Estrategia de branching | GitHub Flow, convenciones de rama/commit y hooks documentados | Alta | 2 | 0 | Done |
| DCG-77 | HU-AUTH-01: diagnosticar `auth-service` | Estructura, endpoints, dependencias y compilación documentados en `docs/arquitectura/diagnostico-auth-service.md` | Alta | 5 | 1 | Done |
| DCG-304 | HU-PROMO-01: diagnosticar `promotion-service` | Estructura, endpoints, dependencias, eventos y compilación documentados | Alta | 5 | 1 | Done |
| DCG-309 | HU-PROMO-04: desplegar `promotion-service` | Imagen inmutable, middleware PostgreSQL/Neo4j/Kafka/Redis, healthchecks y flujo E2E en dev/stage/prod | Alta | 8 | 1 | Done |
| HU-INFRA-02 | Habilitar servicios núcleo en Jenkins | Job `circleguard`, catálogo con `docker`/`deploy`, publicación de las 8 imágenes y despliegue de los 5 núcleos + web | Alta | 8 | 1 | En revisión (PR #34 integrado en `feat/parte1-integracion`) |
| HU-AUTH-03/04 | Empaquetar y desplegar `auth-service` | Dockerfile multi-stage no-root, healthcheck, integración con PostgreSQL/OpenLDAP, compose y E2E de login | Alta | 8 | 1 | En revisión |
| HU-FORM-03 | Empaquetar `form-service` | Dockerfile multi-stage no-root, healthcheck, compose con PostgreSQL/Kafka y pruebas E2E del cuestionario | Alta | 5 | 1 | En revisión |
| HU-ID-03/04 | Empaquetar y desplegar `identity-service` | Dockerfile multi-stage no-root, Sonar, JaCoCo ≥ 60 %, compose con PostgreSQL y validación E2E | Alta | 8 | 1 | En revisión |
| HU-GATEWAY-04/05 | Empaquetar gateway y pipeline CI | Imagen no-root, Jenkins multibranch, Sonar + quality gate, integración Redis y despliegue | Alta | 8 | 1 | Done en `main` |
| E1.2 | Infraestructura AWS con Bash | Scripts idempotentes, 4 hosts, Jenkins/Sonar/Nexus/registry/ingress y entornos aislados | Alta | 13 | 0–1 | Done (evidencia pendiente de actualizar) |
| E1.3 | Empaquetamiento inmutable | Dockerfiles multi-stage, usuario no root, tag semver, imágenes en Nexus y compose con healthchecks | Alta | 8 | 1 | En revisión |
| E1.5 | CD y promoción | Deploy dev, E2E, stage, aprobación manual, prod, tag Git y rollback | Alta | 8 | 1 | En revisión |
| E1.6 | Pruebas | Unitarias ≥ 60 % en 5 núcleos, 3 integraciones Testcontainers y 3 flujos E2E Playwright | Alta | 13 | 1 | En progreso |
| E1.7 | Change management y release notes | Plantilla de cambio, política de aprobación, release notes automáticas y etiquetado | Media | 5 | 1 | En revisión |
| E1.8 | Documentación | README operativo, arquitectura, pipeline y bitácora IAG | Media | 5 | 1 | En revisión |

## Definition of Done

Una historia se considera Done cuando:

1. El código está integrado en `main` mediante PR con al menos una aprobación.
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
