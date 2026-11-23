<!-- CG-DOC: Observability and security evidence for dashboard-service (HU-DASH-05). -->
# HU-DASH-05 — Observabilidad y seguridad de `dashboard-service`

**Fecha:** 23 de noviembre de 2026
**Rama:** `feat/HU-DASH-05-integrar-observabilidad-y-seguridad`
**Base:** trabajo iniciado sobre `main` `526219b` y actualizado por fast-forward hasta `main`/`origin/main` `9c19916`
**Dependencias:** HU-DASH-04 (despliegue Kubernetes de dashboard, ya presente en `deploy/k8s/base/apps.yml`) y herramientas compartidas `deploy/k8s/monitoring/` + `Jenkinsfile`.
**Entregables cubiertos:** E2.2 (sondas Actuator), E2.3 (Trivy con umbral bloqueante), E2.5 (Micrometer/Prometheus, Grafana, alertas, logs centralizados) y E2.6 (seguridad del artefacto).
**Estado:** implementación local terminada y verificada; despliegue, datos reales, entrega de alertas y corrida remota de Jenkins **pendientes de acceso autorizado**. No se presentan como ejecutadas.

## 1. Contraste con el enunciado y con `main`

El enunciado (`Proyecto_Final_CircleGuard.tex`, líneas 275-276) exige Micrometer + Prometheus, paneles por servicio, alertas críticas, logs centralizados y escaneo Trivy de imágenes e IaC con umbral bloqueante. La línea 186 describe la brecha DevOps **inicial** del repositorio base (“no tenía Actuator ni Micrometer”); ese enunciado ya no describe el estado actual: siete servicios incorporaron el registro Prometheus de Micrometer en entregas anteriores.

Al iniciar esta HU, `dashboard-service` era la excepción:

| Necesidad de la HU | Estado en `main` antes de la HU | Brecha real |
| --- | --- | --- |
| Spring Boot Actuator | `build.gradle.kts:18-19` ya lo declara; `Dockerfile:50-51` ya usa `/actuator/health` como `HEALTHCHECK` | No reañadir Actuator; solo configurar exposición y grupos de sondas |
| Micrometer/Prometheus | `application.yml` termina en `server.port: 8084`; sin `management:` ni `micrometer-registry-prometheus` | Falta el registro, el endpoint y las etiquetas de servicio/ambiente |
| Scrape en Prometheus | Job `circleguard-services` ya filtra pods con `prometheus.io/scrape=true` (`deploy/k8s/monitoring/prometheus.yml:57-84`) | El Deployment `dashboard` (`apps.yml:453-520`) no tiene anotaciones de scrape |
| Panel Grafana | Existen datasources Prometheus/Loki y paneles de Auth/Plataforma y Promotion | No hay panel de `dashboard-service` |
| Logs centralizados | Loki 2.9.6 + Promtail recogen los namespaces `cg-*` (`monitoring/loki.yml`, `promtail.yml`) | El servicio no tiene patrón de log con servicio/ambiente/correlación ni panel propio |
| Fallos hacia `promotion-service` | `PromotionClient` usa `RestTemplate` y `log.error`, sin métricas; el controlador devuelve HTTP 200 con `error` | Instrumentar duración y resultado sin propagar departamentos ni PII |
| Alerta compartida | `prometheus.yml` monta reglas de auth/promotion/notification | No existe `dashboard-alerts.yml` ni su montaje |
| Trivy imagen + IaC | `Jenkinsfile:290-347` ya escanea IaC e imágenes; `dashboard` está en el catálogo (`ciServices.groovy:107-120`) con `docker/publish=true` | No hay evidencia específica de dashboard; la imagen actual sí dispara el gate por CRITICAL |
| Jira de trazas | E2.5 pide Jaeger para `form → Kafka → promotion → notification` | Fuera de alcance por decisión del equipo: esta HU cubre solo métricas y logs |

La conclusión del contraste fue acotar el trabajo: **no crear otro stack de monitoreo ni otro gate de seguridad**, reutilizar el componente Kustomize de `form-observability` como plantilla y documentar honestamente lo que no puede verificarse sin clúster/Jenkins.

## 2. Cambios realizados

### 2.1 Servicio (`services/circleguard-dashboard-service/`)

