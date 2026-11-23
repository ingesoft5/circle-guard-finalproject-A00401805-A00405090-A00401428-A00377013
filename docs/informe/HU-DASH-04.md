<!-- CG-DOC: Deployment, operation and rollback evidence for dashboard-service. -->
# HU-DASH-04: Despliegue de `dashboard-service` en Kubernetes (dev, stage y prod)

**Fecha:** 22 de noviembre de 2026
**Dependencias:** HU-DASH-03 (imagen versionada) e infraestructura Kubernetes compartida (Parte 2).
**Entregables del enunciado cubiertos:** E1.5, E2.2, E2.3, E2.4, E2.5, E2.6, E2.8 y E2.9.

## Alcance y plataforma

La plataforma evaluada es **exclusivamente on-premise**: un único host Ubuntu
(`10.147.20.67`, usuario `docker`) con el clúster k3d `cg` (k3s `v1.35.5-k3s1`), los
namespaces `cg-dev`, `cg-stage` y `cg-prod`, el registry HTTP local `10.147.20.67:5000`
y Traefik publicado en `:30080` con routing por header `Host`. No se aprovisiona EKS,
ni ningún servicio de nube: E2.2 se cumple con la opción k3s/k3d prevista en el
enunciado. La URL `on-premise://10.147.20.67/cg-main-tools` usada como etiqueta OCI de
la imagen identifica el checkout local de origen.

El enunciado (`Proyecto_Final_CircleGuard.tex:260`) reserva el despliegue de
`dashboard` para la Parte 2, por lo que la HU es coherente con esa planificación.
El trabajo previo (HU-DASH-03) ya había resuelto el empaquetamiento; esta HU cierra
las brechas operativas que impedían ejecutarlo de forma controlada.

## Situación previa y brechas cerradas

| Brecha detectada | Estado antes de HU-DASH-04 | Resolución |
| --- | --- | --- |
| Configuración externalizada incompleta | URL JDBC y usuario hardcodeados en `apps.yml`; URL de `promotion-service` caía a `localhost:8088` dentro del clúster | ConfigMap `dashboard-config` + placeholders en `application.yml`; la password sigue en `cg-secrets` |
| Sondas genéricas | `/actuator/health` sin grupos Actuator habilitados | `health.probes.enabled: true` y sondas `/actuator/health/{readiness,liveness}` |
| Sin HPA y réplicas fijas | `replicas: 1` en el Deployment, sin HPA de dashboard | HPA `dashboard` 1–3 @70 % CPU; el Deployment ya no declara réplicas |
| Sin exposición HTTP | Ingress sin ruta analytics; clientes apuntaban a `localhost:8084` | Ruta `/api/v1/analytics` → `dashboard:8084`, cubierta por el componente TLS |
| Observabilidad incompleta | Único servicio sin `micrometer-registry-prometheus` ni anotaciones de scrape | Dependencia Micrometer + anotaciones `prometheus.io/*` en Service y pod |
| Catálogo de pipeline desactualizado | `dashboard.deploy = false` (documentaba Parte 1) | `deploy: true` en `ciServices.groovy` y en la lista `deployable` del `Jenkinsfile` |
| Rollback no ejecutado | `docs/infra/rollback.md` indicaba que solo se había simulado el render | Rollback ejecutado y evidenciado en `cg-prod` (sección 5) |

## Cambios realizados

| Archivo | Cambio |
| --- | --- |
| `services/circleguard-dashboard-service/src/main/resources/application.yml` | Placeholders para `SERVER_PORT`, datasource y `PROMOTION_SERVICE_URL`; bloque `management` con exposición `health,info,prometheus`, `health.probes.enabled` y exportación Prometheus. |
| `services/circleguard-dashboard-service/build.gradle.kts` | `io.micrometer:micrometer-registry-prometheus`. |
| `services/circleguard-dashboard-service/src/test/java/.../PromotionClientConfigurationTest.java` | Nueva prueba que verifica la inyección de `circleguard.promotion-service.url` desde configuración externa. |
| `deploy/k8s/base/dashboard-config.yml` | ConfigMap con `SERVER_PORT`, URL JDBC, usuario y `PROMOTION_SERVICE_URL`. Sin credenciales. |
| `deploy/k8s/base/kustomization.yml` | Registro del ConfigMap anterior. |
| `deploy/k8s/base/apps.yml` | Service/pod con anotaciones de scrape; Deployment sin `replicas`, consumiendo `dashboard-config`, con password desde `cg-secrets` y sondas de grupos Actuator. |
| `deploy/k8s/base/hpa.yml` | HPA `dashboard` 1–3 réplicas @70 % CPU, `scaleDown.stabilizationWindowSeconds: 300`. |
| `deploy/k8s/base/ingress.yml` | Ruta `/api/v1/analytics` → `dashboard:8084`. |
| `deploy/k8s/overlays/{dev,stage,prod}/kustomization.yml` | Misma imagen `circleguard-docker/dashboard:1.10.0` en los tres ambientes. |
| `jenkins/vars/ciServices.groovy`, `Jenkinsfile` | `dashboard.deploy = true`; el pipeline shared promociona la misma imagen a dev/stage/prod con aprobación manual pre-existente en `Approval prod`. |
| Documentación | `docs/infra/rollback.md`, `docs/arquitectura/pipeline.md`, `docs/agil/backlog.md`, `deploy/k8s/README.md`, `CHANGELOG.md`, `IAG_REGISTRO.md`, README del servicio e informe HU-DASH-03 actualizados. |

