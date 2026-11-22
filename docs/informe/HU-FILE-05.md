# HU-FILE-05 — Orquestación, observabilidad y seguridad del file-service

Issue: #149 (`circle-guard-dasf-project`).
Rama: `feat/HU-FILE-05-orquestar-file-service-en-kubernetes-observabilidad-seguridad` (desde `main`).

## Alcance

Equivalente a HU-GATEWAY-06, aplicado a `circleguard-file-service`: sondas nativas de
Kubernetes, métricas Prometheus, trazas OTLP y un contador propio de subidas, sin
versionar secretos ni configuración sensible.

## Cambios

| Archivo | Cambio |
|---|---|
| `deploy/k8s/base/apps.yml` | Anotaciones de scrape en el Service `file`; `readinessProbe` → `/actuator/health/readiness` y `livenessProbe` → `/actuator/health/liveness` en el Deployment. Sin probes duplicadas. |
| `services/circleguard-file-service/build.gradle.kts` | `micrometer-registry-prometheus`, `micrometer-tracing-bridge-otel` y `opentelemetry-exporter-otlp`. |
| `services/circleguard-file-service/src/main/resources/application.yml` | `health,info,prometheus` expuestos, probes habilitadas, `show-details: never`, tag `application` y endpoint OTLP parametrizado. S3/MinIO sigue por variables de entorno. |
| `FileUploadController.java` | Contador `circleguard_file_uploads_total` con tag `result` (`success`/`failure`). |
| `FileUploadControllerTest.java` | Asserts del contador y nuevo caso de fallo con `SimpleMeterRegistry`. |

## Verificación

- `./gradlew :services:circleguard-file-service:test :services:circleguard-file-service:jacocoTestCoverageVerification --console=plain --no-daemon` → **BUILD SUCCESSFUL** (12 tests, JaCoCo ≥ 60 %).
- `git diff --check` limpio.
- Sin secretos, `*.tfstate`, `backend.hcl`, `tfplan` ni `.terraform/` versionados.

## Pendiente

Validación on-prem con `kubectl diff` / manifiestos en `cg-dev`, `cg-stage` y `cg-prod`:
requiere autorización previa para ejecutar acciones en el host.
