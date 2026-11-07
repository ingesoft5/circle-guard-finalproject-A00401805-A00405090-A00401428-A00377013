#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_state

bootstrap_host() {
  local ip="$1" ports="$2"
  log "Preparando ${ip}"
  ssh_host "${ip}" "PORTS='${ports}' bash -s" <<'REMOTE'
set -euo pipefail
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 openjdk-21-jdk awscli jq curl ufw || \
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose openjdk-21-jdk awscli jq curl ufw
sudo systemctl enable --now docker
if ! docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi
sudo usermod -aG docker ubuntu
sudo mkdir -p /opt/circleguard/compose /opt/circleguard/tools
if ! sudo swapon --show | grep -q swapfile && [ "$(free -m | awk '/Mem:/{print $2}')" -lt 4096 ]; then
  sudo fallocate -l 4G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=4096
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
sudo ufw allow 22/tcp >/dev/null
for p in ${PORTS}; do sudo ufw allow "${p}/tcp" >/dev/null; done
sudo ufw --force enable >/dev/null
REMOTE
}

bootstrap_host "${TOOLS_IP}" "8080 9000 8081 8082"
bootstrap_host "${ENVS_IP}" "8087 8088 8089"
log "Hosts preparados"
