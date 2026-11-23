# CG-DOC: FinOps bonus verification (enunciado L284)

Bonificación de **FinOps** del enunciado L284: monitoreo de costos, spot y
scale-to-zero. Evidencia real del clúster on-prem `k3d-cg`.

## Alcance y aislamiento

- KEDA 2.15.1 instalado en `kube-system`; namespace aislado `cg-finops`
  (`finops=enabled`). Los servicios obligatorios de `cg-dev` NO se tocan.
- Dos `ScaledObject` con `minReplicas: 0`, `idleReplicas: 0` y trigger de
  Prometheus para demostrar scale-to-zero real de `file` y `notification`.
- OpenCost desplegado en `monitoring` con ConfigMap de precios on-prem y
  dashboard de costo en Grafana.

## Archivos

- `deploy/finops/keda-install.sh` — instalación idempotente de KEDA.
- `deploy/finops/scaledobjects.yml` — deployments replicas:0 + ScaledObjects.
- `deploy/finops/opencost-install.sh` — OpenCost + pricing + dashboard.
- `deploy/finops/opencost-prices.yml` — ConfigMap de precios del host.
- `deploy/finops/grafana-cost-dashboard.yml` — dashboard de costo.
- `docs/costos/spot-policy.md` — política spot y trade-offs.
- `scripts/verify-finops.sh` — verificador de los checks.

## Verificación ejecutada

```bash
bash deploy/finops/keda-install.sh
bash deploy/finops/opencost-install.sh
kubectl apply -f deploy/finops/scaledobjects.yml
bash scripts/verify-finops.sh
```

Salida real del verificador: _(se registra tras la ejecución en el host)_.

## Dictamen

_Pendiente de verificación real en el clúster._
