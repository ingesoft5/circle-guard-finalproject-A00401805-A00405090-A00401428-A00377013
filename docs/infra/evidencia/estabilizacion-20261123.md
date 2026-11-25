<!-- CG-DOC: Estabilización on-prem — 2026-11-23. -->
# Estabilización on-prem — 2026-11-23

Registro de las correcciones aplicadas sin borrar recursos.

## ConfigMap `dashboard-config` en `cg-dev`

- Antes: `configmaps "dashboard-config" not found` en `cg-dev`; `cg-stage` y `cg-prod` sí lo tenían.
- El repo ya define el recurso en `deploy/k8s/base/dashboard-config.yml` y lo referencia `deploy/k8s/base/apps.yml`.
- Acción: `kubectl apply -f -` desde el contenido del archivo del repo.
- Resultado: el pod `dashboard-596dfc8d56-lcpqk` pasó de `CreateContainerConfigError` a `Running`; el pod `dashboard-6b5b89fcb6-7r2tq` quedó `1/1`.

## TLS

- Secret `circleguard-tls` presente en `cg-dev`, `cg-stage` y `cg-prod`.
- Overlays `*-tls` aplicados; Ingress con `websecure` y `spec.tls`.
- Traefik `websecure` expuesto en nodePort `30443`; HTTPS verificado vía port-forward con `curl -k -H Host:` para dev/stage/prod → HTTP 200.
- El certificado es autofirmado y la comprobación fue por port-forward; el certificado definitivo, el DNS propio y la exposición pública en 443 corresponden a la publicación externa. Detalle en `E2.4-E2.5-seguridad.md`.

## Sondas

- `gateway` y `promotion` usan `tcpSocket` en los puertos 8087/8088 porque las imágenes `1.4.0` devuelven 401 en `/actuator/health`.
- `deploy/k8s/base/apps.yml` y `deploy/k8s/K8S.md` actualizados para reflejar el estado real.

## Escalado

- `cg-stage` y `cg-prod`: deployments escalados de 0 a 1; gateway `1/1 Running`.
- HPAs con métricas reales; `notification` en 2 réplicas por HPA.

## Monitoreo

- `grafana`, `alertmanager`, `jaeger`, `loki`, `prometheus`, `promtail` y `opencost` en `1/1`.
- `form06-alloy`: configmap corregido con `http { listen_address = "0.0.0.0:12345" }`, con los pods en `0/1` durante la observación del estado D / I/O wait del nodo; no se reemplazó ningún pod.
