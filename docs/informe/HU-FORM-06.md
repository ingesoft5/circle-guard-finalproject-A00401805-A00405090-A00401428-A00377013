# HU-FORM-06 — Observabilidad y trazabilidad de form-service

- Fecha: 21 de noviembre de 2026.
- Rama: `feat/HU-FORM-06-observabilidad-form`.
- Estado: implementación local validada; cierre operativo pendiente de infraestructura, integración distribuida y despliegue autorizado.

## Diagnóstico y subtareas

| Subtarea | Estado | Evidencia y pendiente |
|---|---|---|
| Actuator, Micrometer y Prometheus | Parcial | Registro y endpoint implementados y probados sin PostgreSQL/Kafka. Falta desplegar esta versión y comprobar el scrape real. |
| Grafana y logs centralizados | Parcial | Panel y reglas conectados a los manifiestos compartidos de #93; logs operativos correlacionables implementados. Falta despliegue, panel con datos y colector central. |
| OpenTelemetry y Kafka | Parcial | W3C y exportador OTLP con filtro de privacidad implementados. Pasan las pruebas con KafkaTemplate/MockProducer y con broker Kafka real temporal, incluida la recepción de `traceparent`. Falta evidencia desplegada y la traza completa hasta Notification. |
| Form → Promotion → Notification | Pendiente | Existe el flujo funcional, pero falta instrumentación coordinada y una traza completa en Jaeger. La integración actual termina en Promotion. |
| Alerta en Alertmanager | Parcial | Regla y pruebas promtool pasan. Faltan carga, disparo controlado, entrega al receptor y recuperación real. |
| Privacidad | Parcial | Casos automatizados de logs y spans propios pasan. Faltan revisión desplegada y saneamiento de los servicios relacionados. |

## Infraestructura auditada: solo lectura

Servidor `205m07`, dos nodos k3d sobre el mismo host. Form estaba Running en dev/prod y stage tenía cero réplicas. No se encontraron componentes de Prometheus, Grafana, Alertmanager, Jaeger o centralización de logs en los Pods/Deployments/Services del clúster ni en los contenedores Docker consultados. Esto no descarta servicios externos no identificados.

La lectura mostró aproximadamente 31 GiB de RAM total y 14,8 GiB disponibles; Form consumía unos 284–285 MiB por pod. Jenkins estaba activo. Es una lectura puntual, no un presupuesto de capacidad. Las estadísticas de ambos nodos Docker no representan dos servidores físicos y no deben sumarse como memoria disponible.

El HTTP 200 de `/actuator/prometheus` por Ingress era HTML de Nginx, no métricas. La consulta a la IP del pod desde el host no obtuvo respuesta. El endpoint nuevo se verificó localmente mediante MockMvc.

En el diagnóstico inicial, #93 seguía abierta y sus manifiestos en `850a854eb30934f9d5377fcf89c00114a5d054fd` se utilizaron en una copia temporal. Después de su merge, se incorporó `origin/main` en `f651093` a esta rama sin conflictos. La configuración compartida ya está presente; HU-FORM-05 sigue siendo una PR independiente y no se copian sus cambios.

La consulta SSH de continuación confirmó que monitoring aún no estaba desplegado y Form seguía en `1.4.0` en dev/prod, con stage en cero réplicas. El merge no equivale a un despliegue ni habilita automáticamente Jaeger o logs centralizados.

## Funcionamiento y configuración

Actuator expone únicamente `health`, `info` y `prometheus`. Las etiquetas comunes son `service=form` y `environment`. El componente de aplicación obtiene el ambiente del namespace; la configuración compartida de scrape de #93 añade `app`, `namespace` y `pod`.

`HealthSurveyService` utiliza `FormEventPublisher` conservando cuerpos y claves de ambos eventos. El contador `circleguard_form_event_publications_total{event_type,result}` inicializa las cuatro combinaciones de `survey.submitted|certificate.validated` y `success|failure`. Se incrementa al finalizar el envío, no al invocar `send`. Los errores síncronos se registran y relanzan; los fallos asíncronos no cambian retroactivamente la respuesta HTTP. No se añaden reintentos, outbox ni garantías transaccionales nuevas.

