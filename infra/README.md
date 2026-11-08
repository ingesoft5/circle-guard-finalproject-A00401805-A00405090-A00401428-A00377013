<!-- CG-DOC: Infrastructure bootstrap entry point, AWS topology, and operational notes. -->
# Infrastructure

## Purpose

The `infra/` directory provisions and destroys the AWS platform used by the CircleGuard gateway lifecycle:

- `cg-tools`: Jenkins, SonarQube, PostgreSQL, and Nexus.
- `cg-envs`: isolated `dev`, `stage`, and `prod` gateway stacks with Redis.

## Scripts

| Script | Responsibility |
|---|---|
| `bootstrap-common.sh` | Shared idempotent helpers for logging, Docker, secrets, SSH, Compose, and AWS state. |
| `bootstrap-check.sh` | Validates AWS identity, tools, and instance types without creating resources. |
| `bootstrap-aws.sh` | Creates or reconciles EC2 instances, security groups, elastic IPs, and the key pair. |
| `bootstrap-tools.sh` | Installs Docker and starts Jenkins, SonarQube, Nexus, and the Docker registry. |
| `bootstrap-envs.sh` | Creates isolated environment files and authenticates Docker against Nexus. |
| `bootstrap-all.sh` | Runs AWS, tools, and environment bootstrap in order. |
| `bootstrap-start.sh` | Starts the provisioned instances after a cost-saving stop. |
| `bootstrap-stop.sh` | Stops the provisioned instances to reduce compute cost. |
| `bootstrap-destroy.sh` | Terminates instances and removes AWS resources created by the bootstrap. |

## Instance sizing

- `TOOLS_TYPE` defaults to `m7i-flex.xlarge` (4 vCPU, 16 GB) for Jenkins, SonarQube, Nexus, and PostgreSQL.
- `ENVS_TYPE` defaults to `t3.medium` (2 vCPU, 4 GB) for the isolated gateway environments.
- Override either value before running `bootstrap-aws.sh` when a different budget is required.
- Free Tier only covers `t3.micro` and `t2.micro`, which are not sufficient for the CI/CD tools.

## Prerequisites

- AWS CLI v2 with EC2 and SSM permissions.
- `ADMIN_CIDR` in CIDR form.
- `GITHUB_USER`, `GITHUB_TOKEN`, `GITHUB_OWNER`, and `GITHUB_REPO`.
- Optional `TEACHER_CIDR` for classroom access.

## Usage

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
export GITHUB_USER=...
export GITHUB_TOKEN=...
./infra/bootstrap-all.sh
```

Partial runs:

```bash
./infra/bootstrap-all.sh --tools-only
./infra/bootstrap-all.sh --envs-only
```

Destroy:

```bash
CONFIRM=yes ./infra/bootstrap-destroy.sh
```

## State and secrets

- `infra/state/aws.env` stores instance IDs, public IPs, security group IDs, and the key path.
- `infra/state/tools.env` stores generated tool credentials copied from `cg-tools`.
- `infra/state/registry.env` stores Nexus credentials for `cg-envs`.
- All state files are ignored by Git and must never be committed.

## Idempotency

Each script describes resources before creating them, preserves existing secret files, merges Docker registry configuration, and waits for service health. Re-running `bootstrap-all.sh` must converge without duplicating AWS resources or rotating secrets.
