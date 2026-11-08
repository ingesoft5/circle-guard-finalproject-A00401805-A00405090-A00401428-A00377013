#!/usr/bin/env bash
# CG-DOC: Provisions Jenkins, SonarQube, Nexus, the private registry, and the ingress on cg-tools.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

TOOLS_COMPOSE="${REPO_ROOT}/deploy/compose/tools/docker-compose.yml"
TOOLS_ENV="${REPO_ROOT}/deploy/compose/tools/.env"
REGISTRY_REPO="circleguard-docker"
REGISTRY_PORT="5000"

ensure_secret() {
  local f="$1" k="$2" current
  current="$(get_env_kv "$f" "$k")"
  if [ -z "$current" ] || [ "$current" = "change-me" ]; then
    set_env_kv "$f" "$k" "$(rand_secret)"
  fi
}

ensure_tools_env() {
  ensure_env_file "${TOOLS_ENV}" <<EOF
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
NEXUS_REGISTRY=${NEXUS_REGISTRY}
TOOLS_IP=${TOOLS_IP}
TOOLS_PRIVATE_IP=${TOOLS_PRIVATE_IP}
DEV_PRIVATE_IP=${DEV_PRIVATE_IP:-}
STAGE_PRIVATE_IP=${STAGE_PRIVATE_IP:-}
PROD_PRIVATE_IP=${PROD_PRIVATE_IP:-}
SONAR_HOST_URL=http://sonarqube:9000
JENKINS_URL=http://${TOOLS_IP}:8080/
LIBRARY_BRANCH=${LIBRARY_BRANCH:-main}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL:-}
EOF
  ensure_secret "${TOOLS_ENV}" JENKINS_ADMIN_PASSWORD
  ensure_secret "${TOOLS_ENV}" PROFESSOR_PASSWORD
  ensure_secret "${TOOLS_ENV}" SONAR_DB_PASSWORD
  ensure_secret "${TOOLS_ENV}" SONAR_ADMIN_PASSWORD
  ensure_secret "${TOOLS_ENV}" NEXUS_PASSWORD
  ensure_env_kv "${TOOLS_ENV}" GITHUB_USER "${GITHUB_USER:-change-me}"
  ensure_env_kv "${TOOLS_ENV}" GITHUB_TOKEN "${GITHUB_TOKEN:-change-me}"
  ensure_env_kv "${TOOLS_ENV}" GITHUB_OWNER "${GITHUB_OWNER:-ingesoft5}"
  ensure_env_kv "${TOOLS_ENV}" GITHUB_REPO "${GITHUB_REPO:-circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013}"
  ensure_env_kv "${TOOLS_ENV}" GITHUB_REPO_URL "${GITHUB_REPO_URL:-https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git}"
  ensure_env_kv "${TOOLS_ENV}" SONAR_TOKEN "change-me"
  ensure_env_kv "${TOOLS_ENV}" NEXUS_USER "jenkins"
  set_env_kv "${TOOLS_ENV}" NEXUS_URL "http://nexus:8081"
  set_env_kv "${TOOLS_ENV}" NEXUS_REGISTRY "${NEXUS_REGISTRY}"
  set_env_kv "${TOOLS_ENV}" TOOLS_IP "${TOOLS_IP}"
  set_env_kv "${TOOLS_ENV}" TOOLS_PRIVATE_IP "${TOOLS_PRIVATE_IP}"
  set_env_kv "${TOOLS_ENV}" DEV_PRIVATE_IP "${DEV_PRIVATE_IP:-}"
  set_env_kv "${TOOLS_ENV}" STAGE_PRIVATE_IP "${STAGE_PRIVATE_IP:-}"
  set_env_kv "${TOOLS_ENV}" PROD_PRIVATE_IP "${PROD_PRIVATE_IP:-}"
  set_env_kv "${TOOLS_ENV}" JENKINS_URL "http://${TOOLS_IP}:8080/"
  ensure_env_kv "${TOOLS_ENV}" SONAR_HOST_URL "http://sonarqube:9000"
  ensure_env_kv "${TOOLS_ENV}" LIBRARY_BRANCH "main"
  ensure_env_kv "${TOOLS_ENV}" SLACK_WEBHOOK_URL ""
  set -a; source "${TOOLS_ENV}"; set +a
}