Los logs de publicación contienen tipo de evento, resultado y `trace_id` capturado antes del envío, incluso si el callback termina después de la petición. El formato añade servicio, ambiente e identificadores MDC disponibles. Sin trazabilidad activa, `trace_id=none` es esperado. Se sustituye el listener Kafka predeterminado que podría registrar mensajes fallidos; el publisher registra su resultado de forma segura.

Micrometer Tracing utiliza el puente OpenTelemetry, sin agente Java adicional. KafkaTemplate crea el span de publicación e inyecta W3C en los headers. Las convenciones HTTP/Kafka omiten valores de alta cardinalidad. El exportador conserva atributos operativos permitidos y elimina atributos no permitidos, eventos de excepción y descripciones de error.

| Variable | Valor predeterminado | Uso |
|---|---|---|
| `FORM_ENVIRONMENT` | `local` | Ambiente; el componente Kubernetes usa el namespace. |
| `FORM_TRACING_ENABLED` | `false` | Activar trazabilidad/exportación una vez disponible el backend. |
| `FORM_TRACING_SAMPLING_PROBABILITY` | `0.1` | Muestreo; usar `1.0` temporalmente en validación autorizada. |
| `FORM_OTLP_TRACING_ENDPOINT` | `http://localhost:4318/v1/traces` | URL OTLP HTTP completa; configurar la dirección real antes de activar trazas. |

La instrumentación propia no registra usuarios, respuestas, certificados, credenciales ni cuerpos completos. Las pruebas no certifican todos los logs de librerías o de otros servicios. No activar DEBUG/TRACE de cuerpos o mensajes Kafka para obtener evidencia.

## Integración compartida preparada

Ver los [componentes de Form](../../deploy/k8s/components/form-observability/README.md). Los overlays de dev, stage y prod ya seleccionan el componente de aplicación; `monitoring/kustomization.yml` selecciona el componente de monitoring. Falta desplegar la configuración mediante una acción autorizada.

- Aplicación: scrape interno en 8086 y ambiente por namespace; no agrega rutas públicas ni cambia réplicas/HPA/persistencia.
- Monitoring: ConfigMaps con hash combinados con los de Auth en los volúmenes proyectados existentes de Prometheus/Grafana. Se conservan archivos y montajes de Auth, sin servidores adicionales ni montajes anidados.
- Panel: scrape disponible, tráfico, errores 5xx, latencia HTTP media, heap JVM y publicaciones Kafka por resultado; filtro por namespace.
- Alerta: tres publicaciones fallidas en cinco minutos, sostenidas un minuto; dev/prod. Excluye stage y no detecta por sí sola servicio caído o scrape ausente.
- Alertmanager: reutiliza el enlace de #93. Su receptor actual carece de destino externo; entrega pendiente del equipo.
- Logs centrales y Jaeger: dependencias compartidas, sin instalación en esta HU.

## Validación local ejecutada

| Verificación | Resultado |
|---|---|
| Gradle `check` | PASS: 44 pruebas, cero fallos; reporte y gate existente. |
| JaCoCo de líneas | **119/148 = 80,41 %**; supera el 60 %. |
| Endpoint Prometheus | PASS: JVM/etiquetas, salud sin detalles y endpoints administrativos no expuestos. |
| Publicación de ambos eventos | PASS: éxito confirmado, fallo síncrono/asíncrono y rechazo de tipos arbitrarios. |
| W3C con KafkaTemplate real/MockProducer | PASS: header válido, span hijo con igual trace ID, clave/cuerpo conservados. |
| Privacidad | PASS para casos automatizados de logs propios y atributos/eventos sensibles en spans. |
| `integrationTest` | PASS tras iniciar Docker Desktop: dos pruebas, cero fallos y cero omitidas. Adjuntos utiliza PostgreSQL temporal real; Form–Promotion utiliza Kafka temporal real y verifica `traceparent`, con persistencia y HealthStatusService simulados. |
| Kustomize dev/stage/prod con componente | PASS mediante `verify.py`, sobre los overlays conectados y secretos ficticios; solo cambian anotaciones y ambiente de Form. Réplicas/HPA/persistencia permanecen iguales. |
| Monitoring fusionado de #93 | PASS mediante `verify.py`: proyecciones resuelven ConfigMaps con hash y conservan reglas/panel de Auth. Sin cambios en el resto de los recursos compartidos. |
| Panel Grafana | PASS JSON: seis paneles, IDs únicos y filtros; visualización real pendiente. |
| Promtool 2.51.2 | PASS: las tres reglas de Auth y la de Form renderizadas juntas; disparo, recuperación, ausencia de errores y exclusión de stage para Form. |

