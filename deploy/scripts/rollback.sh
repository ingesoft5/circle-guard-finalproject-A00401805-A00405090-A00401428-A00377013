#!/usr/bin/env bash
set -euo pipefail

ENV="${1:?Uso: rollback.sh <dev|stage|prod> <tag-anterior>}"
PREVIOUS_TAG="${2:?Uso: rollback.sh <dev|stage|prod> <tag-anterior>}"

echo "Rollback de ${ENV} a ${PREVIOUS_TAG}"
exec "$(dirname "$0")/deploy.sh" "${ENV}" "${PREVIOUS_TAG}"
