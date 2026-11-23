<!-- CG-DOC: Containerization and shared CI/CD integration evidence for dashboard-service. -->
# HU-DASH-03: Contenerización e integración de `dashboard-service` al pipeline CI/CD

**Fecha:** 22 de noviembre de 2026  
**Dependencias:** HU-DASH-01 (diagnóstico), HU-DASH-02 (pruebas unitarias y cobertura), pipeline Jenkins compartido.  
**Entregables del enunciado cubiertos:** E1.3 (empaquetamiento inmutable), E1.4 (pipeline Jenkins con Shared Library), E1.5 (semver y trazabilidad), E1.6 (pruebas y reportes), E1.7 (release notes).

## Alcance según el enunciado

El enunciado (`Proyecto_Final_CircleGuard.tex`, alcance de la Parte 1) indica que `notification`, `file` y `dashboard` deben ser **compilados, probados y empaquetados** por el pipeline, y que su despliegue queda para la Parte 2. En coherencia, el catálogo mantiene `dashboard.deploy = false`, pero `docker = true`, `publish = true` y `jacoco = true`; los manifiestos de Kubernetes ya consumen `circleguard-docker/dashboard:<TAG>` para la Parte 2.

La HU se encontró parcialmente resuelta por trabajo previo: el Dockerfile multi-stage, las pruebas, la configuración de SonarQube, el catálogo del Shared Library y las etapas del pipeline ya existían. Esta HU cierra tres brechas concretas:

1. **Trazabilidad y reproducibilidad de la imagen**: el Dockerfile ignoraba `VERSION`, `GIT_COMMIT` y `SOURCE_URL`, aunque `ci.dockerBuildPush` ya los pasa como `--build-arg`.
2. **Verificación del healthcheck**: el pipeline solo comprobaba `HEALTHCHECK` en los servicios núcleo; dashboard lo declara pero no se validaba en CI.
3. **Evidencia documental**: no existía un informe de empaquetado/publicación para dashboard (solo HU-DASH-01 y HU-DASH-02).

## Cambios realizados

| Archivo | Cambio |
| --- | --- |
| `services/circleguard-dashboard-service/Dockerfile` | Directiva `# syntax=docker/dockerfile:1.7`, cache mounts de Gradle, usuario no root con UID/GID fijos `10001`, `COPY --chown`, selección explícita del JAR (excluyendo `*-plain.jar`) y `LABEL org.opencontainers.image.*` alimentado por los argumentos que envía Jenkins. |
| `Jenkinsfile` | `dashboard` añadido a la lista de servicios cuyo `HEALTHCHECK` se verifica en la publicación de imagen. |
| `jenkins/vars/ciServices.groovy` | `healthcheck: true` en la entrada de `dashboard` del catálogo compartido (el override local del Jenkinsfile cubre el periodo en que la librería sigue fijada a `main`). |
| `docs/arquitectura/pipeline.md` | Matriz de calidad/seguridad actualizada con el servicio verificado. |
| `docs/informe/HU-DASH-03.md` | Este informe. |

## Verificación ejecutada (22 de noviembre de 2026)

### 1. Bloqueo por pruebas, cobertura y tests omitidos

```bash
./gradlew :services:circleguard-dashboard-service:clean \
          :services:circleguard-dashboard-service:check --no-daemon --console=plain
```

Resultado: `BUILD SUCCESSFUL in 53s`. Según los reportes generados:

- JUnit: **19 tests, 0 failures, 0 errors, 0 skipped** (4 suites: `PromotionClientTest`, `AnalyticsControllerTest`, `AnalyticsServiceTest`, `KAnonymityFilterTest`).
- JaCoCo: **73/75 líneas cubiertas (97.33 %)**, por encima del umbral bloqueante de 60 % aplicado por `gradle/jacoco-convention.gradle`.
- `verifyUnitTestsRan` se ejecutó y no detectó suites vacías ni omitidas.

Las tareas `test`, `jacocoTestReport` y `jacocoTestCoverageVerification` se restauraron desde la caché de Gradle; el XML de resultados y el reporte JaCoCo se regeneraron en el workspace y fueron los usados para el conteo anterior.

### 2. Selección selectiva del Shared Library

```bash
LIB=$(find ~/.gradle/wrapper/dists -type d -path '*/gradle-8.14/lib' | head -n 1)
CP=$(find "$LIB" -maxdepth 1 -name 'groovy-*.jar' | tr '\n' ':')
java -cp "$CP" groovy.ui.GroovyMain jenkins/test/TestCiServices.groovy
```

Resultado: `ALL CASES PASSED`, incluidos los casos `global Gradle file` (selecciona los nueve componentes, entre ellos `dashboard`) y `CI-only` (los cambios en `Jenkinsfile`/`jenkins/` no seleccionan microservicios). Por eso la ejecución en Jenkins debe solicitarse con `SERVICE=circleguard-dashboard-service` o `SERVICE=all`; el valor `dashboard` no existe en la lista de parámetros del job.

