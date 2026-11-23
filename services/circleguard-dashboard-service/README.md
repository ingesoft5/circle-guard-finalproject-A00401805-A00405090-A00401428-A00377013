<!-- CG-DOC: Architecture, dependencies, build evidence, and technical findings for dashboard-service. -->
# dashboard-service: funcionamiento y estructura

Este documento registra el diagnóstico de `circleguard-dashboard-service` para HU-DASH-01. Describe el código existente y los requisitos conocidos para continuar con pruebas y empaquetado.

## Cómo funciona dashboard-service

`dashboard-service` ofrece datos agregados de salud y tendencias para las pantallas administrativas de CircleGuard. El cliente Expo consulta su API HTTP; el servicio obtiene los conteos actuales de `promotion-service` y consulta su propia base PostgreSQL para las tendencias. En los resultados por departamento y ubicación aplica reglas de privacidad para ocultar conteos pequeños.

```mermaid
flowchart LR
    cliente["Cliente Expo"] -->|GET /api/v1/analytics| controlador["AnalyticsController"]
    controlador --> analitica["AnalyticsService"]
    analitica --> clientePromo["PromotionClient"]
    clientePromo -->|HTTP GET: estadísticas de salud| promocion["promotion-service"]
    analitica -->|SQL: tendencias| postgres[(PostgreSQL: circleguard_dashboard)]
    analitica --> filtro["KAnonymityFilter: umbral 5"]
```

El servicio aporta una vista de los estados de salud a nivel de campus y departamento. Se comunica con `promotion-service` por HTTP; no consume eventos Kafka ni se conecta directamente a la base de datos de ese microservicio.

## Organización

El código está en `src/main/java/com/circleguard/dashboard/`. `DashboardApplication.java` inicia Spring Boot.

| Paquete | Responsabilidad |
| --- | --- |
| `controller` | Expone los endpoints de analítica. |
| `service` | Consulta y prepara estadísticas, tendencias y protección de conteos pequeños. |
| `client` | Consulta la API HTTP de `promotion-service`. |
| `src/main/resources` | Contiene la configuración del servicio y la migración SQL. |
| `src/test` | Contiene las pruebas existentes del controlador. |

## Controlador y rutas HTTP

`AnalyticsController` expone las siguientes operaciones bajo `/api/v1/analytics`:

| Método | Ruta | Responsabilidad |
| --- | --- | --- |
| `GET` | `/api/v1/analytics/summary` | Devuelve el resumen de estados de salud obtenido de `promotion-service`. |
| `GET` | `/api/v1/analytics/health-board` | Devuelve las mismas estadísticas globales para el tablero de salud. |
| `GET` | `/api/v1/analytics/department/{department}` | Obtiene estadísticas del departamento y aplica `KAnonymityFilter`. |
| `GET` | `/api/v1/analytics/trends/{locationId}` | Consulta entradas por hora en una ubicación y oculta conteos inferiores a cinco. `locationId` es un UUID. |
| `GET` | `/api/v1/analytics/time-series` | Consulta conteos de estados por período. Acepta `period` (`hourly` por defecto; `daily` como alternativa) y `limit` (24 por defecto). |

## Servicios y cliente HTTP

| Clase | Responsabilidad |
| --- | --- |
| `AnalyticsService` | Obtiene resúmenes de `promotion-service` y consulta `entry_logs` y `status_events` mediante `JdbcTemplate`. |
| `KAnonymityFilter` | Enmascara conteos positivos menores que cinco; si la población del departamento está entre uno y cuatro, oculta el resultado completo. |
| `PromotionClient` | Realiza las dos consultas HTTP de estadísticas a `promotion-service` y devuelve un mapa de error si la llamada falla. |

El servicio no define entidades ni repositorios JPA propios; las consultas locales de `AnalyticsService` usan SQL mediante `JdbcTemplate`.

## Base de datos y migración

La base configurada es `circleguard_dashboard` en PostgreSQL. La única migración, `V1__initial_dashboard_schema.sql`, crea `entry_logs` con `location_id`, `entry_time` y `status`, además de un índice por ubicación y hora. Flyway está declarado en `build.gradle.kts` y las migraciones se encuentran en `src/main/resources/db/migration/`.

## Dependencias y configuración

| Dependencia | Uso | Configuración actual |
| --- | --- | --- |
| PostgreSQL | Guarda los registros consultados para tendencias. | `application.yml` apunta a `localhost:5432/circleguard_dashboard`. |
| `promotion-service` | Provee los conteos de salud globales y por departamento. | `PromotionClient` usa `circleguard.promotion-service.url`, con valor predeterminado `http://localhost:8088`. |
| Gradle y Java | Compilan el módulo con Spring Boot 3.2.4. | El wrapper solicita Gradle 8.14 y el proyecto requiere Java 21. |

