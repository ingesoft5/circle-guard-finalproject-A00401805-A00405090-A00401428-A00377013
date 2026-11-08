#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_state

TOOLS_ENV="${ROOT_DIR}/deploy/compose/tools/.env"
if [[ ! -f "${TOOLS_ENV}" ]]; then
  rand() { head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24; }
  cat > "${TOOLS_ENV}" <<EOF
JENKINS_ADMIN_PASSWORD=${JENKINS_ADMIN_PASSWORD:-$(rand)}
GITHUB_USER=${GITHUB_USER:-change-me}
GITHUB_TOKEN=${GITHUB_TOKEN:-change-me}
GITHUB_OWNER=${GITHUB_OWNER:-ingesoft5}
GITHUB_REPO=${GITHUB_REPO:-circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013}
GITHUB_REPO_URL=${GITHUB_REPO_URL:-https://github.com/ingesoft5/circle-guard-finalproject-A00401805-A00405090-A00401428-A00377013.git}
SONAR_DB_PASSWORD=${SONAR_DB_PASSWORD:-$(rand)}
SONAR_TOKEN=${SONAR_TOKEN:-change-me}
NEXUS_USER=${NEXUS_USER:-jenkins}
NEXUS_PASSWORD=${NEXUS_PASSWORD:-$(rand)}
NEXUS_URL=${NEXUS_URL:-http://nexus:8081/repository/maven-releases/}
NEXUS_REGISTRY=${NEXUS_REGISTRY:-${TOOLS_IP}:8082}
SONAR_HOST_URL=http://sonarqube:9000
ENVS_IP=${ENVS_IP}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL:-}
EOF
  chmod 600 "${TOOLS_ENV}"
fi

scp_host "${ROOT_DIR}/deploy/compose/tools" "/opt/circleguard/" "${TOOLS_IP}"
ssh_host "${TOOLS_IP}" "cd /opt/circleguard/tools && docker compose up -d --build"

log "Esperando servicios..."
for _ in $(seq 1 40); do
  if curl -fsS "http://${TOOLS_IP}:9000/api/system/status" >/dev/null 2>&1; then break; fi
  sleep 10
done
curl -fsS "http://${TOOLS_IP}:8080/login" >/dev/null || { echo "Jenkins no responde"; exit 1; }
curl -fsS "http://${TOOLS_IP}:9000/api/system/status" >/dev/null || { echo "SonarQube no responde"; exit 1; }
curl -fsS "http://${TOOLS_IP}:8081/service/rest/v1/status" >/dev/null || { echo "Nexus no responde"; exit 1; }

cp "${TOOLS_ENV}" "${STATE_DIR}/tools.env"
chmod 600 "${STATE_DIR}/tools.env"
cat <<EOF
Herramientas levantadas:
  Jenkins   http://${TOOLS_IP}:8080
  SonarQube http://${TOOLS_IP}:9000
  Nexus     http://${TOOLS_IP}:8081
  Docker    ${TOOLS_IP}:8082
Credenciales: ${STATE_DIR}/tools.env
EOF
