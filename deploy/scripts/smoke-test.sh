#!/usr/bin/env bash
# CG-DOC: Runs health and authorization smoke checks against an environment (gateway + auth).
set -euo pipefail

ENV="${1:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
PORT="${2:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
ANONYMOUS_ID="${3:?Uso: smoke-test.sh <dev|stage|prod> <puerto> <anonymousId> [status]}"
STATUS="${4:-ACTIVE}"
AUTH_PORT="${AUTH_PORT:-8180}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"${SCRIPT_DIR}/seed-status.sh" "${ENV}" "${ANONYMOUS_ID}" "${STATUS}"

echo "Gateway Health (:${PORT}):"
curl -fsS "http://localhost:${PORT}/actuator/health"
echo

echo "Auth Health (:${AUTH_PORT}):"
curl -fsS "http://localhost:${AUTH_PORT}/actuator/health"
echo

echo "Validación sin token en Gateway (debe ser 401):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:${PORT}/api/v1/gate/validate" \
  -H 'Content-Type: application/json' -d '{"token":"invalid"}'

echo "Login con credenciales inválidas en Auth (debe ser 401):"
curl -s -o /dev/null -w "%{http_code}\n" -X POST "http://localhost:${AUTH_PORT}/api/v1/auth/login" \
  -H 'Content-Type: application/json' -d '{"username":"staff_guard","password":"wrong-password"}'
