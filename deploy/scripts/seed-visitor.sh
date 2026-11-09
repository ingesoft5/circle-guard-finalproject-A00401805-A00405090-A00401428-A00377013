#!/usr/bin/env bash
# CG-DOC: Seeds Neo4j with the visitors an end-to-end run is going to promote.
# promotion-service matches visitors by anonymousId and never creates them: the node
# has to exist before the questionnaire event is published, or the status cascade
# finds nothing to update and the run fails for the wrong reason.
set -euo pipefail

ENV="${1:?Uso: seed-visitor.sh <dev|stage|prod> <anonymousId>...}"
shift
[[ "$#" -gt 0 ]] || { echo "Uso: seed-visitor.sh <dev|stage|prod> <anonymousId>..."; exit 1; }

COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/deploy/compose}"
DIR="${COMPOSE_ROOT}/${ENV}"
[[ -f "${DIR}/docker-compose.yml" ]] || { echo "No existe ${DIR}/docker-compose.yml"; exit 1; }
[[ -f "${DIR}/.env" ]] || { echo "Falta ${DIR}/.env"; exit 1; }

cd "${DIR}"
set -a; source .env; set +a

for ANONYMOUS_ID in "$@"; do
  [[ "${ANONYMOUS_ID}" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "anonymousId inválido: ${ANONYMOUS_ID}"; exit 1; }
  docker compose exec -T neo4j \
    cypher-shell -a bolt://localhost:7687 -u neo4j -p "${NEO4J_PASSWORD}" \
    "MERGE (u:User {anonymousId: '${ANONYMOUS_ID}'}) SET u.status = 'ACTIVE', u.statusUpdatedAt = timestamp()" \
    >/dev/null
  echo "Visitante sembrado: ${ANONYMOUS_ID} (ACTIVE)"
done
