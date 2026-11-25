<!-- CG-DOC: Revisión de Sprint 1 — CircleGuard. -->
# Sprint 1 — Revisión

**Sprint:** DCG Sprint 1
**Tablero:** [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67?filter=&groupBy=none)
**Acta completa:** [Sprint-Review-Sprint-1.docx](Sprint-Review-Sprint-1.docx)

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
| Ítems cerrados en repositorio | 10 |
| Ítems en revisión por PR | 0 |
| Cobertura de servicios núcleo | ≥ 60 % (promotion 76.13 % de líneas) |
| Flujos E2E | 4 (`login`, `health-form`, `access-validation`, `promotion-flow`) |
| Integraciones Testcontainers | 5 servicios |

> **Cierre:** el trabajo que estaba en la rama
> `feat/parte1-integracion` quedó integrado en `main` con el PR #38 y los PR de seguimiento
> #39–#45, #47, #52 y #53. El backlog actualizado en `docs/agil/backlog.md` enlaza cada
> historia con su PR y su evidencia, por lo que no quedan ítems en revisión dentro del
> alcance del Sprint 1.

## Demostración

1. Ejecutar el pipeline con `SERVICE=all`, `DEPLOY=true` en `main`.
2. Verificar publicación de imágenes en Nexus con tag semver.
3. Abrir dev y recorrer login, cuestionario y validación de acceso.
4. Confirmar el quality gate de Sonar y el reporte de cobertura.
5. Promover a stage, aprobar prod y comprobar el rollback con `ROLLBACK_TAG`.

## Cierre

- [x] Merge del PR de la rama `feat/parte1-integracion` (PR #38).
- [x] Capturas reales de Jira (Sprint 1, backlog, historias y retrospectiva) incorporadas al informe.
- [x] Capturas reales de GitHub (ruleset, PR, aprobación y checks) en `docs/informe/evidencia/github/`.
- [x] `docs/informe/CircleGuard_INFORME_FINAL.pdf` actualizado con el estado actual del pipeline (release `1.2.8`).
- [x] Modalidad de sustentación oral declarada en la sección P1.8 del informe.

> La sustentación oral es un evento de entrega, no un artefacto del repositorio.
> El PDF consolidado ya contiene la evidencia y la modalidad declarada.
