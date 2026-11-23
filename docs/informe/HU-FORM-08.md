# HU-FORM-08 — Controles de seguridad de form-service (DCG-368)

## 1–2. Gates existentes de Trivy

El catálogo `jenkins/vars/ciServices.groovy` incluye `form` como servicio Java con imagen Docker. En `Jenkinsfile`, la etapa `Trivy Image Security Gate` recorre todas las imágenes de `javaServices` con `docker=true` en `main` y archiva `trivy-form-<tag>.json`; bloquea hallazgos CRITICAL. La etapa `Trivy IaC Security Gate` corre en ramas sin rollback y ejecuta `trivy config` sobre todo `deploy/k8s`, incluidos `base/apps.yml`, `base/form-storage.yml` y componentes de Form; bloquea CRITICAL y archiva `trivy-iac-k8s.json`. No se necesitó un gate duplicado.

La participación está comprobada por lectura de la configuración versionada. Faltan los reportes de una ejecución real de Jenkins para acreditar resultados de escaneo de la imagen publicada y de IaC. Los umbrales son los compartidos del proyecto; hallazgos HIGH se registran pero no bloquean.

## 3–4. Secretos y autorización HTTP

`application.yml` ya no contiene usuario ni contraseña PostgreSQL fijos. `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` se obtienen del entorno; el Deployment de Form usa `cg-secrets/POSTGRES_PASSWORD`. El secreto JWT también se exige mediante `JWT_SECRET` y se referencia desde `cg-secrets`; el valor no está en el repositorio. El operador debe proveer una clave de al menos 32 bytes que coincida con Auth.

El canario `form06-dev` también referencia `cg-secrets/JWT_SECRET` para que una imagen que incluya HU8 pueda arrancar en esa vista aislada.

La validación JWT usa la firma HS256 y el claim `permissions` emitido por Auth. Los endpoints se protegen así:

| Endpoint | Permiso |
|---|---|
| `GET /api/v1/questionnaires/active` | Público, necesario para presentar el cuestionario |
| Otros `/api/v1/questionnaires/**` | `ROLE_ADMIN` |
| `/api/v1/certificates/**` | `ROLE_ADMIN` o `ROLE_HEALTH_CENTER` |
| `/api/v1/surveys/**`, `/api/v1/attachments/**` | JWT válido |
| `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus` | Público para sondas y monitoreo interno |
| Otros | Denegado |

Las pruebas de controlador existentes comprueban contratos HTTP sin filtros; `FormSecurityTest` comprueba de forma independiente ausencia de token, token inválido, autoridad insuficiente, autoridad administrativa y ruta pública. Las pantallas móviles de encuesta, adjuntos y reporte ahora envían el JWT que guarda `AuthContext`. Queda pendiente probar el cliente contra un Form desplegado y definir si deben ligarse `anonymousId` y `adminId` al sujeto del token: la autorización actual controla el acceso al endpoint, pero no garantiza titularidad sobre esos identificadores.

## 5–6. TLS, RBAC y despliegue

El Ingress base enruta `/api/v1/questionnaires`, `/surveys`, `/attachments` y `/certificates` hacia Form. El componente compartido `deploy/k8s/components/tls` configura Traefik `websecure` y el Secret `circleguard-tls` en cada overlay `dev-tls`, `stage-tls` y `prod-tls`. La vista HTTP sigue siendo la predeterminada. La habilitación de HTTPS requiere certificado, DNS, configuración del entrypoint y despliegue por el responsable de infraestructura; el código no acredita TLS activo.

El pod de Form usa ahora el ServiceAccount dedicado `form`, sin montaje del token de API. No se le concede Role ni ClusterRole porque la aplicación no llama a Kubernetes. El ServiceAccount `circleguard-deployer` y el RoleBinding de despliegue son compartidos y limitados por namespace, pero `rbac.yml` indica que Jenkins aún debe adoptar esa identidad; sus permisos efectivos no están verificados aquí. El responsable de CI debe confirmar el kubeconfig y ejecutar `kubectl auth can-i` para el principal de Jenkins, sin consultar Secrets.

## 7–8. ZAP, hallazgos y estado de la evidencia

El gate ZAP de `Jenkinsfile` ahora espera el Deployment `form` en `cg-stage` y ejecuta baseline sobre el cuestionario activo, listado administrativo, encuesta, certificados pendientes y adjuntos. Cada objetivo escribe su propio reporte para evitar mezcla de salidas paralelas; el gate conserva el bloqueo `FAIL-NEW`. El baseline hace solicitudes de descubrimiento, no una prueba autenticada completa de POST ni una verificación de autorización; el reporte de Jenkins y un escaneo autenticado deben adjuntarse después de un despliegue aprobado.

| Hallazgo | Corrección o seguimiento |
|---|---|
| Credencial PostgreSQL fija en configuración Form | Eliminada; usar variables del entorno y `cg-secrets` en Kubernetes. |
| Endpoints de administración y datos sin control local | JWT firmado y autorización por autoridad; falta validar titularidad de IDs y clientes. |
| Form ausente del gate ZAP | Objetivos de Form añadidos; ejecución real en stage pendiente. |
| El cliente móvil no enviaba JWT a Form y registraba el token en consola | Se añadió `Authorization: Bearer` a encuesta, reporte y adjuntos; se retiraron logs que exponían el token. |
| Pod de Form con token Kubernetes implícito | ServiceAccount dedicado sin token montado; permisos efectivos de Jenkins pendientes. |
| Trivy y TLS compartidos | Cobertura declarada; resultados CI y certificado/HTTPS activos pendientes. |

No se declara Done para controles que solo pueden demostrarse con reportes Jenkins, configuración efectiva del clúster o coordinación con sus responsables. Ningún Secret real se leyó o guardó como evidencia.

Verificación local del 22 de noviembre de 2026: `:services:circleguard-form-service:check :services:circleguard-form-service:integrationTest` terminó correctamente (46 pruebas unitarias y dos de integración, sin fallos). `python deploy/k8s/components/form-observability/verify.py` pasó en dev, stage, prod y monitoring usando secretos ficticios. Estas ejecuciones no sustituyen reportes de Trivy/ZAP en Jenkins ni pruebas de TLS/RBAC efectivos.
