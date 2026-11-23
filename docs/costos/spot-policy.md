# FinOps — Política de instancias Spot y ahorro de costos (enunciado L284)

## Objetivo
Reducir el costo de la infraestructura de CircleGuard sin comprometer la
disponibilidad de los ocho servicios obligatorios de la Parte 2.

## Estrategias aplicadas

### 1. Scale-to-zero con KEDA
Los servicios `file` y `notification` son de consumo intermitente (carga
impulsada por eventos). Se despliegan réplicas aisladas en el namespace
`cg-finops` con `ScaledObject` de KEDA:

- `minReplicas: 0`, `idleReplicas: 0`, `cooldownPeriod: 60s`.
- Trigger Prometheus sobre la tasa de peticiones HTTP.
- Cuando no hay tráfico, el Deployment baja a **0 réplicas** (costo de cómputo
  cero) y escala a 1 automáticamente cuando llega una petición.

Los servicios obligatorios en `cg-dev` **no se modifican**: la demostración
usa un namespace aislado para no arriesgar la nota de los entregables.

### 2. Instancias Spot
Para cargas tolerantes a interrupciones (workers de procesamiento de imágenes
y consumidores de cola no críticos) se recomienda instancias Spot/Azure
Spot/Preemptible VM con:

- Mezcla de al menos 2 tipos de instancia para reducir riesgo de evicción.
- `maxPrice` <= precio on-demand.
- Checkpointing y reintentos idempotentes en el consumidor.
- Nunca usar Spot para la base de datos ni para el gateway (servicios críticos).

Ahorro estimado: 60–90% frente a on-demand para las cargas tolerantes.

### 3. Monitoreo de costos con OpenCost
OpenCost se despliega con un ConfigMap de precios del host on-prem y exporta
métricas `opencost_*` a Prometheus. Un dashboard de Grafana muestra el costo
por namespace y por workload, permitiendo identificar el gasto de cada
servicio y validar el ahorro del scale-to-zero.

## Trade-offs
- Scale-to-zero añade latencia de arranque en frío (~10–20s) al primer
  request tras un periodo inactivo.
- Las instancias Spot pueden ser evacuadas con ~30s de aviso; se mitiga con
  réplicas y reintentos.

## Verificación
Ejecutar `bash scripts/verify-finops.sh` en el host on-prem. Evidencia en
`docs/informe/evidencia/finops/README.md`.