### 3. Imagen multi-stage, no root y con metadatos de release

```bash
docker build \
  --build-arg VERSION=0.1.0 \
  --build-arg GIT_COMMIT=$(git rev-parse HEAD) \
  --build-arg SOURCE_URL=https://github.com/ingesoft5/circle-guard-finalproject \
  -t circleguard-docker/dashboard:0.1.0 \
  -f services/circleguard-dashboard-service/Dockerfile .
```

Resultado: construcción exitosa (`bootJar` en la etapa `build`, JRE Alpine 21 en la etapa final). Verificación de la imagen:

| Comprobación | Resultado |
| --- | --- |
| `docker run --rm --entrypoint id <imagen> -u` | `10001` (no root) |
| `docker inspect .Config.User` | `circleguard` |
| `docker inspect .Config.Healthcheck` | `wget -qO- http://127.0.0.1:8084/actuator/health ...`, intervalo 30 s, start period 45 s, 3 reintentos |
| `org.opencontainers.image.version` | `0.1.0` |
| `org.opencontainers.image.revision` | `1a9199d3c5639823e23c17c87ffaf99e8c08439e` (HEAD verificado) |
| `org.opencontainers.image.source` | URL del repositorio |
| Tags locales | solo `circleguard-docker/dashboard:0.1.0`; **no se generó `latest`** |

En Jenkins la misma construcción usa `--build-arg GRADLE_IMAGE=.../circleguard-base/gradle:8.14-jdk21` y `ALPINE_IMAGE=.../circleguard-base/eclipse-temurin:21-jre-alpine` para evitar el mirror de Docker Hub sin IPv6; los argumentos de versión/commit/source son idénticos.

## Integración con el pipeline compartido

- `ci.unitTest(dashboard)` ejecuta `:services:circleguard-dashboard-service:clean :...:check`; cualquier fallo de test, de cobertura o de `verifyUnitTestsRan` detiene el pipeline antes de publicar.
- `ci.sonarScan` analiza el proyecto `circleguard-dashboard-service` usando `services/circleguard-dashboard-service/sonar-project.properties` y envía `-Dsonar.projectVersion=${TAG}`.
- `ci.qualityGate` usa `waitForQualityGate abortPipeline: true` y una segunda comprobación por API (`api/qualitygates/project_status` con `jq -e '.projectStatus.status == "OK"'`).
- Los stages `Git tag`, `Release notes`, `Publish` y `Docker` están después de las validaciones y solo se ejecutan en `main`.
- `resolveSemver()` calcula la versión desde el último tag `vX.Y.Z` y los Conventional Commits posteriores; `ci.dockerBuildPush` etiqueta la imagen con `${TAG}` y escribe el digest en `image-digest.txt`. No se publica `latest`.
- `ci.archive()` deja en Jenkins los XML de JUnit, el XML/HTML de JaCoCo, los enlaces de SonarQube y el digest de la imagen.

Validación remota parcial (23 de noviembre de 2026): el PR [#163](https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/pull/163) disparó el build [PR-163/1](http://10.147.20.67:8080/job/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/job/PR-163/1/) y el check `continuous-integration/jenkins/pr-merge` terminó en `success`, cubriendo Build → Unit Tests → JaCoCo → SonarQube → Quality Gate. La corrida sobre `main` con `SERVICE=circleguard-dashboard-service` para capturar la URL del build final, el panel de SonarQube y el digest publicado en Nexus sigue pendiente: requiere autenticación en Jenkins, que no está disponible en este entorno. La configuración que produce esos resultados quedó verificada por código y por las pruebas locales anteriores.

## Criterios de aceptación

| Criterio | Estado |
| --- | --- |
| Imagen reproducible multi-stage con Gradle | Sí: dos etapas, wrapper y toolchain fijados, cachés BuildKit, JAR sin `-plain`. |
| Contenedor no root | Sí: UID/GID `10001`, verificado en la imagen construida. |
| Jenkins ejecuta validaciones por el pipeline compartido | Sí: entrada en el catálogo y etapas reutilizables; requiere `SERVICE=dashboard` para una corrida selectiva. |
| Resultados consultables en Jenkins y SonarQube | Sí: artefactos archivados por `ci.archive()` y proyecto Sonar `circleguard-dashboard-service`. |
| Tests/quality gate bloquean publicación | Sí: `check` con gates y `qualityGate` con `abortPipeline: true` antes de `Publish`/`Docker`. |
| Release semver trazable a tag Git, sin `latest` | Sí: `${TAG}` y label `org.opencontainers.image.revision`; verificado localmente con versión de prueba `0.1.0`. |
| Imagen disponible para la Parte 2 | Sí: repositorio `circleguard-docker/dashboard` coincide con los manifiestos K8s; despliegue fuera del alcance de Parte 1. |