Promtool fue un binario temporal oficial comprobado contra `sha256sums.txt`; no se agregó al repositorio. No se desplegó, reinició ni modificó ningún recurso del servidor.

La primera ejecución de integración quedó bloqueada porque Docker Engine no estaba disponible. Después de iniciar Docker Desktop, se repitió `integrationTest` y finalizó con `BUILD SUCCESSFUL`. Los XML de `AttachmentControllerTest` y `FormPromotionKafkaIntegrationTest` registran una prueba correcta cada uno. Esta evidencia sustituye el bloqueo local anterior; no prueba una traza distribuida de los tres servicios desplegados.

### Comandos reproducibles desde la raíz

```powershell
.\gradlew.bat :services:circleguard-form-service:check --console=plain
# Requiere Docker Engine accesible:
.\gradlew.bat :services:circleguard-form-service:integrationTest --console=plain
Start-Process 'services/circleguard-form-service/build/reports/tests/test/index.html'
Start-Process 'services/circleguard-form-service/build/reports/tests/integrationTest/index.html'
Start-Process 'services/circleguard-form-service/build/reports/jacoco/test/html/index.html'
promtool check rules deploy/k8s/components/form-observability/monitoring/form-alerts.yml
promtool test rules deploy/k8s/components/form-observability/monitoring/form-alerts.test.yml
git diff --check
python deploy/k8s/components/form-observability/verify.py
```

Los componentes ya están conectados. `verify.py` renderiza copias aisladas con secretos ficticios y verifica que los cambios se limitan a la integración de Form. No publicar renders con Secrets como evidencia.

Tras incorporar main también se ejecutaron `:services:circleguard-form-service:check`, `:services:circleguard-form-service:integrationTest` y `:services:circleguard-dashboard-service:check`: `BUILD SUCCESSFUL`, con reutilización de tareas válidas por Gradle. La conexión Kustomize posterior se validó con `verify.py` y promtool.

## Pendientes operativos y coordinación

1. Revisar e integrar esta rama y desplegar monitoring y Form mediante acciones autorizadas. #93 ya está integrada; coordinar HU-FORM-05 separadamente. No aplicar el overlay completo de stage como parte de esta validación: su estado remoto intencional es cero réplicas, mientras el overlay mantiene la política existente para promociones.
2. Acordar backend Jaeger/OTLP, colector de logs, memoria, retención, acceso y receptor de Alertmanager con plataforma. La lectura de RAM no autoriza instalar una pila nueva.
3. Verificar endpoint desde acceso interno, Prometheus Targets/series y los seis paneles con el namespace correcto. Un 200 HTML no es evidencia válida.
4. Coordinar observación de listeners con Promotion y Notification y del productor `promotion.status.changed` en Promotion. Sanear sus logs de eventos completos/identificadores; conservar contexto al cambiar de hilo en ejecutores.
5. Preparar datos sintéticos acordados: cuestionario activo, respuesta con síntomas y usuario de prueba presente en el grafo. Confirmar que Promotion publica `promotion.status.changed` y Notification lo procesa. Aislar destinos de notificación. No crear datos ni activar este flujo sin autorización.
6. Con muestreo 1.0 temporal, capturar contexto de ambos saltos Kafka y traza Jaeger con relaciones padre/hijo verificables en los tres servicios. Form–Promotion no sustituye esta evidencia.
7. Probar la alerta con fallo controlado autorizado, sin detener Kafka compartido. Comprobar firing en Prometheus, recepción en Alertmanager, entrega y recuperación. Promtool solo demuestra la lógica local.
8. Buscar datos sintéticos sensibles en métricas, trazas y logs centrales sin copiar datos reales a Git/Jira; restaurar el muestreo acordado.

Los cambios en otros servicios, infraestructura, activación remota, pruebas que escriban datos y push/PR requieren autorización adicional. No marcar la HU completa como Done hasta obtener las evidencias pendientes.
