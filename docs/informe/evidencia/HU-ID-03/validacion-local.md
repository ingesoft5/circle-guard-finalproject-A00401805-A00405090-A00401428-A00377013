# Validación local HU-ID-03

Resultados obtenidos antes de la validación remota de Jenkins, SonarQube y Nexus.

## Pruebas y cobertura

```text
./gradlew :services:circleguard-identity-service:clean \
  :services:circleguard-identity-service:check \
  :services:circleguard-identity-service:bootJar

BUILD SUCCESSFUL
```

| Métrica | Resultado |
|---|---|
| Clases de prueba | 5 |
| Pruebas ejecutadas | 17 |
| Fallos | 0 |
| Líneas cubiertas | 88 |
| Líneas totales | 93 |
| Cobertura de líneas | 94.6 % |
| Umbral configurado | 60 % |

## Imagen Docker

```text
docker build \
  --build-arg VERSION=1.0.0-test \
  --build-arg GIT_COMMIT=$(git rev-parse HEAD) \
  --build-arg SOURCE_URL=https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013 \
  -t circleguard/identity:merged-test \
  -f services/circleguard-identity-service/Dockerfile .
```

Resultado:

```text
UID=100
user=circleguard
HEALTHCHECK definido para /actuator/health/liveness
Labels OCI: version, revision, source
```

## Arranque con PostgreSQL

```text
Contenedor: Up (healthy)
GET /actuator/health/liveness -> {"status":"UP"}
Flyway: Successfully applied 1 migration to schema "public", now at version v1
Tomcat started on port 8083
```

## Estado remoto pendiente

La captura de Jenkins, SonarQube y Nexus requiere credenciales del host de
herramientas. El estado se completa cuando el pipeline de `main` publique la
imagen y el tag semver.
