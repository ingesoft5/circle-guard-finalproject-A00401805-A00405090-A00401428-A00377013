# HU-DASH-03 — Evidencia de ejecución remota en Jenkins

**Rama de documentación:** `docs/hu-dash-03-jenkins-evidence`  
**PR de código:** [#163](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/163) (mergeado en `main`, commit `cbe6cf7`)

## Objetivo

Registrar lo que ocurrió realmente al intentar validar y publicar `dashboard-service` desde el pipeline Jenkins compartido, sin inventar artefactos que no se generaron.

## Resultado de la corrida sobre `main`

| Dato | Valor |
| --- | --- |
| Build | [main #53](http://10.147.20.67:8080/job/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/job/main/53/) |
| Parámetros | `SERVICE=circleguard-dashboard-service`, `TAG=` (vacío), `SKIP_INTEGRATION=false`, `DEPLOY=false`, `ROLLBACK_TAG=` (vacío) |
| Resultado | **FAILURE** |
| Etapa que falló | `Terraform Plan` (ramas `Plan dev`, `Plan stage`, `Plan prod`) |
| Error | `ERROR: Could not find credentials entry with ID 'terraform-minio-creds'` |
| Etapas omitidas | `Unit Tests + JaCoCo`, `Build`, `SonarQube + Quality Gate`, `Integration Tests`, `Package`, `Git tag`, `Release notes`, `GitHub Release`, `Publish`, `Docker` |
| `image-digest.txt` | Generado vacío en esta corrida; el digest se publica en la corrida siguiente |
| `sonar-links.txt` | Generado vacío; el análisis de esta corrida figura entre las etapas omitidas |

El stage `Terraform Plan` se ejecuta en toda corrida de `main` sin `ROLLBACK_TAG` (`Jenkinsfile`, línea 826), independientemente de `SERVICE` o `SKIP_INTEGRATION`. Como la credencial `terraform-minio-creds` no existe en el Jenkins on-prem, el pipeline aborta antes de llegar a las etapas de dashboard. Por lo tanto esta corrida termina en `Terraform Plan`, antes de la etapa que publica la imagen versionada y el digest.

## Resultado del build de PR

| Dato | Valor |
| --- | --- |
| Build | [PR-163/1](http://10.147.20.67:8080/job/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/job/PR-163/1/) |
| Check GitHub | `continuous-integration/jenkins/pr-merge` = `success` |
| Resultado Jenkins | `SUCCESS` (`Pipeline succeeded: dashboard 1.17.0`) |
| Limitación | El detector de cambios clasificó el diff como `CI-only change; no Java service selected: jenkins/vars/ciServices.groovy`; en consecuencia se omitieron `Unit Tests + JaCoCo`, `Build` y `SonarQube + Quality Gate`. |

El verde del PR confirma que el pipeline aceptó el cambio y calculó semver, pero **no** sirve como evidencia de pruebas ni de quality gate porque esos stages no corrieron.

## Evidencia local válida

Estas comprobaciones sí se ejecutaron y son las que respaldan la HU:

- `./gradlew :services:circleguard-dashboard-service:clean :services:circleguard-dashboard-service:check --no-daemon --console=plain` → `BUILD SUCCESSFUL`, 19 tests, 0 fallos, 0 omitidos.
- JaCoCo: 73/75 líneas (97.33 %).
- `jenkins/test/TestCiServices.groovy` → `ALL CASES PASSED`.
- `docker build ... -t circleguard-docker/dashboard:0.1.0 -f services/circleguard-dashboard-service/Dockerfile .` → exitoso.
- Imagen local: `id -u` = `10001`, `Config.User=circleguard`, `HEALTHCHECK` presente, labels OCI con `version=0.1.0` y `revision=1a9199d...`, sin tag `latest`.

## Cómo capturar la evidencia de la corrida

1. Crear en Jenkins las credenciales que faltan:
   - `terraform-minio-creds` (username/password, desde `TF_STATE_MINIO_USER` / `TF_STATE_MINIO_PASSWORD` de `.env.onpremise`).
   - `terraform-grafana-password` (secret text, desde `TF_GRAFANA_ADMIN_PASSWORD`).
2. Relanzar en `main` con `Build with Parameters`:
   - `SERVICE=circleguard-dashboard-service`
   - `TAG=` vacío
   - `SKIP_INTEGRATION=false`
   - `DEPLOY=false`
   - `ROLLBACK_TAG=` vacío
3. Capturar del build resultante:
   - URL del build y console.
   - `sonar-links.txt` con el enlace `http://10.147.20.67:9000/dashboard?id=circleguard-dashboard-service` y Quality Gate en verde.
   - `image-digest.txt` con `circleguard-docker/dashboard:<TAG>`, el digest y `commit=<sha>`.

## Estado de la corrección

La causa raíz del fallo era únicamente la ausencia de las dos credenciales en la instancia on-prem: el `jenkins.yml` montado en `/var/jenkins_home/cac` (copia del host en `deploy/compose/tools/jenkins/cac/jenkins.yml`, 4667 bytes) estaba desactualizado frente al repo y no declaraba los IDs que consume el `Jenkinsfile` (`terraform-minio-creds` y `terraform-grafana-password`). Las tres variables sí existían en `.env.onpremise` y en el entorno del contenedor `cg-jenkins`.

Acciones ejecutadas:

1. Respaldo del JCasC del host como `jenkins.yml.bak-20261123` e inserción de los dos bloques `credentials` con `${TF_STATE_MINIO_USER}` / `${TF_STATE_MINIO_PASSWORD}` y `${TF_GRAFANA_ADMIN_PASSWORD}`.
2. Recarga de Configuration as Code vía `POST /manage/configuration-as-code/reload` (HTTP 200, con crumb y cookie de sesión).
3. Verificación en `/credentials/store/system/domain/_/api/json`: los 6 IDs presentes, incluidos `terraform-minio-creds` y `terraform-grafana-password`.
4. Relanzamiento de `main` con `SERVICE=circleguard-dashboard-service`, `TAG=`, `SKIP_INTEGRATION=false`, `DEPLOY=false`, `ROLLBACK_TAG=` (cola `queue/item/1547`). El build #54 histórico ya había fallado por esta misma causa antes de la corrección; el nuevo build quedó en cola detrás de `PR-166/5` y `main/57`, que ocupaban los dos executors.

Al liberarse el executor: capturar consola, `sonar-links.txt` e `image-digest.txt` del build resultante y archivarlos en esta carpeta.

## Archivos de esta carpeta

- `main-build-53-console.txt` — consola completa del build fallido de `main`.
- `pr-build-1-console.txt` — consola completa del build del PR #163.

No se incluyen `image-digest.txt` ni `sonar-links.txt` porque ambos artefactos del build #53 están vacíos.
