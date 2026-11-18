# Changelog

All notable changes to CircleGuard are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
Semantic Versioning.

## [Unreleased]

### Changed

- **File service (FS-CI-01)**: Implementado `loadFile` real y endpoint de descarga `GET /api/v1/files/download/{filename}`. Cobertura JaCoCo 85.71% (líneas), umbral 60% cumplido.

### Added

- Jenkins image now ships `kubectl` v1.31.2, `kustomize` v5.4.3 and `k3d` v5.9.0 so the pipeline drives the on-premise cluster directly.
- On-premise playbook `infra/onpremise/README.md` (host → cg-tools Compose → k3d `cg` → verify) plus `infra/onpremise/verify-onpremise.sh`, a read-only health check for the on-premise platform.
- Traefik Ingress now routes `/api/v1/gate` → gateway (`8087`).
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
- Kustomize overlays for `dev`, `stage`, and `prod` on the on-premise k3d cluster, sharing
  one base and differing only in namespace, Ingress host, and image tag.
- `HorizontalPodAutoscaler` manifests for gateway, web, and auth, backed by `metrics-server`.
- Namespace manifests per overlay so `kubectl apply -k` is self-sufficient.
- Verified token issuance in the `dev`, `stage`, and `prod` namespaces.

### Changed

- On-prem deploy & rollback are `kustomize edit set image` (workspace-only tag) + `kubectl apply -k` + `k3d image import -c cg` + `rollout status`; the previous SSH-to-AWS path is gone from `ci.deploy`/`ci.e2e`.
- E2E (Playwright) runs against the k3d Traefik Ingress (`http://dev.circleguard.local:30080`, `E2E_HOST_RESOLVER=--host-resolver-rules=MAP ...`); Neo4j seeding uses `kubectl exec` `cypher-shell`, Kafka `survey.submitted` publish uses `kubectl exec` `kafka-console-producer.sh`, and `kafkajs` is no longer a dependency.
- Documentation moved to the on-premise platform as primary source; the AWS four-host topology (console, scripts, topology docs) is explicitly frozen as Parte 1 historical evidence.
- `Jenkinsfile` no longer expects `DEV/STAGE/PROD_PRIVATE_IP`; `tests/e2e` defaults to the ingress.
- Documentation audit: `docs/infra/tutorial-e1.2.md`, `docs/arquitectura/despliegue-auth-service.md` and `despliegue-identity-service.md` are now marked Parte 1 históricos; `docs/FILE_INDEX.md` tags `aws-free-tier-validation.md`, `ci-cd-setup.md`, `environments.md`, `evidence/README.md` and `evidence/idempotency/README.md` as Parte 1 históricos.
- `docs/agil/plan-circle-guard-5.0.md` marks the webhook and SSH risks as mitigated (live verification 2026-11-18) and adds annex §6 with the P1.4 multibranch status; the `main` build (item 8) stays pending until it is green.
- PR approval policy changed from 2 to 1 required approving review on `main` (ruleset `github-actions`), effective for Sprint 2, to cut approval latency — waiting for two reviewers was too slow and delayed PRs. The status check `continuous-integration/jenkins/pr-merge` remains mandatory, pushes dismiss stale approvals, and the approver must approve the latest revision. `BRANCHING-STRATEGY.md`, `docs/agil/README.md` and `docs/agil/backlog.md` updated; historical Parte 1 records (`docs/informe/P1.1-Branching-GitHub-Flow.md`, `docs/informe/HU-GATEWAY-04.md`, `IAG_REGISTRO.md`) annotated, not rewritten.
- SonarQube now covers promotion and identity in addition to gateway, auth, and form.
- Kafka exposes an external listener on `29092` for end-to-end tests.
- Nginx web client runs as a non-root user on port `8080`.
- `deploy/k8s/base` is now self-sufficient: Neo4j and Kafka run with
  `enableServiceLinks: false`, Kafka's controller quorum points to `localhost`, and the
  shared ConfigMap exposes `SPRING_NEO4J_URI`.
- Kubernetes manifests use the `.yml` extension and the JCasC directory is
  `deploy/compose/tools/jenkins/cac/jenkins.yml`.
- On-premise operations use `kubectl` against the local cluster; the AWS four-host compose
  topology stays as Part 1 evidence.

### Fixed

- JCasC `NoSuchFileException: /var/jenkins_home/.ssh/cg-envs-key` on boot: the dead AWS-era `cg-envs-ssh` credential was removed from JCasC.
- Notification health endpoint no longer fails when the external SMTP server is not
  configured.
- `LIBRARY_BRANCH` defaults to `main` in the tools stack.
- `auth` no longer crash-loops when `QR_SECRET` is shorter than 32 bytes.
- `identity` no longer rejects a non-hex `VAULT_SALT`.
- `promotion` reaches Neo4j through the shared ConfigMap instead of an imperative
  `kubectl set env`.
- `postgres` survives its first `initdb`: a `startupProbe` stops the liveness probe from
  killing it before it creates the five databases and enables remote access.
- `neo4j` and `kafka` start on the first attempt in a fresh namespace.

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
