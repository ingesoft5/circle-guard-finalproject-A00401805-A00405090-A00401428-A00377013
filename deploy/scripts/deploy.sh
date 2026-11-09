#!/usr/bin/env bash
# CG-DOC: Deploys gateway, identity, and auth service image tags to an isolated environment and waits for health.
set -euo pipefail

ENV="${1:?Uso: deploy.sh <dev|stage|prod> <tag>}"
TAG="${2:?Uso: deploy.sh <dev|stage|prod> <tag>}"
COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/deploy/compose}"
DIR="${COMPOSE_ROOT}/${ENV}"

[[ -f "${DIR}/docker-compose.yml" ]] || { echo "No existe ${DIR}/docker-compose.yml"; exit 1; }
[[ -f "${DIR}/.env" ]] || { echo "Falta ${DIR}/.env"; exit 1; }

cd "${DIR}"
export TAG
docker compose pull gateway identity auth
docker compose up -d --remove-orphans

check_health() {
  local name="$1"
  local port="$2"
  local path="${3:-/actuator/health}"
  for _ in $(seq 1 40); do
    if curl -fsS "http://localhost:${port}${path}" | grep -q UP; then
      echo "Health OK (${name}): ${ENV} tag=${TAG} puerto=${port}"
      return 0
    fi
    sleep 3
  done
  echo "Healthcheck falló (${name}) para ${ENV} tag=${TAG} puerto=${port}"
  docker compose logs --tail=80 "${name}" || true
  return 1
}

check_health "gateway" "8087" "/actuator/health"
check_health "identity" "8083" "/actuator/health/liveness"
check_health "auth" "8180" "/actuator/health"

echo "Despliegue OK: ${ENV} tag=${TAG} servicios=gateway(8087),identity(8083),auth(8180)"
