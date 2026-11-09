#!/usr/bin/env bash
# CG-DOC: Idempotently creates the identity SonarQube project and assigns the coverage gate.
set -euo pipefail

SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_URL="${SONAR_URL%/}"
PROJECT_KEY="${SONAR_PROJECT_KEY:-circleguard-identity-service}"
PROJECT_NAME="${SONAR_PROJECT_NAME:-CircleGuard Identity}"
GATE_NAME="${SONAR_GATE_NAME:-CircleGuard Services}"
MIN_COVERAGE="${SONAR_MIN_LINE_COVERAGE:-60}"

if ! command -v curl >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  echo "Se requieren curl y jq." >&2
  exit 1
fi

if [[ -n "${SONAR_ADMIN_TOKEN:-}" ]]; then
  AUTH=(-u "${SONAR_ADMIN_TOKEN}:")
elif [[ -n "${SONAR_ADMIN_PASSWORD:-}" ]]; then
  AUTH=(-u "admin:${SONAR_ADMIN_PASSWORD}")
else
  echo "Define SONAR_ADMIN_TOKEN o SONAR_ADMIN_PASSWORD." >&2
  exit 1
fi

api() {
  curl -fsS "${AUTH[@]}" "$@"
}

if ! api "${SONAR_URL}/api/authentication/validate" | jq -e '.valid' >/dev/null; then
  echo "Credenciales de SonarQube inválidas para ${SONAR_URL}." >&2
  exit 1
fi

if ! api "${SONAR_URL}/api/projects/search?projects=${PROJECT_KEY}" \
    | jq -e --arg key "${PROJECT_KEY}" '.components[]? | select(.key == $key)' >/dev/null; then
  api -X POST "${SONAR_URL}/api/projects/create" \
    --data-urlencode "name=${PROJECT_NAME}" \
    --data-urlencode "project=${PROJECT_KEY}" >/dev/null
  echo "Proyecto creado: ${PROJECT_KEY}"
else
  echo "Proyecto existente: ${PROJECT_KEY}"
fi

gate_id="$(api "${SONAR_URL}/api/qualitygates/list" \
  | jq -r --arg name "${GATE_NAME}" '.qualitygates[]? | select(.name == $name) | .id' \
  | head -n1)"
if [[ -z "${gate_id}" || "${gate_id}" == "null" ]]; then
  gate_id="$(api -X POST "${SONAR_URL}/api/qualitygates/create" \
    --data-urlencode "name=${GATE_NAME}" | jq -r '.id // empty')"
  [[ -n "${gate_id}" ]] || { echo "No se pudo crear el gate ${GATE_NAME}." >&2; exit 1; }
  echo "Gate creado: ${GATE_NAME} (${gate_id})"
else
  echo "Gate existente: ${GATE_NAME} (${gate_id})"
fi

if ! api "${SONAR_URL}/api/qualitygates/show?id=${gate_id}" \
    | jq -e --argjson min "${MIN_COVERAGE}" \
      '.conditions[]? | select(.metric == "line_coverage" and .op == "LT" and ((.error | tonumber) == $min))' >/dev/null; then
  for condition_id in $(api "${SONAR_URL}/api/qualitygates/show?id=${gate_id}" \
      | jq -r '.conditions[]? | select(.metric == "line_coverage") | .id'); do
    api -X POST "${SONAR_URL}/api/qualitygates/delete_condition" \
      --data-urlencode "id=${condition_id}" >/dev/null || true
  done
  api -X POST "${SONAR_URL}/api/qualitygates/create_condition" \
    --data-urlencode "gateId=${gate_id}" \
    --data-urlencode "metric=line_coverage" \
    --data-urlencode "op=LT" \
    --data-urlencode "error=${MIN_COVERAGE}" >/dev/null
  echo "Condición aplicada: line_coverage < ${MIN_COVERAGE} %"
else
  echo "Condición existente: line_coverage < ${MIN_COVERAGE} %"
fi

api -X POST "${SONAR_URL}/api/qualitygates/select" \
  --data-urlencode "gateId=${gate_id}" \
  --data-urlencode "projectKey=${PROJECT_KEY}" >/dev/null
echo "Gate ${GATE_NAME} asignado a ${PROJECT_KEY}."