| Archivo | Cambio |
| --- | --- |
| `build.gradle.kts` | Añadida `io.micrometer:micrometer-registry-prometheus`. Actuator ya existía y no se duplicó. No se añadió tracing bridge/OTLP. |
| `src/main/resources/application.yml` | `management.endpoints.web.exposure.include: health,info,prometheus`; `health.show-details: never`; `health.probes.enabled: true`; etiquetas `service=dashboard`, `environment=${DASHBOARD_ENVIRONMENT:local}`; patrón de log con `[dashboard,environment,traceId,spanId]` (MDC vacío produce `-`). |
| `src/main/java/.../client/PromotionClient.java` | `MeterRegistry` inyectable por setter (por defecto `Metrics.globalRegistry`) y `Timer.Sample`. Contador `circleguard.dashboard.promotion.calls` y temporizador `circleguard.dashboard.promotion.call.duration`, con tags acotados `operation` (`healthStats`/`departmentStats`) y `outcome` (`success`/`error`). El log de error conserva operación, resultado y clase de excepción; `department`, URLs y cuerpos no entran en tags ni logs propios. Se mantiene el `RestTemplate` construido en el campo para no romper `PromotionClientTest`. |
| `src/test/java/.../observability/PrometheusEndpointTest.java` | Exposición Prometheus, JVM, etiquetas, salud sin componentes, sondas liveness/readiness y endpoints administrativos sensibles devueltos como 404. |
| `src/test/java/.../observability/PromotionClientMetricsTest.java` | Éxito y fallo por operación, contadores/timers por `outcome`, cardinalidad fija (2 tags) y ausencia del tag `department`. |

### 2.2 Kubernetes y panel compartido (`deploy/k8s/`)

Se creó `components/dashboard-observability/` siguiendo la forma de `components/form-observability/`:

- `app/kustomization.yml`: anotaciones `prometheus.io/scrape=true`, `prometheus.io/path=/actuator/prometheus`, `prometheus.io/port=8084`; `DASHBOARD_ENVIRONMENT` desde `metadata.namespace`; sondas convertidas a `/actuator/health/readiness` y `/actuator/health/liveness`. No cambia réplicas, HPA, volúmenes ni Ingress.
- `monitoring/dashboard-alerts.yml`: `DashboardServiceDown` (critical, `up == 0` por 2 min en dev/prod) y `DashboardPromotionCallFailures` (warning, `>= 0.2` errores/s en 5 min, sostenido 2 min). Stage se excluye igual que en Form.
- `monitoring/dashboard-alerts.test.yml`: series sintéticas de caída, recuperación y fallos promotion; dev dispara y stage no.
- `monitoring/dashboard-overview.json`: panel `circleguard-dashboard-overview` con variable `namespace` y siete paneles: disponibilidad de scrape, RPS HTTP, 5xx, latencia media HTTP, llamadas promotion por resultado, p95 de duración promotion y logs de Loki.
- `monitoring/{monitoring-patch,grafana-patch}.yml`: proyectan las tres fuentes de reglas (auth, form, dashboard) y los tres dashboards (circleguard, form, dashboard), evitando que el componente nuevo desmonte los anteriores.
- `verify.py` y `README.md`: render offline y comparación con/sin componente; no contacta el clúster ni imprime Secrets.

Integración: el componente de aplicación se añadió a `overlays/{dev,stage,prod}/kustomization.yml` y el de monitoring a `monitoring/kustomization.yml`.

**Reparación de compatibilidad (afecta a HU-FORM-06):** `components/form-observability/monitoring/monitoring-patch.yml` contenía dos documentos YAML en un mismo archivo referenciado por `patches: path:`; con kubectl/Kustomize 5.0.4 fallaba con `unable to parse SM or JSON patch`. Se separó en `monitoring-patch.yml` (Prometheus) y `grafana-patch.yml` (Grafana) y se actualizó su `kustomization.yml`. El comportamiento renderizado es el mismo; `form-observability/verify.py` se ajustó para verificar las tres fuentes y solo eliminar componentes dashboard al construir la comparación.

## 3. Seguridad y gate Trivy

