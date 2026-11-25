<!-- CG-DOC: Notas de uso de los componentes Kustomize de observabilidad de dashboard-service. -->
# Componentes de observabilidad de Dashboard

Estos componentes Kustomize amplían la pila compartida de Prometheus, Alertmanager, Grafana y Loki ya presente en `deploy/k8s/monitoring`. No crean otro servidor de monitorización, recolector de logs ni backend de trazas. Siguen el mismo patrón que `components/form-observability`.

## Integración

Cada overlay de aplicación (`dev`, `stage`, `prod`) incluye:

```yaml
components:
  - ../../components/form-observability/app
  - ../../components/dashboard-observability/app
```

`deploy/k8s/monitoring/kustomization.yml` incluye:

```yaml
components:
  - ../components/form-observability/monitoring
  - ../components/dashboard-observability/monitoring
```

El componente de aplicación añade anotaciones de scraping en los pods para el puerto interno `8084`, deriva `DASHBOARD_ENVIRONMENT` del namespace y cambia las sondas de preparidad/vividad a los grupos de Actuator. No cambia réplicas, HPA, volúmenes ni rutas de ingress.

El componente de monitorización proyecta dos ConfigMaps con hash: reglas de alerta para Prometheus y un dashboard de Grafana. Su parche enumera intencionalmente las fuentes de Auth, Form y dashboard para que añadir este componente después de `form-observability` no elimine los recursos de Form.

## Paneles del dashboard

`monitoring/dashboard-overview.json` ofrece siete paneles con un selector de namespace:

1. Disponibilidad de scraping (`up`).
2. Solicitudes HTTP por segundo.
3. HTTP 5xx por segundo.
4. Latencia HTTP media.
5. Llamadas a Promotion por segundo según el resultado.
6. Latencia p95 de las llamadas a Promotion.
7. Logs centralizados de la datasource de Loki.

## Alertas

- `DashboardServiceDown` (crítica): el objetivo de scraping del dashboard está caído durante dos minutos en dev o prod.
- `DashboardPromotionCallFailures` (advertencia): al menos 0.2 llamadas a Promotion con error por segundo durante cinco minutos, sostenido durante dos minutos.

Stage se excluye intencionalmente de ambas expresiones porque, como en Form, está detenido entre validaciones. Las alertas usan la ruta existente de Prometheus a Alertmanager con el receptor configurado en el despliegue.

## Verificación

Desde `deploy/k8s/components/dashboard-observability/monitoring`:

```bash
promtool check rules dashboard-alerts.yml
promtool test rules dashboard-alerts.test.yml
```

Desde la raíz del repositorio (se requieren Python con PyYAML y kubectl):

```bash
python deploy/k8s/components/dashboard-observability/verify.py
```

El verificador renderiza todos los overlays y la pila de monitorización con Secrets ficticios y compara con la misma configuración sin este componente. Nunca contacta con el clúster ni imprime Secrets. Para renderizados individuales, use `kubectl kustomize deploy/k8s/overlays/<env>` y `kubectl kustomize deploy/k8s/monitoring`.

El renderizado local y las pruebas de reglas validan manifiestos y expresiones. El scraping en ejecución, los datos de los paneles y la entrega de notificaciones se comprueban con un despliegue autorizado y acceso al clúster/Jenkins.
