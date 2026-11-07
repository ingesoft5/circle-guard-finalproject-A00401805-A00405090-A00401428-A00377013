# Ambientes dev, stage y prod

## Topología

```text
cg-tools (t3.large)
├── Jenkins        :8080
├── SonarQube      :9000
├── PostgreSQL     :5432 (interno)
└── Nexus          :8081 / 8082

cg-envs (t3.medium)
├── dev   → gateway :8087 + Redis
├── stage → gateway :8088 + Redis
└── prod  → gateway :8089 + Redis
```

## Aislamiento

Cada ambiente tiene:

- `deploy/compose/<env>/docker-compose.yml`;
- red Docker propia (`dev-net`, `stage-net`, `prod-net`);
- volumen propio de Redis;
- archivo `.env` propio;
- puerto propio;
- credenciales `QR_SECRET` y `JWT_SECRET` propias.

Las herramientas CI/CD son compartidas; los ambientes de despliegue están aislados.

## Comandos

```bash
# Desplegar
COMPOSE_ROOT=/opt/circleguard/compose ./deploy/scripts/deploy.sh dev 1.0.0-abc1234

# Rollback
COMPOSE_ROOT=/opt/circleguard/compose ./deploy/scripts/rollback.sh prod 1.0.0-anterior

# Sembrar estado para una prueba
COMPOSE_ROOT=/opt/circleguard/compose ./deploy/scripts/seed-status.sh dev <anonymousId> ACTIVE

# Smoke test
COMPOSE_ROOT=/opt/circleguard/compose ./deploy/scripts/smoke-test.sh dev 8087 <anonymousId>
```

## Verificación de aislamiento

```bash
docker network ls | grep -E 'dev-net|stage-net|prod-net'
docker volume ls | grep -E 'redis_dev|redis_stage|redis_prod'
docker compose -p circleguard-dev ps
docker compose -p circleguard-stage ps
docker compose -p circleguard-prod ps
```

## Dependencia funcional

Gateway valida el QR contra `user:status:{anonymousId}` en Redis. Para el flujo E2E completo se requieren `auth-service` (emite el QR) y `promotion-service` (puebla Redis). Si no están desplegados, usar `seed-status.sh` y un token firmado con el mismo `QR_SECRET`.
