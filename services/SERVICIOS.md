<!-- CG-DOC: Resumen de los microservicios de CircleGuard, comandos de compilación y convenciones de documentación. -->
# Servicios

## Propósito

`services/` contiene los microservicios Spring Boot de CircleGuard:

- `circleguard-auth-service`
- `circleguard-dashboard-service`
- `circleguard-file-service`
- `circleguard-form-service`
- `circleguard-gateway-service`
- `circleguard-identity-service`
- `circleguard-notification-service`
- `circleguard-promotion-service`

La plataforma CI/CD compila y prueba todos los servicios. Los servicios núcleo
(`auth`, `identity`, `promotion`, `form`, `gateway`) y el cliente web se
empaquetan, publican y despliegan; `notification`, `file` y `dashboard` se
compilan, prueban, empaquetan y publican para la Parte 2.

## Estructura estándar

```text
services/<service>/
├── build.gradle.kts
├── Dockerfile
├── README.md
└── src/
    ├── main/java
    ├── main/resources
    └── test/java
```

## Compilación y pruebas

```bash
./gradlew :services:circleguard-gateway-service:clean \
  :services:circleguard-gateway-service:test \
  :services:circleguard-gateway-service:jacocoTestReport \
  :services:circleguard-gateway-service:jacocoTestCoverageVerification
```

Compilación, pruebas y cobertura del servicio de identidad:

```bash
./gradlew :services:circleguard-identity-service:test \
  :services:circleguard-identity-service:jacocoTestReport \
  :services:circleguard-identity-service:jacocoTestCoverageVerification
```

Compilación, pruebas y cobertura del servicio de archivos:

```bash
./gradlew :services:circleguard-file-service:test \
  :services:circleguard-file-service:jacocoTestReport \
  :services:circleguard-file-service:jacocoTestCoverageVerification
```

Imagen de identidad:

```bash
docker build -f services/circleguard-identity-service/Dockerfile \
  --build-arg VERSION=1.0.0 \
  --build-arg GIT_COMMIT="$(git rev-parse HEAD)" \
  -t circleguard/identity:1.0.0 .
```

El contenedor de identidad requiere `SPRING_DATASOURCE_*`,
`SPRING_KAFKA_BOOTSTRAP_SERVERS`, `VAULT_SECRET`, `VAULT_SALT`,
`VAULT_HASH_SALT` y `JWT_SECRET`. Consulte `docs/informe/HU-ID-03.md`.

Empaquetado:

```bash
./gradlew :services:circleguard-identity-service:bootJar
```

Publicación en Nexus:

```bash
NEXUS_URL=http://nexus:8081 \
NEXUS_USER=... \
NEXUS_PASSWORD=... \
./gradlew :services:circleguard-gateway-service:publish
```

## Documentación

- Las clases y métodos públicos usan Javadoc en inglés.
- Las responsabilidades a nivel de paquete se documentan con `package-info.java` cuando es necesario.
- Las reglas de negocio, las decisiones de seguridad y el manejo de errores de Redis se documentan cuando el comportamiento no es evidente.
- Cada archivo incluye una cabecera de propósito `CG-DOC`.

Consulte `docs/standards/documentation.md`.
