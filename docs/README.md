<!-- CG-DOC: Documentation index for architecture, infrastructure, standards, and evidence. -->
# Documentation

## Purpose

This directory contains the written documentation for CircleGuard.

## Index

| Path | Content |
|---|---|
| `FILE_INDEX.md` | English purpose of every tracked repository file. |
| `standards/documentation.md` | Documentation language, headers, and per-language rules. |
| `infra/aws-free-tier-validation.md` | [Histórico · AWS] AWS resource sizing and cost checks. |
| `infra/ci-cd-setup.md` | [Histórico · Parte 1] Jenkins, SonarQube, Nexus, credentials, and pipeline setup. |
| `infra/environments.md` | [Histórico · Parte 1] Isolated `dev`, `stage`, and `prod` topology. |
| `infra/runbook.md` | Operational checks, restarts, rollback, and troubleshooting (on-prem path). |
| `infra/rollback.md` | Production rollback procedure, Jenkins `ROLLBACK_TAG`, and local tag-swap check. |
| `infra/evidencia/E2.1-terraform.md` | Terraform modules, remote state, validation, and on-prem apply record. |
| `infra/evidencia/E2.4-E2.5-seguridad.md` | Jaeger/Trivy smoke results, TLS/RBAC render checks, and pending on-prem evidence. |
| `../infra/onpremise/README.md` | On-premise platform guide (Compose cg-tools + k3d `cg`). |
| `../infra/onpremise/verify-onpremise.sh` | Read-only health check of the on-premise platform. |
| `infra/evidence/` | Required CI/CD evidence checklist. |
| `informe/E1.2-CircleGuard-Infraestructura.md` | E1.2 report with Markdown screenshots. |
| `informe/Gateway-Cobertura.md` | Gateway unit, integration, E2E, JaCoCo, and quality report. |
| `arquitectura/diagnostico-auth-service.md` | Auth service architecture diagnosis. |
| `agil/README.md` | Agile artifacts index and working agreements. |
| `agil/backlog.md` | Product backlog with acceptance criteria and sprint status. |
| `agil/sprint-0/` | Sprint 0 planning and retrospective. |
| `agil/sprint-1/` | Sprint 1 planning, review, and retrospective. |
| `agil/sprint-2/` | Sprint 2 planning, review, retrospective, and closure of P2 deliverables. |
| `informe/evidencia/E2.6-locust.md` | Locust suite status, targets, and pending stage execution record. |
| `change-management.md` | Change request, approval, release, and rollback policy. |
| `release-notes/README.md` | Release note template and semver rules. |
| `arquitectura/README.md` | Container architecture and isolated environments. |
| `arquitectura/pipeline.md` | CI/CD pipeline architecture and promotion flow. |
| `arquitectura/despliegue-auth-service.md` | Auth service deployment, E2E validation, and rollback record. |
| `arquitectura/despliegue-identity-service.md` | Identity service deployment, anonymization validation, and rollback record. |
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
