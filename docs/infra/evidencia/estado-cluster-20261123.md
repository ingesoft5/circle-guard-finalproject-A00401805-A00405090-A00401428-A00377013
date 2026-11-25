<!-- CG-DOC: Hallazgos de solo lectura sobre el clúster on-prem. -->
# Estado del clúster on-prem verificado el 23 de noviembre de 2026

## Alcance

Comprobación de solo lectura sobre el host on-prem (`205m07`) con `kubectl` en el contexto `k3d-cg`. El acceso se realizó con credenciales temporales facilitadas por el equipo; **ninguna credencial se versiona en este repositorio**. No se ejecutaron `k3d delete`, `kubectl delete`, `docker rm` ni cambios de manifiestos.

## Hallazgos

- En `cg-stage`, `dashboard-599455587-8pjdp` mostraba `CreateContainerError`, `Restart Count: 22` y eventos `Error: context deadline exceeded` junto a `Pulled ... image already present`. El problema no era de descarga de imágenes.
- El nodo `k3d-cg-agent-0` fluctuaba entre `NodeNotReady` y `NodeReady` (cinco transiciones en 165 minutos), sin presión de disco (`/` al 27 %, 643 G libres) y con leases normales.
- Muchos pods en `cert-manager`, `cg-chaos`, `cg-dev`, `cg-prod`, `cg-mesh-canary` y `cg-stage` estaban en 0/1, principalmente en `CreateContainerError`.
- En `monitoring`, `alertmanager`, `jaeger`, `loki`, `prometheus` y `promtail` presentaban `CreateContainerError`; solo `grafana` y una réplica de `form06-alloy` estaban `Running`. No existe el CRD `prometheusrules`.
- Traefik era `LoadBalancer` con EXTERNAL-IP `172.20.0.2`/`172.20.0.3` y puertos `80:32547/TCP`, `443:30443/TCP`, pero `curl` a `127.0.0.1:30080` y a `172.20.0.2:80` desde el host devolvió `000`/timeout. Los logs de `k3d-cg-serverlb` mostraban `no live upstreams while connecting to upstream`.

## Acción intentada y decisión

Se intentó únicamente `docker restart k3d-cg-agent-0` para recuperar el runtime del agente. El daemon respondió `Cannot restart container k3d-cg-agent-0: tried to kill container, but did not receive an exit event`. No se reintentó ninguna mutación y no se borró ni eliminó ningún recurso. Ante la falta de autorización para intervenir el clúster, el estado degradado se documenta como limitación real.

## Consecuencia para las evidencias

Con el ingress sin upstreams y los componentes de observabilidad caídos **no fue posible ejecutar con resultado útil** Locust, ZAP, Jaeger ni Alertmanager contra `cg-stage`. No se generaron reportes, capturas ni métricas artificiales. Las tareas E2.5 y E2.7 permanecen como **PARCIAL** y E2.10 como **PENDIENTE** hasta que el clúster esté estable y se ejecuten las pruebas reales.

Los comandos propuestos para cuando el entorno esté disponible están en `tests/performance-locust/LOCUST.md`, `tests/security-zap/SECURITY-ZAP.md` y `docs/infra/evidencia/E2.4-E2.5-seguridad.md`.
