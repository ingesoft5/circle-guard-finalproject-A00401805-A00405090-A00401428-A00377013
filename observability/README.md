<!-- CG-DOC: Index of CircleGuard observability assets and their effective repository locations. -->
# Observabilidad de CircleGuard

El enunciado (`Proyecto_Final_CircleGuard.tex`, líneas 287-299) propone como **estructura sugerida** los directorios `observability/{prometheus,grafana,alerts}/`. El repositorio ya tiene el stack real y versionado en `deploy/k8s/monitoring/`; por eso este directorio es solo un índice y no duplica configuración ni crea otro colector.

## Dónde está cada pieza

| Necesidad | Ubicación real |
| --- | --- |
| Prometheus, Alertmanager y reglas compartidas | `deploy/k8s/monitoring/prometheus.yml`, `alertmanager.yml` y ConfigMaps de reglas |
| Dashboards globales (Auth/Plataforma y Promotion) | `deploy/k8s/monitoring/grafana.yml` |
| Logs centralizados | `deploy/k8s/monitoring/loki.yml` y `promtail.yml` |
| Componentes por servicio | `deploy/k8s/components/form-observability/` y `deploy/k8s/components/dashboard-observability/` |
| Reglas y panel de dashboard-service | `deploy/k8s/components/dashboard-observability/monitoring/` |
| Informe de la HU | `docs/informe/HU-DASH-05.md` |
| Evidencia local | `docs/informe/evidencia/HU-DASH-05/` |

## Cómo se integra un servicio nuevo

1. El servicio expone `/actuator/prometheus` con `micrometer-registry-prometheus` y etiquetas `service` / `environment`.
2. El componente de aplicación añade anotaciones `prometheus.io/scrape`, `path` y `port` al pod, además de las sondas `liveness`/`readiness` de Actuator.
3. El componente de monitoring genera ConfigMaps con hash para reglas y dashboard, y los proyecta en los volúmenes existentes de Prometheus/Grafana sin borrar los assets de otros servicios.
4. Se incluye el componente desde `deploy/k8s/overlays/{dev,stage,prod}/kustomization.yml` y desde `deploy/k8s/monitoring/kustomization.yml`.
5. Se valida con `promtool check rules`, `promtool test rules` y el `verify.py` del componente. El scrape real, los datos del panel y la entrega de alertas requieren despliegue autorizado.

La demostración final (enunciado, línea 409) debe provocar una falla controlada y mostrar a la vez la métrica, el panel y la alerta. Las pruebas locales descritas arriba no sustituyen esa evidencia.
