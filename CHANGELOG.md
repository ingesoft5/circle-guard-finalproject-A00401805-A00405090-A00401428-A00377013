# Changelog

All notable changes to CircleGuard are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
Semantic Versioning.

## [Unreleased]

### Added

- Full core stack for `dev`, `stage`, and `prod`: auth, identity, promotion, form,
  gateway, Expo web client, PostgreSQL, Neo4j, Kafka, Redis, and OpenLDAP.
- Multi-stage, non-root Docker images for all eight services and the web client.
- Jenkins stages for core image publication, web client publication, dev/stage/prod
  promotion, manual production approval, rollback, release note generation, and
  idempotent GitHub Release publication.
- Sprint 0 and Sprint 1 artifacts, product backlog, change management policy,
  architecture diagrams, and release note template.
- Credential rotation helper that synchronizes GitHub credentials into both
  `infra/state/tools.env` and `deploy/compose/tools/.env`.
- Expanded auth and identity Playwright E2E coverage (login, RBAC, QR validation,
  anonymization, JWT PII checks) and smoke tests for both services.
- Full IAG log entries for auth/identity packaging, deployment, E2E validation,
  and rollback, plus deployment runbooks for both services.

### Changed

- SonarQube now covers promotion and identity in addition to gateway, auth, and form.
- Kafka exposes an external listener on `29092` for end-to-end tests.
- Nginx web client runs as a non-root user on port `8080`.

### Fixed

- Notification health endpoint no longer fails when the external SMTP server is not
  configured.
- `LIBRARY_BRANCH` defaults to `main` in the tools stack.

## [1.2.5] - 2026-11-10

### Added

- Despliegue automático a dev desde `main` y aprobación manual para prod (#39).
- `HEALTHCHECK` del gateway y validación de salud en los tres ambientes.

### Changed

- Healthcheck de OpenLDAP con bind autenticado (#40).
- Pruebas E2E alineadas con `AUTH_URL`, `IDENTITY_URL` y `GATEWAY_URL` de dev (#41).

### Fixed

- Publicación estable a Nexus tras ampliar el volumen de tools y limpiar el blob store.
- Rollback de prod a `1.2.0` verificado y restauración a `1.2.5`.

## [1.2.0] - 2026-11-09

### Added

- Stack completo de la Parte 1: gateway, auth, identity, promotion, form y web.
- Middleware PostgreSQL, Neo4j, Kafka, Redis y OpenLDAP.
- Pipeline multibranch con ejecución selectiva, JaCoCo y quality gate de SonarQube.

### Changed

- Job multibranch renombrado y publicación de las imágenes del núcleo (#34).

### Documentation

- Informe de entrega, diagramas de arquitectura y pipeline, y guías operativas (#38).

## [1.1.1] - 2026-11-09

### Changed

- Actualización del informe de entrega y evidencias de la Parte 1 (#37).

## [1.1.0] - 2026-11-08

### Added

- Identity service packaging, Sonar configuration, and deployment.
- Promotion service deployment with PostgreSQL, Neo4j, Kafka, and Redis.
- Gateway CI follow-up, semver tags, and rollback support.

## [1.0.0] - 2026-11-08

### Added

- Initial GitHub Flow, Jenkins pipeline, gateway packaging, and infrastructure scripts.

[Unreleased]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.2.5...HEAD
[1.2.5]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.2.0...v1.2.5
[1.2.0]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.0.1...v1.1.0
[1.0.0]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/releases/tag/v1.0.0
