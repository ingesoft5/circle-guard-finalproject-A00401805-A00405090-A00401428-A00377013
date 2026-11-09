# Sprint 1 — Review

**Sprint:** DCG Sprint 1
**Periodo:** 3–8 de noviembre de 2026
**Fecha de revisión:** 8 de noviembre de 2026
**Tablero:** [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67?filter=&groupBy=none)

## Objetivo revisado

Pipeline CI/CD de punta a punta, servicios núcleo desplegados con su middleware y cliente
web, pruebas automatizadas y documentación de la Parte 1.

## Incremento demostrado

| Ítem | Resultado | Evidencia |
|---|---|---|
| Pipeline multibranch | Job `circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013`, etapas build, tests, Sonar, integración, package, publish, Docker, deploy, E2E, stage, aprobación y prod | `Jenkinsfile`, `jenkins/vars/ci.groovy` |
| Imágenes inmutables | Dockerfiles multi-stage no-root para los 8 servicios y el cliente web; tag semver; HEALTHCHECK | `services/*/Dockerfile`, `mobile/Dockerfile` |
| Stack dev/stage/prod | PostgreSQL, Neo4j, Kafka, Redis, OpenLDAP, auth, identity, promotion, form, gateway y web | `deploy/compose/*/docker-compose.yml` |
| Pruebas unitarias | JaCoCo ≥ 60 % en auth, identity, gateway y form; promotion ≥ 70 % | `build.gradle.kts` de cada servicio |
| Integración | Testcontainers en auth, form, gateway, identity y promotion | tareas `integrationTest` |
| E2E | Login, cuestionario de salud y validación de acceso con Playwright | `tests/e2e/*.spec.ts` |
| Release notes | Generación automática desde commits convencionales | `docs/release-notes/`, stage `Release notes` |
| Documentación | README, arquitectura, pipeline, Sprint 0/1 y change management | `docs/` |

## Métricas

| Indicador | Resultado |
|---|---|
| Ítems comprometidos | 10 |
| Ítems cerrados en repositorio | 7 |
| Ítems en revisión por PR | 3 |
| Cobertura de servicios núcleo | ≥ 60 % (promotion ≥ 70 %) |
| Flujos E2E | 3 |
| Integraciones Testcontainers | 5 servicios |

> El estado "en revisión" refleja trabajo integrado en la rama `feat/parte1-integracion`
> que aún requiere merge a `main` y captura de evidencia. No se declara Done hasta que el
> PR tenga checks verdes y aprobación.

## Demostración

1. Ejecutar el pipeline con `SERVICE=all`, `DEPLOY=true` en `main`.
2. Verificar publicación de imágenes en Nexus con tag semver.
3. Abrir dev y recorrer login, cuestionario y validación de acceso.
4. Confirmar el quality gate de Sonar y el reporte de cobertura.
5. Promover a stage, aprobar prod y comprobar el rollback con `ROLLBACK_TAG`.

## Pendientes para el cierre

- Merge del PR de la rama `feat/parte1-integracion`.
- Capturas reales de Jira (Sprint 1, backlog, historias y retrospectiva).
- Capturas reales de GitHub (ruleset, PR, aprobación y checks).
- Video de demostración y PDF consolidado del informe.
- Actualización de `docs/informe/E1.2-Informe-Final` con el estado actual del pipeline.
