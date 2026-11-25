<!-- CG-DOC: Componentes de observabilidad de Form. -->
# Componentes de observabilidad de Form

Estos componentes Kustomize amplían la pila compartida integrada en el PR #93. Los seleccionan los overlays de aplicación y la configuración de monitorización de esta rama. No crean servidores de monitorización adicionales, un recolector de logs ni un backend de trazas.

## Integración y despliegue

Los componentes amplían la configuración de monitorización del PR #93. La verificación sin conexión contra main, incluida HU-FORM-05, confirma que el componente conserva las réplicas de Form, el HPA y el almacenamiento.

El `kustomization.yml` de cada overlay de aplicación (dev, stage y prod) incluye:

```yaml
components:
  - ../../components/form-observability/app
```

`deploy/k8s/monitoring/kustomization.yml` incluye:

```yaml
components:
  - ../components/form-observability/monitoring
```

El componente de aplicación añade anotaciones de scraping en los pods y deriva `FORM_ENVIRONMENT` del namespace del pod. No cambia réplicas, HPA, volúmenes ni rutas de ingress público. El scraping usa el puerto interno 8086 del pod y el descubrimiento de servicios compartido de Kubernetes; no se necesita una ruta pública de Actuator. No restringe el acceso de otras cargas de trabajo dentro del clúster.

El componente de monitorización proyecta ConfigMaps de Form con hash junto a las reglas y dashboards existentes. Prometheus carga Auth, Identity y Form desde `/etc/prometheus/rules/*.yml` y Promotion desde `/etc/prometheus/rules/promotion/*.yml`. Grafana carga los dashboards desde `/var/lib/grafana/dashboards`. El dashboard de Form usa las etiquetas de destino compartidas `app` y `namespace` e incluye un selector de namespace.

Los cambios en el nombre de los ConfigMaps generados actualizan la referencia del volumen proyectado y la plantilla de pods, de modo que las actualizaciones de reglas siguen el despliegue progresivo habitual de monitorización autorizado. Este componente no implementa la recarga en caliente de Prometheus. Valide la salud de Prometheus/Grafana tras el despliegue.

## Alerta

`FormKafkaPublicationFailures` agrega las confirmaciones de publicación con error de ambos topics de Form por namespace. Se activa después de al menos tres errores en cinco minutos que permanezcan por encima del umbral durante un minuto. Cubre `cg-dev` y `cg-prod`; stage está excluido por estar detenido intencionalmente. Detecta errores de publicación mientras Form está en ejecución, no la indisponibilidad total del servicio ni objetivos de scraping ausentes.

Las reglas usan la conexión existente de Prometheus con Alertmanager. PR #93 define actualmente un receptor sin integración de notificación externa. Un destino de entrega configurado y una notificación recibida son requisitos previos para afirmar que la entrega de alertas está operativa.

## Verificación local

Con `promtool` de Prometheus 2.51.2 disponible, ejecute desde `monitoring/`:

```powershell
promtool check rules form-alerts.yml
promtool test rules form-alerts.test.yml
```

La prueba de reglas cubre la activación en dev, la recuperación, la ausencia de errores en prod y la exclusión de stage. Ejecute el verificador sin conexión desde la raíz del repositorio (se requieren Python con PyYAML y kubectl):

```powershell
python deploy/k8s/components/form-observability/verify.py
```

El verificador crea copias temporales con Secrets ficticios, renderiza los tres entornos y la monitorización, comprueba las referencias de ConfigMaps generadas y los archivos de Auth conservados, y compara con la misma configuración sin los componentes. Nunca contacta con el clúster ni imprime Secrets renderizados.

Para renderizados individuales con su configuración local (no aplicar como parte de la verificación):

```powershell
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/stage
kubectl kustomize deploy/k8s/overlays/prod
kubectl kustomize deploy/k8s/monitoring
```

Use Secrets de marcador de posición en una copia aislada para el renderizado sin conexión. No guarde los Secrets renderizados como evidencia. En el despliegue autorizado se verifican los objetivos/series de Prometheus, los datos de los paneles de Grafana, la carga de reglas y la entrega de Alertmanager; el renderizado local y las pruebas de reglas validan la configuración.

La recolección centralizada de logs y la recepción de Jaeger/OTLP siguen siendo dependencias de la infraestructura compartida. Aquí no se aprovisiona ninguna datasource de logs ni backend de trazas.
