#!/usr/bin/env bash
set -euo pipefail

ENV="${1:?Uso: seed-status.sh <dev|stage|prod> <anonymousId> <status>}"
ANONYMOUS_ID="${2:?Uso: seed-status.sh <dev|stage|prod> <anonymousId> <status>}"
STATUS="${3:?Uso: seed-status.sh <dev|stage|prod> <anonymousId> <status>}"
COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/compose}"

docker compose -f "${COMPOSE_ROOT}/${ENV}/docker-compose.yml" \
  exec -T redis redis-cli SET "user:status:${ANONYMOUS_ID}" "${STATUS}"
