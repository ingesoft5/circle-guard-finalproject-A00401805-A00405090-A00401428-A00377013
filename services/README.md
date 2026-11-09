<!-- CG-DOC: Overview of CircleGuard microservices, build commands, and documentation conventions. -->
# Services

## Purpose

`services/` contains the Spring Boot microservices of CircleGuard:

- `circleguard-auth-service`
- `circleguard-dashboard-service`
- `circleguard-file-service`
- `circleguard-form-service`
- `circleguard-gateway-service`
- `circleguard-identity-service`
- `circleguard-notification-service`
- `circleguard-promotion-service`

The current CI/CD platform builds and validates `circleguard-gateway-service`,
`circleguard-auth-service`, `circleguard-form-service`, and
`circleguard-identity-service`. Identity and gateway also publish Docker images
with semantic version tags.

## Standard layout

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

## Build and test

```bash
./gradlew :services:circleguard-gateway-service:clean \
  :services:circleguard-gateway-service:test \
  :services:circleguard-gateway-service:jacocoTestReport \
  :services:circleguard-gateway-service:jacocoTestCoverageVerification
```

Identity service build, tests, and coverage:

```bash
./gradlew :services:circleguard-identity-service:test \
  :services:circleguard-identity-service:jacocoTestReport \
  :services:circleguard-identity-service:jacocoTestCoverageVerification
```

Identity image:

```bash
docker build -f services/circleguard-identity-service/Dockerfile \
  --build-arg VERSION=1.0.0 \
  --build-arg GIT_COMMIT="$(git rev-parse HEAD)" \
  -t circleguard/identity:1.0.0 .
```

The identity container requires `SPRING_DATASOURCE_*`,
`SPRING_KAFKA_BOOTSTRAP_SERVERS`, `VAULT_SECRET`, `VAULT_SALT`,
`VAULT_HASH_SALT`, and `JWT_SECRET`. See `docs/informe/HU-ID-03.md`.

Package:

```bash
./gradlew :services:circleguard-identity-service:bootJar
```

Publish to Nexus:

```bash
NEXUS_URL=http://nexus:8081 \
NEXUS_USER=... \
NEXUS_PASSWORD=... \
./gradlew :services:circleguard-gateway-service:publish
```

## Documentation

- Public classes and methods use Javadoc in English.
- Package-level responsibilities are documented with `package-info.java` where needed.
- Business rules, security decisions, and Redis failure handling are documented where the behavior is not obvious.
- Every file includes a `CG-DOC` purpose header.

See `docs/standards/documentation.md` and `docs/FILE_INDEX.md`.