No se modificaron los umbrales ni la versión de Trivy del pipeline: `Jenkinsfile` ya define `trivyScanIaCLocal()` y `trivyScanImagesLocal()` con Trivy 0.57.1, conteo `CRITICAL` con `jq` y fallo si `criticals > 0` (el `trivy image` del pipeline no lleva `--exit-code 1`; el conteo posterior es el bloqueante). Sí se actualizaron, por petición explícita, las versiones de las dependencias de la plataforma. `dashboard` tiene `docker=true`, `publish=true` y `healthcheck=true` en `jenkins/vars/ciServices.groovy`, por lo que la compuerta global cubre su imagen cuando se construye en `main`. OWASP ZAP sigue dirigido solo a auth y promotion: ampliarlo no estaba en esta HU.

**Remediación aplicada:** en lugar de bajar el umbral o añadir excepciones Trivy, se actualizó el platform global de Spring Boot de `3.2.4` a `4.0.10` (Kotlin `2.2.21`, plugin de dependency-management `1.1.7`, BOM centralizado en el build raíz). Boot 4.0.10 incorpora Spring Framework `7.0.11` y Tomcat `11.0.27`, versiones que corrigen los CVEs `CVE-2026-47884` y los de `tomcat-embed-core` detectados en el escaneo inicial. La migración mantuvo el alcance funcional: se retiró el BOM duplicado de los ocho servicios, se eliminó `spring-boot-starter-aop` (sin usos), se sustituyó `flyway-core` por `spring-boot-starter-flyway` en los cinco servicios que usan Flyway, se añadieron los starters de test que Boot 4 separó (`webmvc-test`, `actuator-test`, `security-test` solo donde hay seguridad, `data-jpa-test`), se migraron las anotaciones de test a los paquetes nuevos (`WebMvcTest`, `MockitoBean`, `AutoConfigureMetrics`, auto-configs de JDBC/Hibernate/Kafka/Neo4j/Redis), se actualizó Resilience4j a `spring-boot4:2.4.0`, se añadió `spring-boot-jackson2` para conservar el `ObjectMapper` Jackson 2 del código, `spring-boot-starter-webclient` para `WebClient.Builder` y `spring-retry:2.0.13` porque Boot 4 ya no gestiona su versión. Toda la suite Java quedó en verde (`./gradlew test`, RC=0) antes del rescan de imagen.

Escaneos locales (detalles y comandos reproducibles en `docs/informe/evidencia/HU-DASH-05/README.md`):

- **IaC:** `trivy config --severity CRITICAL --exit-code 1 deploy/k8s` → rc=0, 31 archivos, 0 CRITICAL. Escaneo complementario `HIGH,CRITICAL`: 74 HIGH, 0 CRITICAL (guías estándar, no bloquean la regla definida). El incremento de 27 a 31 archivos se debe a los tres manifiestos nuevos del componente `dashboard-observability` y a `dashboard-config.yml` de HU-DASH-04; los HIGH pasaron de 72 a 74 por los manifiestos del componente.
- **Imagen antes de la remediación:** `circleguard-docker/dashboard:hu-dash-05-local` (manifest list `sha256:7a038045f65824b1879408e2086be9d1eb3792b7b8bec6fe9f0f08249686b233`) → **9 CRITICAL y 35 HIGH**, rc=1. Los CRITICAL son 8 en `tomcat-embed-core 10.1.19` y 1 en `spring-webmvc 6.1.5` (CVE-2026-47884), todos con corrección disponible. Esa versión del artefacto habría **bloqueado** el gate; no se afirmó que pasara Trivy ni que se publicara un digest.
- **Imagen después de la remediación:** `circleguard-docker/dashboard:hu-dash-05-boot4` (image id `sha256:ab6cb91f55ecf97e6b70f20ba8560876a33e7fa34c01587c046d40b8f01e256e`), escaneada desde tarball con **Trivy 0.57.1** (la versión de Jenkins) y **Trivy 0.70.0**: **0 HIGH y 0 CRITICAL** en ambos casos (`trivy-image-0.57.1.json`, `trivy-image-0.70.0.json`). La lógica bloqueante del pipeline no cambió; el gate ya no bloquea este artefacto en la verificación local.

No se bajó el umbral ni se añadieron excepciones. La corrida oficial en Jenkins sigue siendo la única evidencia remota autorizada.

## 4. Verificación local ejecutada

