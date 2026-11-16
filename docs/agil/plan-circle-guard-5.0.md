# Plan CircleGuard 5.0 — Cierre P1 + P2 on-prem

**Nombre corto:** Doble Cinco
**Periodo:** 16–22 de noviembre de 2026
**Meta:** cerrar la Parte 1 con evidencia verificable y entregar la Parte 2 sobre
infraestructura propia.
**Fórmula de la nota:** `(P1 × 15 + P2 × 25) / 40`. Un 5.0 exige 5.0 en ambas partes.

## 1. Reglas del plan

1. **P2 manda.** P1 solo cambia la nota si el docente autorizó reentrega; si ya fue entregada,
   el trabajo de cierre queda como anexo y material de sustentación.
2. **Sin sudo.** Todo corre con el usuario `docker` en espacio de usuario.
3. **Sin exponer servicios.** SSH, API de k3s, registro, Kafka y bases de datos quedan
   privados.
4. **Sin destruir.** No se tocan los contenedores ajenos `k8s-worker` e `icesi-inn`, no se usa
   `docker prune` ni `down -v`.
5. **Evidencia real.** No se enlaza una captura, URL o registro que no exista.

## 2. Parte 1 — cierre

| # | Acción | Evidencia | Estado |
|---|---|---|---|
| 1 | Backlog con cada historia en Done y su PR enlazado | `docs/agil/backlog.md` | Hecho |
| 2 | Sprint 1 Review sin pendientes de repositorio | `docs/agil/sprint-1/review.md` | Hecho |
| 3 | Cobertura de `promotion-service` ≥ 70 % | 76.13 % de líneas en JaCoCo | Hecho |
| 4 | Módulo de `form-service` en el registro IAG | `IAG_REGISTRO.md` sección 3 | Hecho |
| 5 | Nombre correcto de la llave en el runbook | `docs/infra/runbook.md` | Hecho |
| 6 | Anexo de migración on-prem | `docs/infra/migracion-onprem.md` | Hecho |
| 7 | Tablero único de P1 y P2 con PR enlazadas | GitHub Project `circle-guard-dasf` | Hecho |
| 8 | Build final en `main` con pipeline verde | Jenkins + Sonar + JaCoCo | Pendiente |

## 3. Parte 2 — cronograma

| Día | Fecha | Entregable |
|---|---|---|
| 0 | 16 nov | Ocho servicios desplegados en `cg-dev` con healthchecks y E2E básico |
| 1 | 17 nov | `cg-stage` y `cg-prod`; Terraform base y backend remoto con bloqueo |
| 2 | 18 nov | Terraform para los tres ambientes; Resilience4j y configuración externalizada |
| 3 | 19 nov | Tercer patrón de resiliencia; Prometheus, Grafana y tres alertas |
| 4 | 20 nov | Jaeger, Loki, Trivy, ZAP y Locust |
| 5 | 21 nov | Pipeline con Terraform y Kubernetes, rollback probado, documentación del Sprint 2, costos y resumen en inglés |
| 6 | 22 nov | Ensayo general, evidencias y sustentación |

## 4. Reparto

| Responsable | Frente |
|---|---|
| Integrante 1 | Terraform, backend remoto y ambientes |
| Integrante 2 | Manifiestos Kubernetes y despliegue de los servicios |
| Integrante 3 | Observabilidad, seguridad y pruebas de carga |
| Integrante 4 | Documentación, Sprint 2, costos y presentación |

**Regla de concurrencia:** un solo `kubectl apply` a la vez por namespace. Las bonificaciones
no se tocan hasta completar los ítems obligatorios.

## 5. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El pipeline aún usa SSH hacia AWS | P2-08 no avanza | Migrar `Jenkinsfile` y `ci.groovy` a `kubectl` local en el día 1 |
| RAM insuficiente con tres ambientes | Pods en `Pending` | Limitar réplicas en stage/prod y ajustar requests/limits |
| Webhook de GitHub sin túnel | Builds no se disparan | Polling o smee.io como respaldo |
| Pérdida de la cuenta AWS | P1.2 sin demo en vivo | Anexo on-prem y sustentación con el entorno propio |
| Rotación de integrantes | Retrabajo | Un dueño por frente y evidencia en el repositorio, no en chats |
