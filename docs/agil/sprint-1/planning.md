# Sprint 1 — Planning

**Proyecto:** CircleGuard
**Sprint:** DCG Sprint 1
**Periodo:** 3–8 de noviembre de 2026
**Equipo:** Daniela Castaño Moreno, Santiago Alejandro Estrada, Faiber Piedrahita Perlaza y Alejandro Quiñones Caicedo
**Tablero:** [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67?filter=&groupBy=none)

## Objetivo

Dejar el pipeline CI/CD funcionando de punta a punta para los servicios núcleo,
empaquetar y desplegar auth, identity, promotion, form y gateway con su middleware y el
cliente web en dev/stage/prod, y completar la documentación y evidencias de la Parte 1.

## Capacidad

| Indicador | Valor |
|---|---|
| Duración | 6 días |
| Integrantes | 4 |
| Capacidad acordada | 40 puntos |
| Reserva para soporte e imprevistos | 20 % |

## Ítems comprometidos

| Clave | Ítem | Pts | Responsable |
|---|---|---|---|
| HU-INFRA-02 | Job `circleguard`, catálogo de servicios y publicación de imágenes | 8 | DC |
| HU-AUTH-03/04 | Empaquetar y desplegar auth, validar login E2E | 8 | Equipo |
| HU-ID-03/04 | Empaquetar y desplegar identity, Sonar y JaCoCo | 8 | Equipo |
| HU-PROMO-04 | Desplegar promotion con PostgreSQL/Neo4j/Kafka/Redis | 8 | Equipo |
| HU-FORM-03 | Empaquetar form y probar el cuestionario | 5 | Equipo |
| HU-GATEWAY-04/05 | Imagen del gateway, Sonar, quality gate e integración Redis | 5 | Equipo |
| E1.3 | Empaquetamiento inmutable y tags semver | 5 | DC |
| E1.5 | CD, promoción, aprobación y rollback | 5 | DC |
| E1.6 | Unitarias, integración y E2E Playwright | 8 | Equipo |
| E1.7/E1.8 | Change management, release notes, README y diagramas | 5 | DC |

## Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Conflictos entre ramas de servicios | Alto | Integrar en una rama de parte 1 y resolver compose/CI en conjunto |
| Testcontainers sin Docker en el host | Medio | Validar Docker antes del pipeline; ejecutar integración en Jenkins |
| Cuota o límites de AWS | Medio | Mantener instancias m7i-flex.large y destruir recursos al terminar |
| Credenciales desincronizadas | Alto | Sincronizar `tools.env` y `.env` con `update-credentials.sh` |
| E2E dependiente de datos | Medio | Sembrar visitantes por corrida con `seed-visitor.sh` |

## Criterio de cierre

El sprint se considera cerrado cuando el pipeline de `main` construye, prueba, analiza,
empaqueta y publica las imágenes; dev queda sano; stage recibe la promoción tras E2E; prod
requiere aprobación manual; y la documentación de la Parte 1 está enlazada.
