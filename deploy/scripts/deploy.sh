#!/usr/bin/env bash
# CG-DOC: Deploys one image tag to an isolated environment and waits for the whole stack.
set -euo pipefail

ENV="${1:?Uso: deploy.sh <dev|stage|prod> <tag>}"
TAG="${2:?Uso: deploy.sh <dev|stage|prod> <tag>}"
COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/deploy/compose}"
DIR="${COMPOSE_ROOT}/${ENV}"

[[ -f "${DIR}/docker-compose.yml" ]] || { echo "No existe ${DIR}/docker-compose.yml"; exit 1; }
[[ -f "${DIR}/.env" ]] || { echo "Falta ${DIR}/.env"; exit 1; }

cd "${DIR}"
export TAG

# Only the images this repository publishes are pulled by name: their tag is what
# the pipeline changes. The public middleware images are pinned to fixed versions
# in the compose file and `up` fetches them once if they are missing, so a Docker
# Hub outage cannot block a redeploy that only ships a new application tag.
docker compose pull --quiet gateway promotion

# `--wait` blocks until every service with a healthcheck reports healthy, which is
# the same contract the old port-scanning loop enforced, but for the whole stack
# instead of the first published port it found.
docker compose up -d --remove-orphans --wait --wait-timeout 300

echo "Despliegue OK: ${ENV} tag=${TAG}"
docker compose ps --format 'table {{.Service}}\t{{.Status}}'
