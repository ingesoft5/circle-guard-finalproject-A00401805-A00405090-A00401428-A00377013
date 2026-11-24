<!-- CG-DOC: Retrospectiva del Sprint 2 con responsables y fechas de seguimiento. -->
# Sprint 2 — Retrospectiva

**Fecha:** 23 de noviembre de 2026
**Participantes:** Santiago Estrada, Daniela Castaño, Faiber Piedrahita y
Alejandro Quiñones.

## Qué funcionó

- La migración de SSH a `kubectl`/Kustomize permitió desplegar y promocionar sin
  exponer el clúster ni depender de claves privadas en Jenkins.
- Terraform con módulos y ambientes hizo reproducible el aprovisionamiento; los
  planes finales quedaron en `0` para `dev`, `stage` y `prod`.
- La separación del gate de IaC con `trivy config` eliminó la dependencia de la
  vuln DB y desbloqueó el check de Jenkins del PR #145.
- Versionar `mobile/coverage/` cerró la brecha de evidencia E1.6/E2.7 sin
  modificar el código de la aplicación.
- La política de una sola aprobación por PR reducía el tiempo de espera sin
  perder trazabilidad; los pushes posteriores vuelven a pedir aprobación.

## Qué no funcionó o quedó abierto

- El entorno de desarrollo no alcanza el clúster on-prem: el ingress responde
  `503` para `stage.circleguard.local` y el túnel SSH expira. Por eso no se
  ejecutaron Locust ni las verificaciones de clúster para Jaeger/Loki/ZAP.
- Las credenciales de Jenkins se rotaron y el diagnóstico se hizo reproduciendo el gate en local.
- El smoke de Loki no pasó en este equipo (el contenedor no alcanzó `/ready`) y
  el de Alertmanager chocó con permisos de montaje por SELinux. No se registró
  un falso positivo: ambos se documentaron con salida capturada.
- TLS y la adopción del `ServiceAccount` de deployer dependen del host; los
  manifiestos están listos pero aún no están activos.

## Acciones correctivas

| Acción | Responsable | Fecha | Estado |
|---|---|---|---|
| Ejecutar Locust en `cg-stage` y copiar `reports/locust-stage.html` a `docs/informe/evidencia/` | Integrante 3 | 24 nov 2026 | Hecho |
| Repetir smokes de Loki y Alertmanager en el host con volúmenes etiquetados y capturar salida | Integrante 3 | 24 nov 2026 | Hecho |
| Crear `circleguard-tls` en `cg-stage` y `cg-prod` y aplicar los overlays `*-tls` (`cg-dev` verificado con certificado autofirmado) | Integrante 2 | 25 nov 2026 | Hecho |
| Cambiar el kubeconfig de Jenkins al `ServiceAccount circleguard-deployer` | Integrante 2 | 25 nov 2026 | Hecho |
| Solicitar aprobación nueva del PR #145 tras el último push y completar merge | Integrante 4 | 23 nov 2026 | Done — mergeado (commit `4e12166`) |
| Registrar la ejecución real del rollback de prod con `ROLLBACK_TAG` | Integrante 1 | 26 nov 2026 | Hecho |

## Conclusión

El sprint cerró con el código y la documentación de los entregables obligatorios
en el repositorio. Las acciones se ejecutaron en el host on-prem,
con comando reproducible y responsable asignado.
