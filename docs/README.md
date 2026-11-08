<!-- CG-DOC: Documentation index for architecture, infrastructure, standards, and evidence. -->
# Documentation

## Purpose

This directory contains the written documentation for CircleGuard.

## Index

| Path | Content |
|---|---|
| `FILE_INDEX.md` | English purpose of every tracked repository file. |
| `standards/documentation.md` | Documentation language, headers, and per-language rules. |
| `infra/aws-free-tier-validation.md` | AWS resource sizing and cost checks. |
| `infra/ci-cd-setup.md` | Jenkins, SonarQube, Nexus, credentials, and pipeline setup. |
| `infra/environments.md` | Isolated `dev`, `stage`, and `prod` topology. |
| `infra/runbook.md` | Operational checks, restarts, rollback, and troubleshooting. |
| `infra/evidence/` | Required CI/CD evidence checklist. |
| `informe/E1.2-CircleGuard-Infraestructura.md` | E1.2 report with Markdown screenshots. |
| `arquitectura/diagnostico-auth-service.md` | Auth service architecture diagnosis. |

## Standards

- Source comments and file headers are written in English.
- Every tracked file has a `CG-DOC` purpose header or an entry in `FILE_INDEX.md`.
- Commit messages and pull request titles follow English Conventional Commits.
- Secrets, generated state, and private keys are never documented or committed.

## Related documents

- `BRANCHING-STRATEGY.md`
- `IAG_REGISTRO.md`
- `README.md`
