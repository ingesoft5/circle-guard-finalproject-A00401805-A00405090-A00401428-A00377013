#!/usr/bin/env bash
# CG-DOC: Prepares one isolated environment host (dev, stage, or prod).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

ENV_NAME="${1:-}"
COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/deploy/compose}"
REGISTRY_ENV="${REGISTRY_ENV:-/opt/circleguard/registry.env}"
NEXUS_REGISTRY="${NEXUS_REGISTRY:-${REGISTRY_HOST:-}}"

[[ "${ENV_NAME}" =~ ^(dev|stage|prod)$ ]] || die "Uso: bootstrap-envs.sh <dev|stage|prod>"

bootstrap_env() {
  local env_name="$1" dir="${COMPOSE_ROOT}/$1"
  ensure_dir "${dir}" 1000 1000
  ensure_env_file "${dir}/.env" <<EOF
TAG=1.0.0-bootstrap
NEXUS_REGISTRY=${NEXUS_REGISTRY}
QR_SECRET=$(rand_secret)
JWT_SECRET=$(rand_secret)
EOF
  ensure_env_kv "${dir}/.env" TAG "1.0.0-bootstrap"
  set_env_kv "${dir}/.env" NEXUS_REGISTRY "${NEXUS_REGISTRY}"
  ensure_env_kv "${dir}/.env" QR_SECRET "$(rand_secret)"
  ensure_env_kv "${dir}/.env" JWT_SECRET "$(rand_secret)"
  log_info "Environment ${env_name} ready: ${dir}/.env"
}

main() {
  ensure_docker
  [[ -n "${NEXUS_REGISTRY}" ]] || die "Falta REGISTRY_HOST o NEXUS_REGISTRY"
  ensure_insecure_registry "${NEXUS_REGISTRY}"
  if [ -f "${REGISTRY_ENV}" ]; then
    set -a; source "${REGISTRY_ENV}"; set +a
    if [ -n "${NEXUS_USER:-}" ] && [ -n "${NEXUS_PASSWORD:-}" ]; then
      echo "${NEXUS_PASSWORD}" | dk login "${NEXUS_REGISTRY}" -u "${NEXUS_USER}" --password-stdin >/dev/null
      sudo mkdir -p /home/ubuntu/.docker
      sudo cp /root/.docker/config.json /home/ubuntu/.docker/config.json 2>/dev/null || true
      sudo chown -R ubuntu:ubuntu /home/ubuntu/.docker
    fi
  fi
  bootstrap_env "${ENV_NAME}"
}

main "$@"
