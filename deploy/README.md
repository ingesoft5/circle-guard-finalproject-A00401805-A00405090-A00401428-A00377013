<!-- CG-DOC: Deployment layout for CI/CD tools, isolated core environments, and operational scripts. -->
# Deployment

## Purpose

The `deploy/` directory defines how the platform runs:

- `compose/tools/`: Jenkins, SonarQube, PostgreSQL, and Nexus on `cg-tools`.
- `compose/dev/`, `compose/stage/`, `compose/prod/`: the five core services
  (`auth`, `identity`, `promotion`, `form`, `gateway`), the web client and
  their middleware.
- `compose/postgres/init-databases.sh`: creates the per-service databases in
  the shared PostgreSQL instance.
- `scripts/`: deploy, rollback, seed, and smoke-test operations.

`notification`, `file` and `dashboard` are built, tested, packaged and
published by the pipeline, but they are not deployed in Part 1.

## Environment isolation

Each environment has its own Compose project, Docker network, volumes, `.env`
file, host ports, and QR/JWT secrets:

| Environment | Web | Gateway | Auth | Identity | Form | Promotion | Network |
|---|---:|---:|---:|---:|---:|---:|---|
| `dev` | 8081 | 8087 | 8180 | 8083 | 8086 | 8088 | `dev-net` |
| `stage` | 8081 | 8087 | 8180 | 8083 | 8086 | 8088 | `stage-net` |
| `prod` | 8081 | 8087 | 8180 | 8083 | 8086 | 8088 | `prod-net` |

## Images

The pipeline publishes semantic-versioned images:

```text
<TOOLS_PRIVATE_IP>:5000/circleguard-docker/{gateway,auth,identity,promotion,form,web}:<TAG>
```

## Scripts

| Script | Responsibility |
|---|---|
| `deploy.sh` | Pulls the requested tag, recreates the five core services and web client, and waits for every healthcheck. |
| `rollback.sh` | Reuses `deploy.sh` with a previous tag. |
| `seed-status.sh` | Writes `user:status:<anonymousId>` into the environment Redis. |
| `smoke-test.sh` | Checks health and the unauthenticated validation response. |

## Required environment variables

- `TAG`: image tag.
- `NEXUS_REGISTRY`: `<TOOLS_PRIVATE_IP>:5000`.
- `POSTGRES_PASSWORD`, `NEO4J_PASSWORD`, `OPENLDAP_ADMIN_PASSWORD`.
- `QR_SECRET`, `JWT_SECRET`, `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT`.

## Jenkins interface

The pipeline calls:

```bash
ssh ubuntu@<ENV_PRIVATE_IP> \
  'cd /opt/circleguard && \
   COMPOSE_ROOT=/opt/circleguard/deploy/compose \
   TAG=<TAG> ./deploy/scripts/deploy.sh <environment> <TAG>'
```

The `cg-envs-ssh` credential is declared in JCasC and uses the key mounted at `/var/jenkins_home/.ssh/cg-envs-key`.
