# CG-DOC: Patrones de diseño verificados en el código (E2.4)

## Patrones existentes (evidencia en código)

| Patrón | Evidencia |
| --- | --- |
| API Gateway | `services/circleguard-gateway-service` es el punto de entrada del campus. |
| Event-driven architecture | `notification-service`, `form-service` y `promotion-service` consumen/publican topics Kafka. |
| Database per service | Cada microservicio declara su propio `application.yml` y esquema. |
| Cache-aside | `gateway-service` consulta Redis para el estado del QR y TTL corto. |
| State machine | `promotion-service` implementa Sospechoso → Probable → Confirmado con TTL de 14 días. |

## Patrones implementados o mejorados

1. **Circuit Breaker / Retry (resiliencia)** — Resilience4j en `gateway-service`
   y `identity-service`, con pruebas de falla inyectada. *Trade-off*: latencia
   adicional al abrir el circuito frente a protección del downstream.
2. **External Configuration (configuración)** — perfiles Spring y ConfigMaps
   por ambiente en `deploy/k8s/overlays/*`. *Trade-off*: centralización vs.
   dependencia del cluster para arrancar.
3. **Observer/Micrometer instrumentation (adicional)** — contadores y
   histogramas en Actuator consumidos por Prometheus y Grafana. *Trade-off*:
   overhead de medición frente a observabilidad de negocio.
