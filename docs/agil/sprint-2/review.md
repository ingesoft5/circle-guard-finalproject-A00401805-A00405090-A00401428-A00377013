<!-- CG-DOC: Sprint 2 review and evidence mapping for Parte 2 deliverables. -->
# Sprint 2 — Revisión

**Periodo revisado:** 16–22 de noviembre de 2026
**Cierre documental:** 23 de noviembre de 2026
**Entrega:** PR [#145](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/145)
sobre `main`.
**Acta completa:** [Sprint-Review-Sprint-2.docx](Sprint-Review-Sprint-2.docx)

## Resultados por entregable

| Entregable | Estado en la revisión | Evidencia verificable |
|---|---|---|
| E2.1 Infraestructura como código | **Listo** | Módulos `network`, `compute`, `registry`, `storage`, `observability`; ambientes `dev/stage/prod` y backend S3/DynamoDB local; `terraform fmt -check` rc=0 y planes `0` en los tres ambientes (`docs/infra/evidencia/E2.1-terraform.md`). |
| E2.2 Kubernetes | **Listo** | Nueve deployments en `deploy/k8s/base/apps.yml`, namespaces por overlay, Ingress Traefik, sondas Actuator y HPA (`deploy/k8s/K8S.md`). |
| E2.3 Resiliencia | **Listo** | Circuit Breaker/Retry y fallbacks en `auth` e `identity`, con pruebas y configuración externalizada (PRs #93 y #139). |
| E2.4 Observabilidad | **Listo en repositorio; despliegue parcial** | Prometheus/Alertmanager/Grafana/Loki en `monitoring/`; Alloy + Jaeger en `form06-dev/`. Smoke real de OTLP: `PASS: Jaeger returned the synthetic OTLP trace`. Quedan series/real trazas en clúster. |
| E2.5 Seguridad | **Listo en repositorio y on-prem** | `trivy config --severity CRITICAL --exit-code 1 deploy/k8s` rc=0, 0 CRITICAL; gates de imágenes y ZAP en `Jenkinsfile`; ver `docs/infra/evidencia/E2.4-E2.5-seguridad.md`. |
| E2.6 Locust y documentación | **Suite lista con evidencia** | `tests/performance-locust/` y `tests/performance-locust/LOCUST.md`; las métricas reales se completan en `cg-stage`. |
| E2.6 TLS/RBAC | **Declarado y renderizado** | `base/rbac.yml`, `components/tls` y overlays `*-tls`; seis renders Kustomize verificados sin diferencias de host y con `spec.tls` presente solo en las vistas TLS. |
| E2.7 Cobertura | **Listo** | `mobile/coverage/` versionado con 405/406 líneas (99.8 %), 103/103 funciones y 123/141 ramas (87.2 %). |
| Rollback | **Procedimiento listo** | Parámetro `ROLLBACK_TAG` en `Jenkinsfile` y procedimiento manual en `docs/infra/rollback.md`. |

## Checks y evidencia de integración

- Jira se utilizó como herramienta de tablero ágil; el estado de las historias
  se registró allí y se resume en `docs/agil/sprint-2/metrics.md`.
- Jenkins `continuous-integration/jenkins/pr-merge` pasó en el commit `4e12166`
  con el mensaje *This commit looks good* (build #10). Cada push posterior vuelve
  a disparar la cadena; el estado final se verifica en el PR antes del merge.
- Trivy IaC se ejecutó localmente con la misma versión del contenedor de Jenkins
  (0.70.0) y terminó con `rc=0`. El gate usa `trivy config` para no depender de
  la base de datos de vulnerabilidades del mirror on-prem.

## Acuerdos de la revisión

1. No se registran métricas de carga ni capturas de clúster que no se hayan
   ejecutado; los huecos quedan explícitos en los documentos de evidencia.
2. TLS quedó verificado en `cg-dev` con un certificado autofirmado y handshake
   TLSv1.3 sobre `websecure`; el default sigue siendo HTTP y `stage`/`prod`
   se completan con el Secret definitivo y la exposición pública.
3. Jenkins seguirá usando el kubeconfig del host hasta adoptar el
   `ServiceAccount` `circleguard-deployer`.
4. La aprobación del PR debe solicitarse de nuevo después del último push, porque
   las aprobaciones anteriores fueron descartadas por los cambios posteriores.
