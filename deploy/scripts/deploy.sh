#!/usr/bin/env bash
set -euo pipefail

ENV="${1:?Uso: deploy.sh <dev|stage|prod> <tag>}"
TAG="${2:?Uso: deploy.sh <dev|stage|prod> <tag>}"
COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/compose}"
DIR="${COMPOSE_ROOT}/${ENV}"

[[ -f "${DIR}/docker-compose.yml" ]] || { echo "No existe ${DIR}/docker-compose.yml"; exit 1; }
[[ -f "${DIR}/.env" ]] || { echo "Falta ${DIR}/.env"; exit 1; }

cd "${DIR}"
export TAG
docker compose pull gateway
docker compose up -d --remove-orphans

PORT="$(grep -E '^[[:space:]]*-[[:space:]]*"[0-9]+:8087"' docker-compose.yml | head -n1 | grep -oE '[0-9]+' | head -n1)"
PORT="${PORT:-8087}"

for _ in $(seq 1 30); do
  if curl -fsS "http://localhost:${PORT}/actuator/health" | grep -q UP; then
    echo "Despliegue OK: ${ENV} tag=${TAG} puerto=${PORT}"
    exit 0
  fi
  sleep 3
done

echo "Healthcheck falló para ${ENV} tag=${TAG}"
docker compose logs --tail=80 gateway
exit 1
