<!-- CG-DOC: E2.6 load test suite: objectives, scenarios and reproduction. -->
# Locust — pruebas de carga de CircleGuard

Escenarios sobre `cg-stage` que reproducen los dos flujos exigidos en E2.6:

| Escenario | Peticiones medidas | Peso |
|---|---|---|
| Validación de acceso | `POST /api/v1/auth/login` → `GET /api/v1/auth/qr/generate` → `POST /api/v1/gate/validate` | 5 |
| Envío de cuestionario | `GET /api/v1/questionnaires/active` → `POST /api/v1/surveys` | 3 |
| Control negativo | `POST /api/v1/gate/validate` con token inválido (espera 401/403) | 1 |

## Objetivos declarados (antes de ejecutar)

| Métrica | Objetivo |
|---|---|
| p95 de `POST /api/v1/gate/validate` | ≤ 500 ms |
| p95 de `POST /api/v1/auth/login` | ≤ 800 ms |
| Tasa de errores global | < 1 % (excluyendo el control negativo, que espera 4xx) |
| Throughput sostenido | ≥ 50 req/s con 50 usuarios concurrentes |
| Estabilidad | 5 minutos sin `ConnectionError` ni 5xx |

## Ejecución contra `cg-stage`

El ingress on-prem selecciona el ambiente por cabecera `Host` (ver `docs/infra/runbook.md`).

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r tests/performance-locust/requirements.txt

locust -f tests/performance-locust/locustfile.py \
  --host http://10.147.20.67:30080 \
  --users 50 --spawn-rate 5 --run-time 5m --headless \
  --html reports/locust-stage.html --csv reports/locust-stage
```

Variables opcionales: `HOST_HEADER` (por defecto `stage.circleguard.local`),
`LOADTEST_USER` / `LOADTEST_PASSWORD` (por defecto el usuario semilla
`staff_guard`). Si el puerto 30080 solo está expuesto dentro del host on-prem,
establece un túnel `ssh -L 30080:127.0.0.1:30080 <host-onprem>` y usa
`--host http://127.0.0.1:30080`.

Los resultados y recomendaciones se registran en este documento.
