#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_state
# shellcheck disable=SC1090
source "${STATE_DIR}/tools.env"

SONAR_URL="http://${TOOLS_IP}:9000"
NEXUS_URL_BASE="http://${TOOLS_IP}:8081"
NEXUS_API="${NEXUS_URL_BASE}/service/rest/v1"
SONAR_ADMIN_PASSWORD="${SONAR_ADMIN_PASSWORD:-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')}"
NEXUS_NEW_PASSWORD="${NEXUS_PASSWORD}"

log "Configurando SonarQube"
for _ in $(seq 1 30); do
  curl -fsS "${SONAR_URL}/api/system/status" | grep -q '"status":"UP"' && break
  sleep 5
done
curl -fsS -u "admin:${SONAR_ADMIN_PASSWORD}" "${SONAR_URL}/api/system/status" >/dev/null 2>&1 || \
  curl -fsS -u "admin:admin" -X POST "${SONAR_URL}/api/users/change_password" \
    --data-urlencode "login=admin" --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=${SONAR_ADMIN_PASSWORD}" >/dev/null
SONAR_AUTH="admin:${SONAR_ADMIN_PASSWORD}"
curl -fsS -u "${SONAR_AUTH}" -X POST "${SONAR_URL}/api/projects/create" \
  --data-urlencode "name=CircleGuard Gateway" --data-urlencode "project=circleguard-gateway-service" >/dev/null 2>&1 || true
SONAR_TOKEN="$(curl -fsS -u "${SONAR_AUTH}" -X POST "${SONAR_URL}/api/user_tokens/generate" \
  --data-urlencode "name=jenkins-$(date +%s)" | jq -r '.token // empty')"
[[ -n "${SONAR_TOKEN}" ]] || SONAR_TOKEN="${SONAR_TOKEN:-change-me}"
curl -fsS -u "${SONAR_AUTH}" -X POST "${SONAR_URL}/api/webhooks/create" \
  --data-urlencode "name=Jenkins" --data-urlencode "url=http://${TOOLS_IP}:8080/sonarqube-webhook/" >/dev/null 2>&1 || true

log "Configurando Nexus"
for _ in $(seq 1 30); do
  curl -fsS "${NEXUS_API}/status" >/dev/null 2>&1 && break
  sleep 5
done
OLD_PASSWORD="$(ssh_host "${TOOLS_IP}" "docker exec cg-nexus cat /nexus-data/admin.password 2>/dev/null || true" | tr -d '\r')"
if [[ -n "${OLD_PASSWORD}" ]]; then
  curl -fsS -u "admin:${OLD_PASSWORD}" -X PUT -H 'Content-Type: application/json' \
    -d "{\"password\":\"${NEXUS_NEW_PASSWORD}\"}" \
    "${NEXUS_API}/security/users/admin/change-password" >/dev/null || true
fi
NEXUS_AUTH="admin:${NEXUS_NEW_PASSWORD}"

curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d '{
  "name":"maven-releases","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow_once"},
  "maven":{"versionPolicy":"RELEASE","layoutPolicy":"STRICT","contentDisposition":"ATTACHMENT"}}' \
  "${NEXUS_API}/repositories/maven/hosted" >/dev/null 2>&1 || true
curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d '{
  "name":"maven-snapshots","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow"},
  "maven":{"versionPolicy":"SNAPSHOT","layoutPolicy":"STRICT","contentDisposition":"ATTACHMENT"}}' \
  "${NEXUS_API}/repositories/maven/hosted" >/dev/null 2>&1 || true
curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d '{
  "name":"raw","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow"},
  "raw":{"contentDisposition":"ATTACHMENT"}}' \
  "${NEXUS_API}/repositories/raw/hosted" >/dev/null 2>&1 || true
curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d '{
  "name":"docker-hosted","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"allow"},
  "docker":{"httpPort":8082,"forceBasicAuth":true,"v1Enabled":false}}' \
  "${NEXUS_API}/repositories/docker/hosted" >/dev/null 2>&1 || true
curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d '{
  "id":"jenkins-publisher","name":"jenkins-publisher","description":"Jenkins publisher",
  "privileges":["nx-repository-view-*-*-add","nx-repository-view-*-*-edit","nx-repository-view-*-*-read"],"roles":[]}' \
  "${NEXUS_API}/security/roles" >/dev/null 2>&1 || true
curl -fsS -u "${NEXUS_AUTH}" -X POST -H 'Content-Type: application/json' -d "{
  \"userId\":\"${NEXUS_USER}\",\"firstName\":\"Jenkins\",\"lastName\":\"CI\",\"emailAddress\":\"jenkins@circleguard.local\",
  \"source\":\"default\",\"status\":\"active\",\"roles\":[\"jenkins-publisher\"],\"password\":\"${NEXUS_NEW_PASSWORD}\"}" \
  "${NEXUS_API}/security/users" >/dev/null 2>&1 || true

cat > "${STATE_DIR}/tools.env" <<EOF
JENKINS_ADMIN_PASSWORD=${JENKINS_ADMIN_PASSWORD}
GITHUB_USER=${GITHUB_USER}
GITHUB_TOKEN=${GITHUB_TOKEN}
GITHUB_OWNER=${GITHUB_OWNER}
GITHUB_REPO=${GITHUB_REPO}
GITHUB_REPO_URL=${GITHUB_REPO_URL}
SONAR_TOKEN=${SONAR_TOKEN}
SONAR_ADMIN_PASSWORD=${SONAR_ADMIN_PASSWORD}
NEXUS_USER=${NEXUS_USER}
NEXUS_PASSWORD=${NEXUS_NEW_PASSWORD}
NEXUS_URL=http://nexus:8081/repository/maven-releases/
NEXUS_REGISTRY=${TOOLS_IP}:8082
SONAR_HOST_URL=http://sonarqube:9000
ENVS_IP=${ENVS_IP}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL:-}
EOF
chmod 600 "${STATE_DIR}/tools.env"
scp_host "${STATE_DIR}/tools.env" "/opt/circleguard/tools/.env" "${TOOLS_IP}"

ssh_host "${TOOLS_IP}" "cd /opt/circleguard/tools && docker compose up -d --force-recreate jenkins"
log "Configuración terminada. Credenciales en ${STATE_DIR}/tools.env"
