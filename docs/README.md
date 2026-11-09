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
| `informe/Gateway-Cobertura.md` | Gateway unit, integration, E2E, JaCoCo, and quality report. |
| `arquitectura/diagnostico-auth-service.md` | Auth service architecture diagnosis. |
| `agil/README.md` | Agile artifacts index and working agreements. |
| `agil/backlog.md` | Product backlog with acceptance criteria and sprint status. |
| `agil/sprint-0/` | Sprint 0 planning and retrospective. |
| `agil/sprint-1/` | Sprint 1 planning, review, and retrospective. |
| `change-management.md` | Change request, approval, release, and rollback policy. |
| `release-notes/README.md` | Release note template and semver rules. |
| `arquitectura/README.md` | Container architecture and isolated environments. |
| `arquitectura/pipeline.md` | CI/CD pipeline architecture and promotion flow. |
| `informe/P1.1-Branching-GitHub-Flow.md` | Branching strategy, real branch status, and P1.1 evidence. |

## Standards

- Source comments and file headers are written in English.
- Every tracked file has a `CG-DOC` purpose header or an entry in `FILE_INDEX.md`.
- Commit messages and pull request titles follow English Conventional Commits.
- Secrets, generated state, and private keys are never documented or committed.

## Related documents

- `BRANCHING-STRATEGY.md`
- `IAG_REGISTRO.md`
- `README.md`
