#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_state
# shellcheck disable=SC1090
source "${STATE_DIR}/tools.env"

REGISTRY="${TOOLS_IP}:8082"

for host in "${TOOLS_IP}" "${ENVS_IP}"; do
  ssh_host "${host}" "echo '{\"insecure-registries\":[\"${REGISTRY}\"]}' | sudo tee /etc/docker/daemon.json >/dev/null && sudo systemctl restart docker"
done

scp_host "${ROOT_DIR}/deploy/compose" "/opt/circleguard/" "${ENVS_IP}"

for env_name in dev stage prod; do
  qr_secret="$(head -c 32 /dev/urandom | base64 | tr -d '/+=' | head -c 32)"
  jwt_secret="$(head -c 32 /dev/urandom | base64 | tr -d '/+=' | head -c 32)"
  ssh_host "${ENVS_IP}" "cat > /opt/circleguard/compose/${env_name}/.env <<'EOF'
TAG=1.0.0-bootstrap
NEXUS_REGISTRY=${REGISTRY}
QR_SECRET=${qr_secret}
JWT_SECRET=${jwt_secret}
EOF
chmod 600 /opt/circleguard/compose/${env_name}/.env"
  ssh_host "${ENVS_IP}" "echo '${NEXUS_PASSWORD}' | docker login '${REGISTRY}' -u '${NEXUS_USER}' --password-stdin >/dev/null"
done

log "Ambientes creados en ${ENVS_IP}: /opt/circleguard/compose/{dev,stage,prod}"
