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

### Changed

- SonarQube now covers promotion and identity in addition to gateway, auth, and form.
- Kafka exposes an external listener on `29092` for end-to-end tests.
- Nginx web client runs as a non-root user on port `8080`.

### Fixed

- Notification health endpoint no longer fails when the external SMTP server is not
  configured.
- `LIBRARY_BRANCH` defaults to `main` in the tools stack.

## [1.1.0] - 2026-11-08

### Added

- Identity service packaging, Sonar configuration, and deployment.
- Promotion service deployment with PostgreSQL, Neo4j, Kafka, and Redis.
- Gateway CI follow-up, semver tags, and rollback support.

## [1.0.0] - 2026-11-08

### Added

- Initial GitHub Flow, Jenkins pipeline, gateway packaging, and infrastructure scripts.

[Unreleased]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/compare/v1.0.1...v1.1.0
[1.0.0]: https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013/releases/tag/v1.0.0
