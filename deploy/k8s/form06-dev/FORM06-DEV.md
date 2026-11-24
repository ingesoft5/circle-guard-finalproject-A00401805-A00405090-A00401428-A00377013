<!-- CG-DOC: HU-FORM-06: recursos de validación aislados en cg-dev. -->
# HU-FORM-06: recursos de validación aislados en cg-dev

Este overlay opcional reutiliza la pila de monitorización de los PR #93 y #138 ya integrados. Se mantiene
sin desplegar. Está deliberadamente separado de los overlays habituales de dev/stage/prod
y del Jenkinsfile compartido.

Use la canalización oficial de Jenkins para las comprobaciones de PR y la publicación de imágenes. La
comprobación de PR no publica imágenes. Para la validación canaria use la imagen de Form
y el digest registrados por la compilación de versión oficial tras superar sus compuertas de
calidad y seguridad de imagen. Los recursos canarios se aplican por separado y con aprobación.

## Aislamiento e impacto

- `canary/` crea solo `Service/cg-dev/form-hu6` y
  `Deployment/cg-dev/form-hu6`. No tiene Ingress. El Service `form` existente
  selecciona `app=form`, mientras que el canario selecciona `app=form-hu6`.
- El canario usa solo los Services de PostgreSQL y Kafka de dev. Flyway está desactivado
  y Hibernate usa `validate` durante la prueba. Un envío de cuestionario aprobado
  manualmente seguiría escribiendo en la base de datos **dev** y emitiendo
  eventos al broker **dev**; no envíe datos de producción ni identidades reales.
- `monitoring/` renderiza las reglas existentes de Prometheus, Grafana, Alertmanager, Auth,
  Identity y Promotion, los dashboards compartidos, los recursos de Form, Loki, Alloy y
  Jaeger. No crea otra instancia de Loki. Elimina el DaemonSet de Promtail heredado
  y su RBAC global porque ese recolector observa los
  tres entornos. El RBAC de Prometheus se sustituye por un Role en `cg-dev`.
  Alloy solo tiene un Role en `cg-dev` y
  selecciona `app=form-hu6`. La canalización de logs descarta todas las líneas salvo los
  resultados acotados de `FormEventPublisher` y reescribe las líneas aceptadas a partir de
  campos incluidos en la lista de permitidos. Sin montajes de logs del host, DaemonSet, Ingress público ni recursos de prod o stage.
- El overlay limita el descubrimiento de Prometheus al canario en `cg-dev` y mantiene
  sin cambios las datasources originales de Prometheus y Loki, las reglas compartidas y
  los dashboards. Sus paneles están aprovisionados pero permanecen vacíos en esta
  prueba aislada. Ambas configuraciones cargan las reglas de Auth, Identity y Form
  desde el directorio raíz y las reglas de Promotion desde su subdirectorio existente.
- El Loki compartido de #138 usa un PVC de 2 GiB. Jaeger está en memoria. La retención
  de Prometheus es de dos horas y su `emptyDir` está limitado a 512 MiB. El presupuesto
  total de recursos debe revisarse de nuevo antes del despliegue. El PVC compartido de Loki
  nunca debe eliminarse como parte de una reversión de Form.
- El receptor predeterminado de Alertmanager de #93 no tiene destino externo.
  La entrega **no** se considera completa hasta que se configure un receptor aprobado
  mediante un Secret y se observe una notificación real.

## Verificación sin conexión

Desde la raíz del repositorio:

```powershell
python deploy/k8s/form06-dev/verify.py
kubectl kustomize deploy/k8s/form06-dev/monitoring
kubectl kustomize deploy/k8s/form06-dev/canary
# Optional synthetic log privacy test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_logs.py
# Optional synthetic OTLP ingestion test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_traces.py
# Optional synthetic Alertmanager route test; requires local Docker Engine:
python deploy/k8s/form06-dev/smoke_alertmanager.py
```

La salida de Kustomize contiene configuración pero ningún valor de Secret. No aplique
el manifiesto canario con su tag de imagen de marcador de posición. Use
`prepare_release.py --tag X.Y.Z --commit FULL_SHA --digest sha256:FULL_DIGEST
--jenkins-build-url URL` solo después de que Jenkins haya compilado y aprobado ese
commit exacto. El script fija la imagen canaria al digest registrado, exige un checkout limpio y escribe un inventario, hashes
y dos listas de recursos JSON en `build/form06-release/`. Su entrada debe cotejarse
con el artefacto de digest de imagen de Jenkins. Nunca contacta con el
clúster.

## Compuerta de despliegue y recuperación

Antes de cualquier escritura, obtenga una aprobación separada para las dos listas de
recursos renderizados exactas. Compruebe de nuevo que el namespace `monitoring` y los nuevos nombres
de recursos siguen ausentes, que las réplicas de dev/prod/stage se mantienen según lo previsto, que la ejecución
de Jenkins superó las comprobaciones unitarias, de cobertura, Sonar, integración y seguridad de imagen, y que
el responsable de monitorización ha reservado una ventana de prueba. Detenga el proceso si alguna comprobación previa difiere.
No ejecute aquí el `ci.deploy` habitual: actualiza todos los servicios y promueve
stage/prod en main.

Aplique primero la monitorización, espere a sus seis Deployments y luego aplique el canario.
Para la recuperación, elimine solo el Service/Deployment `form-hu6` tras comprobar su
propiedad y su imagen. Revise si el namespace compartido de monitorización ha adquirido
otros usuarios antes de revertir sus deployments o su configuración. Nunca elimine
el namespace, un recurso compartido ni un volumen persistente como
reversión automática. El Deployment `form` existente permanece intacto en todo momento.

Evidencia en ejecución requerida antes de marcar la historia como Done: series reales de Prometheus
`up` y de Form; paneles de Form en Grafana con datos; una línea de log de Form incluida en la lista
de permitidos en Loki sin marcador sintético sensible; un trace de Jaeger que abarque Form,
Promotion y Notification con el mismo ID de trace; una entrega y recuperación reales de Alertmanager;
y la ausencia del marcador sintético en métricas, logs
y spans. La instrumentación de Promotion/Notification y un receptor de notificación aprobado
son cambios separados y sujetos a compuerta.
