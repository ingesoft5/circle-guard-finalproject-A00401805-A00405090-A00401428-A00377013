<!-- CG-DOC: Deployment layout for CI/CD tools, isolated gateway environments, and operational scripts. -->
# Deployment

## Purpose

The `deploy/` directory defines how the platform runs:

- `compose/tools/`: Jenkins, SonarQube, PostgreSQL, and Nexus on `cg-tools`.
- `compose/dev/`: gateway and Redis for development.
- `compose/stage/`: gateway and Redis for staging.
- `compose/prod/`: gateway and Redis for production.
- `scripts/`: deploy, rollback, seed, and smoke-test operations.

## Environment isolation

Each environment has its own Compose project, Docker network, Redis volume, `.env` file, host port, and QR/JWT secrets:

| Environment | Host port | Network | Redis volume |
|---|---:|---|---|
| `dev` | 8087 | `dev-net` | `redis_dev` |
| `stage` | 8087 | `stage-net` | `redis_stage` |
| `prod` | 8087 | `prod-net` | `redis_prod` |

## Gateway image

The pipeline publishes and deploys:

```text
<TOOLS_PRIVATE_IP>:5000/circleguard-docker/gateway:<TAG>
```

Nexus hosts the `circleguard-docker` repository on port `5000`.

## Scripts

| Script | Responsibility |
|---|---|
| `deploy.sh` | Pulls the requested tag, recreates the gateway, and waits for `/actuator/health`. |
| `rollback.sh` | Reuses `deploy.sh` with a previous tag. |
| `seed-status.sh` | Writes `user:status:<anonymousId>` into the environment Redis. |
| `smoke-test.sh` | Checks health and the unauthenticated validation response. |

## Required environment variables

- `TAG`: gateway image tag.
- `NEXUS_REGISTRY`: `<TOOLS_PRIVATE_IP>:5000`.
- `QR_SECRET` and `JWT_SECRET`: per-environment secrets.

## Jenkins interface

The pipeline calls:

```bash
ssh ubuntu@<ENV_PRIVATE_IP> \
  'cd /opt/circleguard && \
   COMPOSE_ROOT=/opt/circleguard/deploy/compose \
   TAG=<TAG> ./deploy/scripts/deploy.sh <environment> <TAG>'
```

The `cg-envs-ssh` credential is declared in JCasC and uses the key mounted at `/var/jenkins_home/.ssh/cg-envs-key`.