`PromotionClient` consulta `GET /api/v1/health-status/stats` y `GET /api/v1/health-status/stats/department/{department}`. El servicio expone su API en el puerto `8084`. Para empaquetarlo ya existe un `Dockerfile` cuyo contexto de construcción es la raíz del repositorio. `build.gradle.kts` también declara publicación en Nexus con `NEXUS_URL`, `NEXUS_USER` y `NEXUS_PASSWORD`.

## Verificación de compilación

Ejecutada el 17 de noviembre de 2026 desde la raíz del repositorio, en la rama `feat/HU-DASH-01-DIAGNOSTICO-DASHBOARD`:

```powershell
.\gradlew.bat :services:circleguard-dashboard-service:classes --console=plain
```

**Resultado:** `BUILD SUCCESSFUL in 32s`; Gradle reportó `3 actionable tasks: 3 executed` y el proceso terminó con código de salida 0. Gradle informó el uso de funciones obsoletas que serán incompatibles con Gradle 9.0. Esta verificación no inicia el servicio ni ejecuta pruebas.

## Observabilidad y seguridad (HU-DASH-05)

Desde la rama `feat/HU-DASH-05-integrar-observabilidad-y-seguridad`, el servicio declara `micrometer-registry-prometheus` junto al Actuator que ya existía. La configuración `management` expone solo `health`, `info` y `prometheus`, oculta los detalles de salud, habilita los grupos `liveness`/`readiness` y añade las etiquetas comunes `service=dashboard` y `environment=${DASHBOARD_ENVIRONMENT:local}`. El patrón de log incluye `[dashboard,environment,traceId,spanId]`; si no hay trazas activas, los dos últimos campos aparecen como `-`.

| Señal | Nombre / ruta | Uso |
| --- | --- | --- |
| Endpoint | `GET /actuator/prometheus` | Scrape interno del pod en el puerto `8084`. |
| Sondas | `/actuator/health/liveness`, `/actuator/health/readiness` | K8s usa estos grupos mediante el componente `dashboard-observability/app`. |
| Contador | `circleguard.dashboard.promotion.calls{operation,outcome}` | Llamadas a `promotion-service`, por operación y resultado. |
| Temporizador | `circleguard.dashboard.promotion.call.duration{operation,outcome}` | Duración de esas llamadas; permite identificar demoras. |

Los tags son acotados (`operation`: `healthStats`/`departmentStats`; `outcome`: `success`/`error`). No se registran departamentos, URLs, cuerpos ni datos personales en métricas ni en los logs propios del cliente. El error conserva únicamente la operación, el resultado y la clase de la excepción.

En Kubernetes, `deploy/k8s/components/dashboard-observability/` añade las anotaciones de scrape, el ambiente desde el namespace y las sondas; su parte de monitoring genera las reglas `DashboardServiceDown` (critical) y `DashboardPromotionCallFailures` (warning), además del panel Grafana `circleguard-dashboard-overview` con siete fuentes (disponibilidad, RPS, 5xx, latencia HTTP, llamadas promotion, p95 y logs Loki). Los componentes ya están referenciados en los overlays `dev`, `stage` y `prod`.

Verificación local: `./gradlew :services:circleguard-dashboard-service:check --no-daemon` (23 pruebas, JaCoCo 97,87 %), `promtool check/test rules`, los `verify.py` de los componentes y los escaneos Trivy listados en `docs/informe/evidencia/HU-DASH-05/README.md`. El scrape real, el panel con datos, Loki, la entrega de alertas y el build remoto siguen pendientes de despliegue autorizado. El escaneo de la imagen construida localmente reportó 9 `CRITICAL`, por lo que el gate actual bloquearía la entrega; no se bajó el umbral. Detalle completo en `docs/informe/HU-DASH-05.md`.

## Hallazgos para las siguientes HU

1. `getTimeSeries` consulta `status_events`, pero no existe una migración que cree esa tabla. Si la consulta falla, el método devuelve datos simulados; la serie temporal no demuestra datos reales en ese caso.
2. `V1` crea `entry_logs`, pero no se encontró en el repositorio un proceso que inserte registros en esa tabla. Las tendencias por ubicación pueden quedar vacías aunque el servicio compile.
3. La configuración de PostgreSQL y el valor predeterminado de `promotion-service` usan `localhost`; `application.yml` contiene credenciales de desarrollo y `ddl-auto: update` junto con Flyway. Las pruebas que carguen todo el contexto y una ejecución en contenedor necesitan direcciones y credenciales apropiadas, además de las dependencias disponibles.
4. Ante un fallo de `promotion-service`, `PromotionClient` devuelve un mapa con `error` y el controlador responde con HTTP 200. Las pruebas deben distinguir ese resultado de una respuesta de estadísticas válida.
5. La prueba actual `AnalyticsControllerTest` cubre `health-board` y `trends`, con `AnalyticsService` simulado. Faltan pruebas directas del filtro de privacidad, del cliente HTTP y de las demás rutas; corresponden a la HU de pruebas.
6. El `Dockerfile` y la publicación en Nexus están configurados, pero esta HU solo verificó la compilación Gradle. La construcción de imagen y la publicación pertenecen a la HU de empaquetado.
