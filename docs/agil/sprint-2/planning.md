<!-- CG-DOC: Planeación del Sprint 2 para la Parte 2 (infraestructura on-premise, entrega y reversión). -->
# Sprint 2 — Planeación

**Objetivo del sprint:** entregar la Parte 2 sobre infraestructura propia, con
aprovisionamiento declarativo, despliegue Kubernetes, resiliencia, observabilidad,
seguridad, pruebas de carga y documentación verificable.

**Marco:** Scrum con el tablero [DCG en Jira](https://bomastik-site.atlassian.net/jira/software/projects/DCG/boards/67),
único registro del proceso. La fuente operativa es
[`../plan-circle-guard-5.0.md`](../plan-circle-guard-5.0.md) y el backlog es
[`../backlog.md`](../backlog.md).

## Compromisos por frente

| Entregable | Compromiso | Responsable | Evidencia de cierre |
|---|---|---|---|
| E2.1 | Terraform para `dev`, `stage` y `prod`, backend remoto con control de concurrencia y planes limpios | Integrante 1 | `infra/terraform/`, `docs/infra/evidencia/E2.1-terraform.md` |
| E2.2 | Ocho servicios en k3d, namespaces, Ingress, sondas y HPA | Integrante 2 | `deploy/k8s/base/apps.yml`, `hpa.yml`, `docs/infra/migracion-onpremise.md` |
| E2.3 | Circuit Breaker, Retry y fallback con Resilience4j | Integrante 1 | PR #93 y #139, pruebas de resiliencia |
| E2.4 | Prometheus, Alertmanager, Grafana, Loki, Alloy y Jaeger con logs/trazas seguros | Integrante 3 | `deploy/k8s/monitoring/`, `form06-dev/`, `docs/infra/evidencia/E2.4-E2.5-seguridad.md` |
| E2.5 | Trivy IaC/imágenes y OWASP ZAP como compuertas obligatorias | Integrante 3 | `Jenkinsfile`, `docs/infra/ci-cd-setup.md` |
| E2.6 | Sprint 2, costos, resumen en inglés y sustentación | Integrante 4 | Esta carpeta, informe final y `docs/infra/rollback.md` |
| E2.7 | Cobertura de todas las aplicaciones, incluido `mobile` | Equipo | `mobile/coverage/`, `docs/informe/evidencia/` |
| E2.8 | Versionado y release notes automáticas | Equipo | `docs/release-notes/` |

## Reglas acordadas

1. Una sola aplicación de manifiestos por namespace y siempre con revisión del
   render de Kustomize.
2. Ningún secreto, certificado o llave privada versionada; los ejemplos usan
   valores `change-me`.
3. Las evidencias se capturan al cerrar cada historia. Los pasos que requieren el
   host on-prem se documentan con el comando exacto.
4. Las imágenes son inmutables; un rollback cambia el tag, nunca el PVC ni la
   base de datos.
5. Todo cambio entra por PR con al menos una aprobación y checks verdes.

## Definición de terminado

Un ítem se cierra solo cuando: (a) el código está en la rama de entrega, (b) la
verificación automatizada disponible pasa, (c) la evidencia se enlaza desde el
repositorio y (d) las limitaciones de ejecución quedan por escrito. Los ítems
cuyo despliegue exige certificados o un ventana autorizada se entregan como
“declarado y verificado localmente”, no como “desplegado”.

## Riesgos monitoreados

- RAM del host compartido con Jenkins, SonarQube y Nexus: se limitan réplicas y
  se evita mantener los tres ambientes encendidos.
- Webhook y builds: relé smee verificado y `periodicFolderTrigger` como respaldo.
- Rotación de credenciales del host: los accesos on-prem se ejecutan con
  autorización del usuario; las credenciales no se copian al repositorio.
