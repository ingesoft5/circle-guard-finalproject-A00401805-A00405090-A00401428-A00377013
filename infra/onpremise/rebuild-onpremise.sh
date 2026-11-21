#!/usr/bin/env bash
# Idempotent one-command rebuild of the on-premise tools platform.
# Run from the repository root on the on-premise host as user `docker`.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

set -a
. deploy/compose/tools/.env.onpremise
set +a

: "${SONAR_HOST_URL:?SONAR_HOST_URL no definido en .env.onpremise}"
: "${SONAR_TOKEN:?SONAR_TOKEN no definido en .env.onpremise}"

SONAR_URL="${SONAR_URL:-$SONAR_HOST_URL}"
ADMIN_TOKEN="${SONAR_ADMIN_TOKEN:-$SONAR_TOKEN}"

curl -fsS -u "$ADMIN_TOKEN:" \
  "$SONAR_URL/api/authentication/validate" \
  | jq -e '.valid == true' >/dev/null

bash infra/onpremise/bootstrap-onpremise.sh

mapfile -t KEYS < <(grep -oP "projectKey:'\K[^']+" jenkins/vars/ciServices.groovy | sort -u)
if (( ${#KEYS[@]} == 0 )); then
  echo "No se encontraron projectKey en jenkins/vars/ciServices.groovy" >&2
  exit 1
fi

for key in "${KEYS[@]}"; do
  env SONAR_PROJECT_KEY="$key" \
      SONAR_PROJECT_NAME="$key" \
      SONAR_URL="$SONAR_URL" \
      SONAR_ADMIN_TOKEN="$ADMIN_TOKEN" \
      SONAR_MIN_LINE_COVERAGE=60 \
      bash infra/bootstrap/configure-sonar-identity.sh
done

docker compose -f deploy/compose/tools/docker-compose.onpremise.yml config -q
docker compose -f deploy/compose/tools/docker-compose.onpremise.yml up -d --remove-orphans

echo "Rebuild on-premise convergente completado: ${#KEYS[@]} proyectos SonarQube verificados."