configure_sonar() {
  local base=http://localhost:9000 auth token
  wait_http "${base}/api/system/status" 600 '"status":"UP"'
  if curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" "${base}/api/authentication/validate" | jq -e '.valid' >/dev/null; then
    log_info "SonarQube: admin already configured."
  elif curl -fsS -u admin:admin "${base}/api/authentication/validate" | jq -e '.valid' >/dev/null; then
    curl -fsS -u admin:admin -X POST "${base}/api/users/change_password" \
      --data-urlencode login=admin --data-urlencode previousPassword=admin \
      --data-urlencode "password=${SONAR_ADMIN_PASSWORD}" >/dev/null
  else
    die "SonarQube authentication failed; review SONAR_ADMIN_PASSWORD."
  fi
  auth="admin:${SONAR_ADMIN_PASSWORD}"
  curl -fsS -u "${auth}" -X POST "${base}/api/projects/create" \
    --data-urlencode "name=CircleGuard Gateway" \
    --data-urlencode "project=circleguard-gateway-service" >/dev/null 2>&1 || true
  token="${SONAR_TOKEN:-}"
  if [ -z "${token}" ] || [ "${token}" = "change-me" ] || \
     ! curl -fsS -u "${token}:" "${base}/api/authentication/validate" | jq -e '.valid' >/dev/null; then
    token="$(curl -fsS -u "${auth}" -X POST "${base}/api/user_tokens/generate" \
      --data-urlencode "name=jenkins-$(date +%s)" | jq -r '.token // empty')"
    [ -n "${token}" ] || die "SonarQube token generation failed."
    set_env_kv "${TOOLS_ENV}" SONAR_TOKEN "${token}"
    SONAR_TOKEN="${token}"
  fi
  if ! curl -fsS -u "${auth}" "${base}/api/webhooks/list" \
      | jq -e --arg u "http://jenkins:8080/sonarqube-webhook/" \
        '.webhooks[]? | select(.url == $u)' >/dev/null; then
    curl -fsS -u "${auth}" -X POST "${base}/api/webhooks/create" \
      --data-urlencode "name=Jenkins" \
      --data-urlencode "url=http://jenkins:8080/sonarqube-webhook/" >/dev/null || true
  fi
}

