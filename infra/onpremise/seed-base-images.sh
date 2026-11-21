#!/usr/bin/env bash
# CG-DOC: Seed the base images used by the Dockerfiles into the on-premise Nexus
# registry. Docker Hub's CDN is unreachable over IPv6 on the shared Jenkins host,
# so the builds take base images from ${NEXUS_REGISTRY}/circleguard-base instead.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${SEED_ENV:-${ROOT_DIR}/deploy/compose/tools/.env.onpremise}"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
else
  echo "No se encontró $ENV_FILE; usa SEED_ENV=/ruta/a/.env.onpremise" >&2
  exit 1
fi

NEXUS_REGISTRY="${NEXUS_REGISTRY:-10.147.20.67:5000}"
NEXUS_USER="${NEXUS_USER:?NEXUS_USER no definido en $ENV_FILE}"
NEXUS_PASSWORD="${NEXUS_PASSWORD:?NEXUS_PASSWORD no definido en $ENV_FILE}"

echo "==> Login en $NEXUS_REGISTRY"
echo "$NEXUS_PASSWORD" | docker login "$NEXUS_REGISTRY" -u "$NEXUS_USER" --password-stdin

BASE_IMAGES=(
  "gradle:8.14-jdk21"
  "eclipse-temurin:21-jdk"
  "eclipse-temurin:21-jre-alpine"
  "eclipse-temurin:21-jre-jammy"
  "node:22-alpine"
  "nginxinc/nginx-unprivileged:1.27-alpine"
)

for image in "${BASE_IMAGES[@]}"; do
  name="${image%%:*}"
  tag="${image##*:}"
  target="${NEXUS_REGISTRY}/circleguard-base/${name}:${tag}"
  echo "==> ${image} -> ${target}"
  docker pull "$image"
  docker tag "$image" "$target"
  docker push "$target"
done

echo "==> Repositorios circleguard-base en el registro"
curl -fsS "http://${NEXUS_REGISTRY}/v2/_catalog" \
  | jq -r '.repositories[]' | grep '^circleguard-base/' | sort
