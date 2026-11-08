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
ssh -i infra/state/circleguard-e12-key.pem ubuntu@${TOOLS_IP}
cd /opt/circleguard/deploy/compose/tools
docker compose --env-file .env -p circleguard-tools restart jenkins
```

## Restart one environment

```bash
ssh -i infra/state/circleguard-e12-key.pem ubuntu@${DEV_PUBLIC_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose restart gateway
```

## Rollback

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <previous-tag>
```

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
| Pipeline library fails | Wrong `LIBRARY_BRANCH` | Review `deploy/compose/tools/.env`. |