configure_nexus() {
  local base=http://localhost:8081 auth old_password role_json user_json
  wait_http "${base}/service/rest/v1/status" 600
  old_password="$(dk exec cg-nexus cat /nexus-data/admin.password 2>/dev/null || true)"
  if [ -n "${old_password}" ]; then
    curl -fsS -u "admin:${old_password}" -X PUT -H 'Content-Type: text/plain' \
      --data "${NEXUS_PASSWORD}" "${base}/service/rest/v1/security/users/admin/change-password" >/dev/null
  fi
  auth="admin:${NEXUS_PASSWORD}"
  curl -fsS -u "${auth}" -X PUT -H 'Content-Type: application/json' \
    -d '["NexusAuthenticatingRealm","DockerToken"]' \
    "${base}/service/rest/v1/security/realms/active" >/dev/null
  if ! curl -fsS -u "${auth}" -o /dev/null "${base}/service/rest/v1/repositories/${REGISTRY_REPO}" 2>/dev/null; then
    curl -fsS -u "${auth}" -X POST -H 'Content-Type: application/json' \
      -d "{\"name\":\"${REGISTRY_REPO}\",\"online\":true,
           \"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":true,\"writePolicy\":\"allow\"},
           \"docker\":{\"httpPort\":${REGISTRY_PORT},\"forceBasicAuth\":true,\"v1Enabled\":false}}" \
      "${base}/service/rest/v1/repositories/docker/hosted" >/dev/null
  fi
  role_json="{\"id\":\"jenkins-publisher\",\"name\":\"jenkins-publisher\",
    \"description\":\"Jenkins publisher\",
    \"privileges\":[\"nx-repository-view-*-*-add\",\"nx-repository-view-*-*-edit\",\"nx-repository-view-*-*-read\",\"nx-repository-view-*-*-browse\"],
    \"roles\":[]}"
  if curl -fsS -u "${auth}" -o /dev/null "${base}/service/rest/v1/security/roles/jenkins-publisher" 2>/dev/null; then
    curl -fsS -u "${auth}" -X PUT -H 'Content-Type: application/json' -d "${role_json}" \
      "${base}/service/rest/v1/security/roles/jenkins-publisher" >/dev/null || true
  else
    curl -fsS -u "${auth}" -X POST -H 'Content-Type: application/json' -d "${role_json}" \
      "${base}/service/rest/v1/security/roles" >/dev/null || true
  fi
  user_json="{\"userId\":\"${NEXUS_USER}\",\"firstName\":\"Jenkins\",\"lastName\":\"CI\",
    \"emailAddress\":\"jenkins@circleguard.local\",\"source\":\"default\",\"status\":\"active\",
    \"roles\":[\"jenkins-publisher\"],\"password\":\"${NEXUS_PASSWORD}\"}"
  if curl -fsS -u "${auth}" -o /dev/null "${base}/service/rest/v1/security/users/${NEXUS_USER}" 2>/dev/null; then
    curl -fsS -u "${auth}" -X PUT -H 'Content-Type: application/json' -d "${user_json}" \
      "${base}/service/rest/v1/security/users/${NEXUS_USER}" >/dev/null || true
  else
    curl -fsS -u "${auth}" -X POST -H 'Content-Type: application/json' -d "${user_json}" \
      "${base}/service/rest/v1/security/users" >/dev/null || true
  fi
}

main() {
  TOOLS_PRIVATE_IP="${TOOLS_PRIVATE_IP:-$(hostname -I | awk '{print $1}')}"
  TOOLS_IP="${TOOLS_IP:-$(host_ip)}"
  NEXUS_REGISTRY="${NEXUS_REGISTRY:-${TOOLS_PRIVATE_IP}:${REGISTRY_PORT}}"
  export TOOLS_IP TOOLS_PRIVATE_IP NEXUS_REGISTRY

  ensure_docker
  ensure_insecure_registry "${NEXUS_REGISTRY}"
  ensure_sysctl vm.max_map_count 262144
  ensure_sysctl fs.file-max 65536
  ensure_swap 4
  ensure_dir "${TOOLS_HOME}"
  ensure_dir "${TOOLS_HOME}/jenkins_ssh" 1000 1000
  if [ -n "${CG_ENVS_KEY_PATH:-}" ] && [ -f "${CG_ENVS_KEY_PATH}" ]; then
    sudo install -m 600 -o 1000 -g 1000 "${CG_ENVS_KEY_PATH}" "${TOOLS_HOME}/jenkins_ssh/cg-envs-key"
  fi

  ensure_tools_env
  local before after
  before="$(sha256sum "${TOOLS_ENV}" | cut -d' ' -f1)"
  compose_up "${TOOLS_COMPOSE}" circleguard-tools "${TOOLS_ENV}"
  configure_sonar
  configure_nexus
  cp "${TOOLS_ENV}" "${TOOLS_ENV}.tmp"
  after="$(sha256sum "${TOOLS_ENV}.tmp" | cut -d' ' -f1)"
  rm -f "${TOOLS_ENV}.tmp"
  if [ "${before}" != "${after}" ]; then
    dk compose --env-file "${TOOLS_ENV}" -f "${TOOLS_COMPOSE}" -p circleguard-tools \
      up -d --force-recreate --wait --wait-timeout 600 jenkins
  fi
  log_info "Tools ready: http://${TOOLS_IP}:8080  registry ${NEXUS_REGISTRY}"
}

main "$@"
