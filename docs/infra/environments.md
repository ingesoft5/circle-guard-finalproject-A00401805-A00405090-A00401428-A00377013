<!-- CG-DOC: Isolated dev, stage, and prod gateway environment topology and commands. -->
# Dev, Stage, and Prod Environments

## Topology

```text
cg-tools (t3.large)
├── Jenkins        :8080
├── SonarQube      :9000
├── PostgreSQL     :5432 (internal)
└── Nexus          :8081 / 8082 (circleguard-docker)

cg-envs (t3.medium)
├── dev   → gateway :8087 + Redis
├── stage → gateway :8088 + Redis
└── prod  → gateway :8089 + Redis
```

## Isolation

Each environment has:

- its own `deploy/compose/<env>/docker-compose.yml`;
- its own Docker network (`dev-net`, `stage-net`, `prod-net`);
- its own Redis volume;
- its own `.env` file;
- its own host port;
- its own `QR_SECRET` and `JWT_SECRET`.

CI/CD tools are shared. Deployment environments are isolated. Middleware services (PostgreSQL, Neo4j, Kafka, OpenLDAP) are outside this user story.

## Commands

```bash
# Deploy
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/deploy.sh dev 1.0.0-abc1234

# Rollback
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod 1.0.0-previous

# Seed state for a test
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/seed-status.sh dev <anonymousId> ACTIVE

# Smoke test
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/smoke-test.sh dev 8087 <anonymousId>
```

## Isolation verification

```bash
docker network ls | grep -E 'dev-net|stage-net|prod-net'
docker volume ls | grep -E 'redis_dev|redis_stage|redis_prod'
docker compose -p circleguard-dev ps
docker compose -p circleguard-stage ps
docker compose -p circleguard-prod ps
```

## Functional dependency

The gateway validates QR tokens against `user:status:{anonymousId}` in Redis. A complete end-to-end flow requires `auth-service` to issue the QR token and `promotion-service` to populate Redis. If they are not deployed, use `seed-status.sh` and a token signed with the same `QR_SECRET`.