## Verificación local (sin clúster)

```bash
./gradlew :services:circleguard-dashboard-service:check --no-daemon --console=plain
python3 /tmp/opencode/validate_hu_dash_04.py .   # aserciones sobre manifiestos y configuración
java -cp "$CP" groovy.ui.GroovyMain jenkins/test/TestCiServices.groovy
```

Resultados: `BUILD SUCCESSFUL` con **20 tests, 0 fallos, 0 omitidos** y JaCoCo
**73/75 líneas (97,33 %)**; `ALL STATIC CHECKS PASSED` y `ALL CASES PASSED`.

## Despliegue on-premise

La imagen `circleguard-docker/dashboard:1.10.0` se construyó en el host on-prem con
las imágenes base del mirror local (`localhost:5000/circleguard-base/*`) y se importó
directamente al clúster k3d (`docker save` + `ctr images import` en el nodo servidor),
porque el push al registry local dio *timeout* HTTPS. Antes de aplicar, los tres
`secrets.env` se regeneraron desde los Secret ya existentes en cada namespace, para
no sobrescribir credenciales reales con los placeholders del repositorio.

Los manifiestos renderizados de cada overlay se aplicaron con `kubectl apply -f` y se
validaron con consultas puntuales de imagen y réplicas (sin comandos `watch`). La
evidencia por ambiente está en `docs/informe/evidencia/HU-DASH-04/<env>/post-deploy/`.

| Ambiente | Imagen efectiva | Pods Ready | HPA | Ingress |
| --- | --- | --- | --- | --- |
| `cg-dev` | `circleguard-docker/dashboard:1.10.0` | ver evidencia | `dashboard 1/3`, CPU 70 % | `/api/v1/analytics` |
| `cg-stage` | `circleguard-docker/dashboard:1.10.0` | ver evidencia | `dashboard 1/3`, CPU 70 % | `/api/v1/analytics` |
| `cg-prod` | `circleguard-docker/dashboard:1.10.0` | ver evidencia | `dashboard 1/3`, CPU 70 % | `/api/v1/analytics` |

## Aprobación manual a producción

El `Jenkinsfile` conserva la etapa `Approval prod` con
`input message: 'Approve production deployment', ok: 'Deploy'` antes de
`Deploy prod`. La promoción usa `ci.deploy('prod', TAG)`, que aplica el mismo tag
que en dev y stage. Esta HU verificó la configuración de la compuerta por código;
la ejecución interactiva del `input` depende del job de Jenkins y queda registrada
como configuración vigente, no como una corrida nueva de Jenkins.

## Rollback ejecutado en `cg-prod`

Tras desplegar `1.10.0`, se ejecutó la reversión al tag anterior `1.4.0` con
`kubectl -n cg-prod rollout undo deploy/dashboard` y se fijó el tag con
`kubectl -n cg-prod set image ...` para que el estado final quede determinístico.
Se capturaron `rollout history`, `rollout status`, `get pods`, la configuración
effectiva del ConfigMap y las respuestas de `/actuator/health`,
`/actuator/health/{readiness,liveness}` y `/api/v1/analytics/summary`.

Evidencia: `docs/informe/evidencia/HU-DASH-04/prod/pre-rollback/`,
`docs/informe/evidencia/HU-DASH-04/prod/post-rollback/` y
`docs/informe/evidencia/HU-DASH-04/prod/rollback-commands.txt`.
El procedimiento operativo quedó actualizado en `docs/infra/rollback.md`, que ya no
describe la reversión como una simulación local.

## Criterios de aceptación

| Criterio | Estado | Evidencia |
| --- | --- | --- |
| Dashboard desplegado y operativo en los tres ambientes | Sí | `evidence/{dev,stage,prod}/post-deploy/` |
| Configuración externalizada y sin credenciales en el repo | Sí | `dashboard-config.yml`, `08-configmap.yaml`, `git diff` |
| Sondas liveness/readiness correctas | Sí | `11-probes.txt` y `04-rollout-status.txt` |
| HPA y acceso según arquitectura | Sí | `06-hpa.txt`, `09-ingress-paths.txt`, `12-analytics-summary.json` |
| Pipeline promociona la misma imagen versionada | Sí | `deployable` con `dashboard` y tag `1.10.0` en los tres overlays |
| Aprobación manual antes de producción | Sí (configuración verificada) | `Jenkinsfile` etapa `Approval prod` |
| Rollback funcional y documentado | Sí | `evidence/prod/rollback-commands.txt` y `docs/infra/rollback.md` |
