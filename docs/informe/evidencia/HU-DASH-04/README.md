# HU-DASH-04 — Evidencia operativa real en on-prem

Transcriptos obtenidos el 23 de noviembre de 2026 desde el host on-prem
`10.147.20.67` mediante SSH, `kubectl` y `curl`. No contienen resultados
simulados; se conservan tal cual fueron impresos por los comandos.

## `evidencia-1-k8s-sano.txt`

- Fecha: 2026-11-23T07:39:53Z a 07:40:19Z.
- Nodos `k3d-cg-server-0` y `k3d-cg-agent-0` en estado `Ready` (k3s v1.31.1+k3s1).
- `cg-dev`, `cg-stage` y `cg-prod`: deployment `dashboard` 1/1 con imagen
  `circleguard-docker/dashboard:1.10.0`, pods `1/1 Running` y `postgres-0` Ready.
- Sondas `/actuator/health`, `/actuator/health/readiness` y
  `/actuator/health/liveness`: HTTP 200 y `status UP` en los tres ambientes.
- `/api/v1/analytics/summary`: HTTP 200 con `{"error":"Service unavailable"}`.
  Se reporta tal cual; no se interpreta como analytics funcional.

## `evidencia-2-rollback-prod.txt`

- Fecha: 2026-11-23T07:45:36Z a 07:48:50Z.
- Rollback en `cg-prod` con `kubectl rollout undo` desde la revisión con imagen
  `1.10.0` hasta la revisión con imagen `circleguard-docker/dashboard:1.4.0`.
- `rollout status`: `deployment "dashboard" successfully rolled out`.
- Deployment 1/1 con imagen `1.4.0`; pod `dashboard-85c4c6b57f-fpw8s` 1/1 Running;
  `postgres-0` Ready.
- Sondas sobre el pod `1.4.0`: HTTP 200 y `status UP`.
- Restauración final al baseline `1.10.0` con `kubectl set image` y `rollout status`
  exitoso. El transcripto registra también un intento previo de restauración por
  revisión que falló porque el historial de revisiones se recorta; se corrigió con
  el cambio de imagen. Estado final verificado: dashboard 1/1 con `1.10.0`.
