<!-- CG-DOC: Módulo `observability` (E2.1). -->
# Módulo `observability` (E2.1)

Declara el namespace de observabilidad del ambiente y los releases Helm de
`kube-prometheus-stack` (Prometheus, Grafana y Alertmanager) y `loki-stack`
(logs centralizados), con versiones fijadas.

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `env` | Identificador del ambiente | — |
| `namespace` | Namespace del stack | — |
| `grafana_admin_password` | Contraseña de Grafana (sensible) | — |
| `prometheus_version` | Versión del chart | `61.7.0` |
| `loki_version` | Versión del chart | `2.17.4` |

Salidas: `namespace`, `prometheus_release`, `loki_release`.
