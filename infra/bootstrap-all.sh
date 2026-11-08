#!/usr/bin/env bash
# CG-DOC: Orchestrates AWS provisioning, tool setup, and environment preparation.
# bootstrap-all.sh - Reconstruye la plataforma CI/CD del gateway desde cero.
# Uso: ./infra/bootstrap-all.sh [--tools-only | --envs-only]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

TOOLS_ENV_LOCAL="${REPO_ROOT}/deploy/compose/tools/.env"
REGISTRY_PORT="8082"
MODE="${1:-all}"

prepare_tools_env() {
  mkdir -p "$(dirname "${TOOLS_ENV_LOCAL}")"
  ensure_env_file "${TOOLS_ENV_LOCAL}" <<EOF
JENKINS_ADMIN_PASSWORD=$(rand_secret)
GITHUB_USER=${GITHUB_USER:-change-me}
GITHUB_TOKEN=${GITHUB_TOKEN:-change-me}
GITHUB_OWNER=${GITHUB_OWNER:-ingesoft5}
GITHUB_REPO=${GITHUB_REPO:-circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013}
GITHUB_REPO_URL=${GITHUB_REPO_URL:-https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git}
SONAR_DB_PASSWORD=$(rand_secret)
SONAR_ADMIN_PASSWORD=$(rand_secret)
SONAR_TOKEN=change-me
NEXUS_USER=jenkins
NEXUS_PASSWORD=$(rand_secret)
NEXUS_URL=http://nexus:8081
NEXUS_REGISTRY=${TOOLS_IP}:${REGISTRY_PORT}
SONAR_HOST_URL=http://sonarqube:9000
ENVS_IP=${ENVS_IP}
LIBRARY_BRANCH=dev
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL:-}
EOF
  if [ -n "${GITHUB_USER:-}" ]; then set_env_kv "${TOOLS_ENV_LOCAL}" GITHUB_USER "${GITHUB_USER}"; fi
  if [ -n "${GITHUB_TOKEN:-}" ]; then set_env_kv "${TOOLS_ENV_LOCAL}" GITHUB_TOKEN "${GITHUB_TOKEN}"; fi
  if [ -n "${GITHUB_OWNER:-}" ]; then set_env_kv "${TOOLS_ENV_LOCAL}" GITHUB_OWNER "${GITHUB_OWNER}"; fi
  if [ -n "${GITHUB_REPO:-}" ]; then set_env_kv "${TOOLS_ENV_LOCAL}" GITHUB_REPO "${GITHUB_REPO}"; fi
  if [ -n "${GITHUB_REPO_URL:-}" ]; then set_env_kv "${TOOLS_ENV_LOCAL}" GITHUB_REPO_URL "${GITHUB_REPO_URL}"; fi
  set_env_kv "${TOOLS_ENV_LOCAL}" NEXUS_REGISTRY "${TOOLS_IP}:${REGISTRY_PORT}"
  set_env_kv "${TOOLS_ENV_LOCAL}" ENVS_IP "${ENVS_IP}"
}

bootstrap_tools() {
  log_info "Sincronizando herramientas en ${TOOLS_IP}"
  sync_repo "${TOOLS_IP}"
  scp_to "${KEY_PATH}" "/opt/circleguard/cg-envs-key" "${TOOLS_IP}"
  ssh_host "${TOOLS_IP}" "chmod 600 /opt/circleguard/cg-envs-key"
  scp_to "${TOOLS_ENV_LOCAL}" "/opt/circleguard/deploy/compose/tools/.env" "${TOOLS_IP}"
  ssh_host "${TOOLS_IP}" "cd /opt/circleguard && \
    TOOLS_IP='${TOOLS_IP}' ENVS_IP='${ENVS_IP}' \
    NEXUS_REGISTRY='${TOOLS_IP}:${REGISTRY_PORT}' \
    CG_ENVS_KEY_PATH=/opt/circleguard/cg-envs-key \
    bash infra/bootstrap-tools.sh"

  scp_from "/opt/circleguard/deploy/compose/tools/.env" "${STATE_DIR}/tools.env" "${TOOLS_IP}"
  chmod 600 "${STATE_DIR}/tools.env"
}

bootstrap_envs() {
  log_info "Sincronizando ambientes en ${ENVS_IP}"
  sync_repo "${ENVS_IP}"
  {
    echo "NEXUS_USER=$(get_env_kv "${STATE_DIR}/tools.env" NEXUS_USER)"
    echo "NEXUS_PASSWORD=$(get_env_kv "${STATE_DIR}/tools.env" NEXUS_PASSWORD)"
  } > "${STATE_DIR}/registry.env"
  chmod 600 "${STATE_DIR}/registry.env"
  scp_to "${STATE_DIR}/registry.env" "/opt/circleguard/registry.env" "${ENVS_IP}"
  ssh_host "${ENVS_IP}" "cd /opt/circleguard && \
    COMPOSE_ROOT=/opt/circleguard/deploy/compose \
    NEXUS_REGISTRY='${TOOLS_IP}:${REGISTRY_PORT}' \
    REGISTRY_ENV=/opt/circleguard/registry.env \
    bash infra/bootstrap-envs.sh"
}

case "${MODE}" in
  all)
    "${BOOTSTRAP_DIR}/bootstrap-aws.sh"
    load_state
    prepare_tools_env
    bootstrap_tools
    bootstrap_envs
    ;;
  --tools-only)
    load_state
    prepare_tools_env
    bootstrap_tools
    ;;
  --envs-only)
    load_state
    bootstrap_envs
    ;;
  *)
    die "Uso: $0 [--tools-only | --envs-only]"
    ;;
esac

log_info "Plataforma lista. Credenciales: ${STATE_DIR}/tools.env"
echo "Ejecuta el pipeline de Jenkins para construir y desplegar el gateway."
