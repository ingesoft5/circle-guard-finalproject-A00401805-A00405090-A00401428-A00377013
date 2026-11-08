#!/usr/bin/env bash
# CG-DOC: Orchestrates four-host provisioning, tools setup, and per-environment preparation.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

TOOLS_ENV_LOCAL="${REPO_ROOT}/deploy/compose/tools/.env"
REGISTRY_PORT="5000"
MODE="${1:-all}"

prepare_tools_env() {
  ensure_env_file "${TOOLS_ENV_LOCAL}" <<EOF
JENKINS_ADMIN_PASSWORD=$(rand_secret)
PROFESSOR_PASSWORD=$(rand_secret)
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
NEXUS_REGISTRY=${TOOLS_PRIVATE_IP}:${REGISTRY_PORT}
TOOLS_IP=${TOOLS_IP}
TOOLS_PRIVATE_IP=${TOOLS_PRIVATE_IP}
DEV_PRIVATE_IP=${DEV_PRIVATE_IP}
STAGE_PRIVATE_IP=${STAGE_PRIVATE_IP}
PROD_PRIVATE_IP=${PROD_PRIVATE_IP}
SONAR_HOST_URL=http://sonarqube:9000
JENKINS_URL=http://${TOOLS_IP}:8080/
LIBRARY_BRANCH=${LIBRARY_BRANCH:-dev}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL:-}
EOF
  for key in GITHUB_USER GITHUB_TOKEN GITHUB_OWNER GITHUB_REPO GITHUB_REPO_URL LIBRARY_BRANCH SLACK_WEBHOOK_URL; do
    val="${!key:-}"
    [ -n "${val}" ] && set_env_kv "${TOOLS_ENV_LOCAL}" "${key}" "${val}"
  done
  set_env_kv "${TOOLS_ENV_LOCAL}" NEXUS_REGISTRY "${TOOLS_PRIVATE_IP}:${REGISTRY_PORT}"
  set_env_kv "${TOOLS_ENV_LOCAL}" TOOLS_IP "${TOOLS_IP}"
  set_env_kv "${TOOLS_ENV_LOCAL}" TOOLS_PRIVATE_IP "${TOOLS_PRIVATE_IP}"
  set_env_kv "${TOOLS_ENV_LOCAL}" DEV_PRIVATE_IP "${DEV_PRIVATE_IP}"
  set_env_kv "${TOOLS_ENV_LOCAL}" STAGE_PRIVATE_IP "${STAGE_PRIVATE_IP}"
  set_env_kv "${TOOLS_ENV_LOCAL}" PROD_PRIVATE_IP "${PROD_PRIVATE_IP}"
  set_env_kv "${TOOLS_ENV_LOCAL}" JENKINS_URL "http://${TOOLS_IP}:8080/"
}

bootstrap_tools() {
  local env_args=(
    "TOOLS_IP=${TOOLS_IP}"
    "TOOLS_PRIVATE_IP=${TOOLS_PRIVATE_IP}"
    "DEV_PRIVATE_IP=${DEV_PRIVATE_IP}"
    "STAGE_PRIVATE_IP=${STAGE_PRIVATE_IP}"
    "PROD_PRIVATE_IP=${PROD_PRIVATE_IP}"
    "NEXUS_REGISTRY=${TOOLS_PRIVATE_IP}:${REGISTRY_PORT}"
    "LIBRARY_BRANCH=${LIBRARY_BRANCH:-dev}"
  )
  if hostname -I | tr ' ' '\n' | grep -qx "${TOOLS_PRIVATE_IP}"; then
    env "${env_args[@]}" CG_ENVS_KEY_PATH=/opt/circleguard/cg-envs-key bash "${BOOTSTRAP_DIR}/bootstrap-tools.sh"
    cp "${TOOLS_ENV_LOCAL}" "${STATE_DIR}/tools.env"
  else
    sync_repo "${TOOLS_IP}"
    scp_to "${KEY_PATH}" "/opt/circleguard/cg-envs-key" "${TOOLS_IP}"
    ssh_host "${TOOLS_IP}" "chmod 600 /opt/circleguard/cg-envs-key"
    scp_to "${TOOLS_ENV_LOCAL}" "/opt/circleguard/deploy/compose/tools/.env" "${TOOLS_IP}"
    ssh_host "${TOOLS_IP}" "cd /opt/circleguard && env ${env_args[*]} CG_ENVS_KEY_PATH=/opt/circleguard/cg-envs-key bash infra/bootstrap/bootstrap-tools.sh"
    scp_from "/opt/circleguard/deploy/compose/tools/.env" "${STATE_DIR}/tools.env" "${TOOLS_IP}"
  fi
  chmod 600 "${STATE_DIR}/tools.env"
}

bootstrap_env() {
  local env_name="$1" ip="$2"
  log_info "Synchronizing ${env_name} on ${ip}"
  sync_repo "${ip}"
  {
    echo "NEXUS_USER=$(get_env_kv "${STATE_DIR}/tools.env" NEXUS_USER)"
    echo "NEXUS_PASSWORD=$(get_env_kv "${STATE_DIR}/tools.env" NEXUS_PASSWORD)"
  } > "${STATE_DIR}/registry.env"
  chmod 600 "${STATE_DIR}/registry.env"
  scp_to "${STATE_DIR}/registry.env" "/opt/circleguard/registry.env" "${ip}"
  ssh_host "${ip}" "cd /opt/circleguard && \
    COMPOSE_ROOT=/opt/circleguard/deploy/compose \
    REGISTRY_HOST='${TOOLS_PRIVATE_IP}:${REGISTRY_PORT}' \
    REGISTRY_ENV=/opt/circleguard/registry.env \
    bash infra/bootstrap/bootstrap-envs.sh ${env_name}"
}

case "${MODE}" in
  all)
    "${BOOTSTRAP_DIR}/bootstrap-aws.sh"
    load_state
    prepare_tools_env
    bootstrap_tools
    bootstrap_env dev "${DEV_PUBLIC_IP}"
    bootstrap_env stage "${STAGE_PUBLIC_IP}"
    bootstrap_env prod "${PROD_PUBLIC_IP}"
    ;;
  --tools-only)
    load_state
    prepare_tools_env
    bootstrap_tools
    ;;
  --envs-only)
    load_state
    bootstrap_env dev "${DEV_PUBLIC_IP}"
    bootstrap_env stage "${STAGE_PUBLIC_IP}"
    bootstrap_env prod "${PROD_PUBLIC_IP}"
    ;;
  *) die "Uso: $0 [--tools-only | --envs-only]" ;;
esac

log_info "Platform ready. Credentials: ${STATE_DIR}/tools.env"
