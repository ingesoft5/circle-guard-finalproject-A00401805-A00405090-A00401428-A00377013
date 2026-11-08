# Runbook operativo

## Verificar plataforma

```bash
source infra/state/aws.env
curl -f "http://${TOOLS_IP}:8080/login"
curl -f "http://${TOOLS_IP}:9000/api/system/status"
curl -f "http://${TOOLS_IP}:8081/service/rest/v1/status"
curl -f "http://${ENVS_IP}:8087/actuator/health"
curl -f "http://${ENVS_IP}:8088/actuator/health"
curl -f "http://${ENVS_IP}:8089/actuator/health"
```

## Reejecutar bootstrap (idempotente)

```bash
export ADMIN_CIDR="$(curl -s https://checkip.amazonaws.com)/32"
./infra/bootstrap-all.sh
```

## Reiniciar herramientas

```bash
ssh -i infra/state/circleguard-key.pem ubuntu@${TOOLS_IP}
cd /opt/circleguard/deploy/compose/tools
docker compose --env-file .env -p circleguard-tools restart jenkins
docker compose --env-file .env -p circleguard-tools logs -f jenkins
```

## Reiniciar un ambiente

```bash
ssh -i infra/state/circleguard-key.pem ubuntu@${ENVS_IP}
cd /opt/circleguard/deploy/compose/dev
docker compose restart gateway
docker compose logs -f gateway
```

## Rollback

```bash
COMPOSE_ROOT=/opt/circleguard/deploy/compose ./deploy/scripts/rollback.sh prod <tag-anterior>
```

## Destruir todo

```bash
CONFIRM=yes ./infra/bootstrap-destroy.sh
```

## Problemas comunes

| Síntoma | Causa probable | Acción |
|---|---|---|
| Jenkins no abre | Contenedor iniciando o poca RAM | `docker compose logs jenkins`; revisar swap. |
| SonarQube no pasa de `STARTING` | PostgreSQL o memoria | Revisar `docker compose logs sonarqube`; aumentar swap. |
| Nexus no abre | Puerto 8081 o memoria | Revisar logs; `mem_limit` 2g. |
| Gateway no descarga imagen | Registro HTTP no confiable | Verificar `insecure-registries` en `cg-envs`. |
| Gateway responde 401 | Falta el JWT del empleado | Enviar `Authorization: Bearer <jwt>`. |
| Gateway responde 403 | JWT sin `gate:scan` | Asignar permiso en auth-service. |
| Gateway responde 503 | Redis no disponible | Revisar `docker compose logs redis`. |
| Pipeline falla en `@Library` | Rama de librería incorrecta | Revisar `LIBRARY_BRANCH` en `deploy/compose/tools/.env`. |
