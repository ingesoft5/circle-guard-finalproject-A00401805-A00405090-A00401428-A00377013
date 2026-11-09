# HU-ID-03 — Imagen Docker inmutable e integración CI de identity-service

> **Estado:** implementación local completada y validada. Las capturas de Jenkins,
> SonarQube y Nexus se agregan cuando el pipeline se ejecute en el entorno de
> herramientas.

## 1. Resumen

Se empaquetó `circleguard-identity-service` en una imagen Docker multi-stage,
reproducible y no root, y se integró al pipeline multibranch compartido de
Jenkins. El pipeline ejecuta compilación, pruebas, cobertura JaCoCo, análisis
SonarQube con quality gate bloqueante y publica únicamente imágenes etiquetadas
con una versión semántica calculada desde Git tags y Conventional Commits.

| Elemento | Implementación |
|---|---|
| Imagen | `services/circleguard-identity-service/Dockerfile` |
| Usuario runtime | `circleguard` (no root) |
| Salud | `/actuator/health/liveness` |
| Cobertura | JaCoCo, mínimo 60 % de líneas |
| Sonar | Proyecto `circleguard-identity-service` |
| Gate | `CircleGuard Services`, bloqueante |
| Registro | `<NEXUS_REGISTRY>/circleguard-docker/identity:X.Y.Z` |
| Versionado | Git tags `vX.Y.Z` + Conventional Commits |
| Prohibido | Etiqueta `latest` |

## 2. Dockerfile multi-stage

```text
eclipse-temurin:21-jdk
  └── ./gradlew :services:circleguard-identity-service:bootJar

eclipse-temurin:21-jre-alpine
  ├── usuario/grupo circleguard
  ├── /app/app.jar
  ├── EXPOSE 8083
  └── HEALTHCHECK /actuator/health/liveness
```

La imagen final incluye labels OCI con `version`, `revision` y `source`, de modo
que el artefacto publicado es trazable al commit y al repositorio.

## 3. Variables de entorno

`identity-service` ya no contiene secretos hardcodeados. El arranque falla si
faltan las variables obligatorias.

| Variable | Uso |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Usuario de base de datos |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de base de datos |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | Driver JDBC, por defecto PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Brokers de Kafka |
| `VAULT_SECRET` | Secreto de cifrado de la bóveda |
| `VAULT_SALT` | Salt hexadecimal de cifrado |
| `VAULT_HASH_SALT` | Salt del índice ciego |
| `JWT_SECRET` | Clave de firma JWT |
| `SERVER_PORT` | Puerto HTTP, por defecto `8083` |

Ejemplo de ejecución local:

```bash
docker run --rm -p 8083:8083 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://<postgres-host>:5432/circleguard_identity \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=password \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  -e VAULT_SECRET=746573742d7365637265742d33322d63686172732d6c6f6e672d313233343536 \
  -e VAULT_SALT=deadbeef \
  -e VAULT_HASH_SALT=12345678 \
  -e JWT_SECRET=my-super-secret-dev-key-32-chars-long-12345678 \
  <NEXUS_REGISTRY>/circleguard-docker/identity:X.Y.Z
```

## 4. Integración con Jenkins

El catálogo `jenkins/vars/ciServices.groovy` ahora declara identity con:

```groovy
sonarSettings: 'services/circleguard-identity-service/sonar-project.properties'
jacoco        : true
docker        : true
publish       : false
integration   : false
```

El pipeline compartido ejecuta:

1. `Build`: `./gradlew :services:circleguard-identity-service:classes`.
2. `Unit Tests + JaCoCo`: pruebas, reporte y `jacocoTestCoverageVerification`.
3. `SonarQube + Quality Gate`: `sonar-scanner` y `waitForQualityGate` bloqueante.
4. `Integration Tests`: omitido para identity.
5. `Package`: `bootJar`.
6. `Git tag`: crea y empuja `vX.Y.Z` en `main`.
7. `Publish`: no publica artefacto Maven para identity.
8. `Docker`: construye y publica la imagen semver.

Un fallo en pruebas, cobertura o quality gate detiene el pipeline antes de las
etapas `Git tag`, `Publish` y `Docker`.

## 5. Versionado semántico

`jenkins/vars/ciVersion.groovy` calcula la versión:

- Valida `X.Y.Z` o `vX.Y.Z`.
- Si `HEAD` tiene un tag exacto, reutiliza esa versión.
- Sin tags previos, publica `1.0.0`.
- Con tags previos, inspecciona los commits desde el último tag:
  - `BREAKING CHANGE` o `tipo!:` → major.
  - `feat` → minor.
  - `fix`, `perf`, `refactor` u otros → patch.
- Crea el tag Git `vX.Y.Z` sobre el commit construido antes de publicar la imagen.

La imagen se publica como `identity:X.Y.Z`; nunca se publica `identity:latest`.

## 6. Pruebas locales

Ejecutadas con Java 21 en contenedor:

| Comando | Resultado |
|---|---|
| `./gradlew :services:circleguard-identity-service:test jacocoTestReport jacocoTestCoverageVerification` | **BUILD SUCCESSFUL** |
| Pruebas unitarias | **16 pruebas, 0 fallos** |
| Cobertura JaCoCo | **74/93 líneas, 79.6 %** |
| `docker build` | **Imagen construida** |
| `docker run --entrypoint id ... -u` | **UID 100** |
| `docker inspect` | **Healthcheck definido** |
| Arranque con PostgreSQL | **UP / healthy** |

## 7. Evidencias

Carpeta: `docs/informe/evidencia/HU-ID-03/`.

| Evidencia | Estado |
|---|---|
| Build de Jenkins con etapas de identity | Pendiente de ejecución remota |
| Reporte JUnit | Pendiente de captura |
| Reporte JaCoCo ≥ 60 % | Pendiente de captura |
| Quality gate verde | Pendiente de captura |
| Quality gate rojo bloqueando publicación | Pendiente de captura |
| Imagen `identity:X.Y.Z` en Nexus | Pendiente de captura |
| Tag Git `vX.Y.Z` | Pendiente de captura |

## 8. Criterios de aceptación

| Criterio | Estado |
|---|---|
| Imagen multi-stage, no root y con healthcheck | ✅ Implementado y validado localmente |
| Jenkins ejecuta pruebas, JaCoCo ≥ 60 % y reportes | ✅ Integrado en Shared Library |
| Fallo de pruebas/cobertura/gate detiene la publicación | ✅ Orden bloqueante del pipeline |
| Imagen con etiqueta semver trazable a Git | ✅ `ciVersion` + tag Git + labels OCI |
