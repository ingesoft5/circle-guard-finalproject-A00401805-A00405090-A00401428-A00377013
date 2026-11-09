<!-- CG-DOC: Operations runbook for four hosts, private deployment, and cost controls. -->
# Operations Runbook

## Verify

```bash
source infra/state/aws.env
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
curl -f "http://${TOOLS_IP}/actuator/health"
```

## IPs

```bash
./infra/bootstrap/bootstrap-ips.sh
```

## Stop and start

```bash
./infra/bootstrap/bootstrap-stop.sh
./infra/bootstrap/bootstrap-start.sh
```

Private IPs remain stable. Environment public IPs change after a restart.

## Restart tools

```bash
ssh -i infra/state/circleguard_e12_key.pem ubuntu@${TOOLS_IP}
cd /opt/circleguard/deploy/compose/tools
docker compose --env-file .env -p circleguard-tools restart jenkins
```

## Restart one environment

```bash
ssh -i infra/state/circleguard_e12_key.pem ubuntu@${DEV_PUBLIC_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose restart gateway
```

## Refresh the deploy scripts on a host

The pipeline runs `deploy/scripts/*.sh` from `/opt/circleguard` on the target host,
so a change to those scripts only takes effect after they are copied over:

```bash
source infra/state/aws.env
tar -C . -czf - infra deploy | ssh -i infra/state/circleguard_e12_key.pem \
  ubuntu@${DEV_PUBLIC_IP} "tar -xzf - -C /opt/circleguard"
```

## Inspect the promotion stack

```bash
ssh -i infra/state/circleguard_e12_key.pem ubuntu@${DEV_PUBLIC_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose ps
docker compose logs --tail=100 promotion
curl -fsS http://localhost:8088/actuator/health
curl -fsS http://localhost:8088/api/v1/health-status/stats
```

## Seed a visitor

`promotion-service` updates visitors by `anonymousId` and never creates them, so a
new `anonymousId` needs the graph node first. The end-to-end suite does this before
each run:

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/seed-visitor.sh dev e2e-123-symptoms
```

## Rollback

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <previous-tag>
```

The rollback redeploys the previous tag over the whole stack. Middleware volumes are
untouched, so the promotion database and graph survive the rollback; only the two
application images change.

## Destroy

```bash
CONFIRM=yes ./infra/bootstrap/bootstrap-destroy.sh
```

## Common issues

| Symptom | Cause | Action |
|---|---|---|
| Jenkins does not open | Container starting or low memory | Check logs and swap. |
| SonarQube stays starting | PostgreSQL or memory | Check logs; increase swap. |
| Nexus does not open | Port 8081 or memory | Check logs. |
| Registry pull fails | Trusted HTTP registry | Verify `insecure-registries` on the environment host. |
| Gateway 401 | Missing staff JWT | Send `Authorization: Bearer <jwt>`. |
| Gateway 503 | Redis unavailable | Check `docker compose logs redis`. |
| Promotion never starts, `WeakKeyException` | `JWT_SECRET` shorter than 32 bytes | Regenerate it with `openssl rand -hex 24` in `.env` and redeploy. |
| Neo4j restarts, `Unrecognized setting ... PASSWORD` | An extra `NEO4J_*` variable reached the container | Keep the password inside `NEO4J_AUTH`; Neo4j rejects unknown settings. |
| Questionnaire never changes a status | The Kafka consumer rejects every record | Keep `spring.json.value.default.type` in `application.yml`. |
| Status never changes for a new visitor | The `anonymousId` has no node in Neo4j | Run `deploy/scripts/seed-visitor.sh` first. |
| `deploy.sh` fails at `--wait` | A container never reports healthy | `docker compose ps` and `docker compose logs <service>`. |
| Pipeline library fails | Wrong `LIBRARY_BRANCH` | Review `deploy/compose/tools/.env`. |
