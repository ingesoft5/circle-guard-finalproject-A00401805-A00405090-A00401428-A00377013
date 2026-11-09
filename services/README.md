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

The CI/CD platform builds and tests all services. The core services
(`auth`, `identity`, `promotion`, `form`, `gateway`) and the web client are
packaged, published and deployed; `notification`, `file` and `dashboard` are
built, tested, packaged and published for Part 2.

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

Package:

```bash
./gradlew :services:circleguard-gateway-service:bootJar
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
