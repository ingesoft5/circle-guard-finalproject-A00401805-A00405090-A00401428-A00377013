#!/usr/bin/env bash
# CG-DOC: Runs health and authorization smoke checks against an environment.
set -euo pipefail

ENV="${1:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
PORT="${2:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
ANONYMOUS_ID="${3:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
STATUS="${4:-ACTIVE}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"${SCRIPT_DIR}/seed-status.sh" "${ENV}" "${ANONYMOUS_ID}" "${STATUS}"

echo "Health:"
curl -fsS "http://localhost:${PORT}/actuator/health"

echo
echo "Validación sin token (debe ser 401):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:${PORT}/api/v1/gate/validate" \
  -H 'Content-Type: application/json' -d '{"token":"invalid"}'
