#!/usr/bin/env bash
# bootstrap-envs.sh - Prepara dev, stage y prod (gateway + Redis) de forma aislada.
# Se ejecuta en la EC2 de ambientes. Idempotente: conserva secretos existentes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

COMPOSE_ROOT="${COMPOSE_ROOT:-/opt/circleguard/deploy/compose}"
REGISTRY_ENV="${REGISTRY_ENV:-/opt/circleguard/registry.env}"
REGISTRY_REPO="circleguard-docker"

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
  log_info "Ambiente ${env_name}: ${dir}/.env listo."
}

main() {
  ensure_docker
  [[ -n "${NEXUS_REGISTRY:-}" ]] || die "Falta NEXUS_REGISTRY (host:puerto del registro)"
  ensure_insecure_registry "${NEXUS_REGISTRY}"

  if [ -f "${REGISTRY_ENV}" ]; then
    # shellcheck disable=SC1090
    set -a; source "${REGISTRY_ENV}"; set +a
    if [ -n "${NEXUS_USER:-}" ] && [ -n "${NEXUS_PASSWORD:-}" ]; then
      echo "${NEXUS_PASSWORD}" | dk login "${NEXUS_REGISTRY}" \
        -u "${NEXUS_USER}" --password-stdin >/dev/null
      # El pipeline se conecta como ubuntu: deja también sus credenciales Docker.
      sudo mkdir -p /home/ubuntu/.docker
      sudo cp /root/.docker/config.json /home/ubuntu/.docker/config.json 2>/dev/null || true
      sudo chown -R ubuntu:ubuntu /home/ubuntu/.docker
      log_info "Login en ${NEXUS_REGISTRY} correcto."
    fi
  else
    log_warn "No existe ${REGISTRY_ENV}; el pipeline deberá autenticarse en el registro."
  fi

  local env_name
  for env_name in dev stage prod; do
    bootstrap_env "${env_name}"
  done
  log_info "Ambientes preparados: dev stage prod"
}

main "$@"
