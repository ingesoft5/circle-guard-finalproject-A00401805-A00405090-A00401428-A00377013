#!/usr/bin/env bash
# CG-DOC: Rolls back an environment to a previous semantic image tag for gateway and auth.
set -euo pipefail

ENV="${1:?Uso: rollback.sh <dev|stage|prod> <tag-anterior>}"
PREVIOUS_TAG="${2:?Uso: rollback.sh <dev|stage|prod> <tag-anterior>}"

echo "Rollback de ${ENV} a ${PREVIOUS_TAG} (servicios: gateway, auth)"
exec "$(dirname "$0")/deploy.sh" "${ENV}" "${PREVIOUS_TAG}"
