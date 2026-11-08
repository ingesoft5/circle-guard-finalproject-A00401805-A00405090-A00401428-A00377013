<!-- CG-DOC: Operational runbook for platform checks, restarts, rollback, and troubleshooting. -->
# Operations Runbook

## Verify the platform

```bash
source infra/state/aws.env
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
curl -f "http://${ENVS_IP}:8087/actuator/health"
curl -f "http://${ENVS_IP}:8088/actuator/health"
curl -f "http://${ENVS_IP}:8089/actuator/health"
```

## Re-run the idempotent bootstrap

```bash
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
./infra/bootstrap-all.sh
```

## Restart tools

```bash
ssh -i infra/state/circleguard-key.pem ubuntu@${TOOLS_IP}
cd /opt/circleguard/deploy/compose/tools
docker compose --env-file .env -p circleguard-tools restart jenkins
docker compose --env-file .env -p circleguard-tools logs -f jenkins
```

## Restart an environment

```bash
ssh -i infra/state/circleguard-key.pem ubuntu@${ENVS_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose restart gateway
docker compose logs -f gateway
```

## Stop and start instances

```bash
./infra/bootstrap-stop.sh
./infra/bootstrap-start.sh
```

Stopping removes compute charges. EBS volumes and Elastic IP addresses remain billable.

## Rollback

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <previous-tag>
```

## Destroy everything

```bash
CONFIRM=yes ./infra/bootstrap-destroy.sh
```

## Common issues

| Symptom | Likely cause | Action |
|---|---|---|
| Jenkins does not open | Container starting or low memory | Check `docker compose logs jenkins`; review swap. |
| SonarQube stays in `STARTING` | PostgreSQL or memory | Check `docker compose logs sonarqube`; increase swap. |
| Nexus does not open | Port 8081 or memory | Check logs; memory limit is 2 GB. |
| Gateway cannot pull the image | Untrusted HTTP registry | Verify `insecure-registries` on `cg-envs`. |
| Gateway returns 401 | Missing staff JWT | Send `Authorization: Bearer <jwt>`. |
| Gateway returns 403 | JWT lacks `gate:scan` | Assign the permission in `auth-service`. |
| Gateway returns 503 | Redis unavailable | Check `docker compose logs redis`. |
| Pipeline fails at `@Library` | Wrong library branch | Review `LIBRARY_BRANCH` in `deploy/compose/tools/.env`. |
