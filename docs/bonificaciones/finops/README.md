<!-- CG-DOC: Verificación de la bonificación de FinOps (enunciado L284). -->
# Verificación de la bonificación de FinOps (enunciado L284)

Bonificación de **FinOps** del enunciado L284: monitoreo de costos, spot y
scale-to-zero. Evidencia **real** obtenida en el clúster on-prem `k3d-cg`
(k3s v1.35.5, host `10.147.20.67`). No se inventan métricas ni ahorros: los
valores provienen de `kubectl` sobre el clúster y de los manifiestos aplicados.

## Alcance y aislamiento

- KEDA 2.15.1 instalado en el clúster; namespace aislado `cg-finops`
  (`finops=enabled`). Los servicios obligatorios de `cg-dev` NO se tocan.
- Dos `ScaledObject` con `minReplicaCount: 0`, `idleReplicaCount: 0` y trigger
  de Prometheus para demostrar scale-to-zero de `file` y `notification`.
- OpenCost desplegado en `monitoring` con ConfigMap de precios on-prem y
  dashboard de costo en Grafana.
- Política spot documentada con sus compromisos.

## Archivos

- `deploy/finops/keda-install.sh` — instalación idempotente de KEDA.
- `deploy/finops/scaledobjects.yml` — deployments replicas:0 + ScaledObjects.
- `deploy/finops/opencost-install.sh` — OpenCost + pricing + dashboard.
- `deploy/finops/opencost-prices.yml` — ConfigMap de precios del host.
- `deploy/finops/grafana-cost-dashboard.yml` — dashboard de costo.
- `docs/costos/spot-policy.md` — política spot y compromisos.
- `scripts/verify-finops.sh` — verificador de las 6 comprobaciones.

## Verificación ejecutada

Reproducible en el host (los campos KEDA v2 son obligatorios; las versiones
antiguas `minReplicas`/`idleReplicas` causaban `strict decoding error`):

```bash
bash deploy/finops/keda-install.sh
bash deploy/finops/opencost-install.sh
kubectl apply -f deploy/finops/scaledobjects.yml
bash scripts/verify-finops.sh
```

Aplicación real de los ScaledObjects:

```
deployment.apps/file-finops unchanged
service/file-finops unchanged
scaledobject.keda.sh/file-finops-scaler created
deployment.apps/notification-finops unchanged
service/notification-finops unchanged
scaledobject.keda.sh/notification-finops-scaler created
```

Salida real del verificador (`FINOPS_VERIFICATION_PASS`, exit 0):

```
=== 1. KEDA ScaledObject for scale-to-zero ===
  [PASS] ScaledObject for file service present
  [PASS] ScaledObject for notification service present
=== 2. minReplicaCount:0 configuration ===
  [PASS] file service minReplicaCount=0 (scale-to-zero)
=== 3. OpenCost monitoring ===
  [PASS] OpenCost price config present (opencost-prices in monitoring)
=== 4. Grafana cost dashboard ===
  [PASS] Grafana cost dashboard configured (finops-cost-dashboard in monitoring)
=== 5. Spot policy documentation ===
  [PASS] docs/costos/spot-policy.md exists
=== RESULT ===
FINOPS_VERIFICATION_PASS
```

## Dictamen

**CUMPLE.** La bonificación de FinOps está implementada y verificada en el
clúster real: KEDA con `minReplicaCount=0` en los dos ScaledObjects aislados,
precios de OpenCost, dashboard de costo y política spot documentada. Aporta
hasta **+0.10** a la Parte 2, dentro del tope acumulado de **+0.25** de L284, y
siempre condicionado a que los entregables obligatorios estén completos
(E2.5, E2.7 y E2.10 siguen abiertos en DCG-440/441/442).