| Verificación | Resultado |
| --- | --- |
| `./gradlew :services:circleguard-dashboard-service:check --no-daemon` | `BUILD SUCCESSFUL`; 23 tests, 0 fallos/errores/omitidos; JaCoCo 92/94 líneas (97,87 %), por encima del gate de 60 %; `verifyUnitTestsRan` sin hallazgos. |
| `./gradlew test --no-daemon --console=plain` tras la migración Boot 4 y el merge con `main` `9c19916` | `BUILD SUCCESSFUL` (RC=0) para toda la suite Java, incluidos los servicios afectados por HU-FORM-07/HU-FORM-08 y las correcciones de `FormSecurityTest` y `HealthStatusServiceTest`. |
| `promtool check rules dashboard-alerts.yml` (Prometheus 2.51.0 en Docker) | `SUCCESS: 2 rules found`. |
| `promtool test rules dashboard-alerts.test.yml` | `SUCCESS`. |
| `python deploy/k8s/components/dashboard-observability/verify.py` (kubectl 1.30.0 / Kustomize 5.0.4 en `/tmp/opencode`) | PASS dev/stage/prod, PASS monitoring, PASS dashboard JSON (7 paneles, filtro `namespace`, panel Loki). |
| `python deploy/k8s/components/form-observability/verify.py` | PASS dev/stage/prod, PASS monitoring, PASS dashboard JSON (6 paneles) tras la reparación del patch. |
| `docker build` del Dockerfile actual | `BUILD SUCCESSFUL`; imagen etiquetada localmente, sin publicación remota. |
| `trivy config` y `trivy image` (0.57.1 y 0.70.0) | IaC 31 archivos, 0 CRITICAL / 74 HIGH; imagen post-remediación 0 HIGH / 0 CRITICAL en ambas versiones. Ver sección 3 y la carpeta de evidencia. |
| `git diff --check` | Sin errores de whitespace al cierre. |

El endpoint Prometheus, los tags y el comportamiento de métricas se probaron con MockMvc y `SimpleMeterRegistry`; **eso no sustituye** un scrape real desde Prometheus.

## 5. Evidencia y documentación

- Informe: `docs/informe/HU-DASH-05.md`.
- Evidencia: `docs/informe/evidencia/HU-DASH-05/` (índice, `trivy-config.json`, `trivy-image.json` inicial, `trivy-image-0.57.1.json` y `trivy-image-0.70.0.json` post-remediación).
- Índice de observabilidad: `observability/README.md` (ruta sugerida por el enunciado; el stack real sigue en `deploy/k8s/monitoring/`).
- Registro IAG: sección 6 de `IAG_REGISTRO.md`.
- Backlog: actualización de P2-04 y P2-06 en `docs/agil/backlog.md`.
- Corrección de la afirmación inexacta sobre “Actuator y Micrometer en los 8 servicios” en `docs/agil/jira-evidence-queue.json`.
- README del servicio y matriz de calidad/seguridad del pipeline actualizados.

## 6. Pendientes para cerrar la HU

1. Aplicar los overlays y `deploy/k8s/monitoring` en el clúster autorizado; confiar el certificado/CA del Ingress si se consulta desde el host (un HTTP 200 con HTML no es evidencia de métricas).
2. Verificar en Prometheus el target `dashboard`, las series `up{app="dashboard"}` y `circleguard_dashboard_promotion_calls_total`, y los siete paneles con el namespace correcto.
3. Confirmar en Loki una línea de log de dashboard con el patrón `[dashboard,cg-dev,…]` y sin departamentos/PII; el panel incluido usa el datasource `loki` ya configurado.
4. Provocar una falla controlada autorizada hacia `promotion-service` (o detener temporalmente solo el pod de dashboard en dev) y comprobar fired en Prometheus, recepción en Alertmanager y recuperación. `promtool` prueba la lógica, no la entrega.
5. Repetir el build de Jenkins con `SERVICE=circleguard-dashboard-service` cuando exista la credencial `terraform-minio-creds` y revisar el gate Trivy de imagen: los escaneos locales con 0.57.1 y 0.70.0 ya dieron 0 CRITICAL, pero la afirmación oficial solo se cierra con la corrida remota. Adjuntar consola/artifacts reales; no reutilizar la imagen local como evidencia remota.
6. Obtener aprobación de par, merge a `main` y actualizar el mapeo Jira cuando se asigne la clave `DCG-*`.
