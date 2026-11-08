#!/usr/bin/env bash
# CG-DOC: Shared idempotent helpers for logging, Docker, secrets, SSH, and Compose.
# bootstrap-common.sh - Utilidades idempotentes para aprovisionamiento y despliegue.
# Se usa con `source`; no define `set -e` para no alterar al script que lo invoca.
[ -n "${CG_COMMON_LOADED:-}" ] && return 0
CG_COMMON_LOADED=1

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC} $*" >&2; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()       { log_error "$*"; exit 1; }

BOOTSTRAP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${BOOTSTRAP_DIR}/../.." && pwd)"
STATE_DIR="${REPO_ROOT}/infra/state"
STATE_FILE="${STATE_DIR}/aws.env"
export DEBIAN_FRONTEND=noninteractive
export TOOLS_HOME="${TOOLS_HOME:-/opt/circleguard}"

mkdir -p "${STATE_DIR}"

require_cmd() {
  local cmd
  for cmd in "$@"; do
    command -v "${cmd}" >/dev/null 2>&1 || die "Falta el comando: ${cmd}"
  done
}

rand_secret() { openssl rand -hex 24; }

# Docker con o sin sudo: el grupo docker no aplica hasta una nueva sesión.
dk() { if docker info >/dev/null 2>&1; then docker "$@"; else sudo docker "$@"; fi; }

load_state() {
  [[ -f "${STATE_FILE}" ]] || die "Falta ${STATE_FILE}. Ejecuta primero infra/bootstrap/bootstrap-aws.sh"
  # shellcheck disable=SC1090
  source "${STATE_FILE}"
}

save_state() {
  {
    echo "AWS_REGION=${AWS_REGION}"
    echo "TOOLS_INSTANCE_ID=${TOOLS_INSTANCE_ID}"
    echo "DEV_INSTANCE_ID=${DEV_INSTANCE_ID}"
    echo "STAGE_INSTANCE_ID=${STAGE_INSTANCE_ID}"
    echo "PROD_INSTANCE_ID=${PROD_INSTANCE_ID}"
    echo "TOOLS_PRIVATE_IP=${TOOLS_PRIVATE_IP}"
    echo "TOOLS_IP=${TOOLS_IP}"
    echo "DEV_PRIVATE_IP=${DEV_PRIVATE_IP}"
    echo "DEV_PUBLIC_IP=${DEV_PUBLIC_IP}"
    echo "STAGE_PRIVATE_IP=${STAGE_PRIVATE_IP}"
    echo "STAGE_PUBLIC_IP=${STAGE_PUBLIC_IP}"
    echo "PROD_PRIVATE_IP=${PROD_PRIVATE_IP}"
    echo "PROD_PUBLIC_IP=${PROD_PUBLIC_IP}"
    echo "TOOLS_SG=${TOOLS_SG}"
    echo "ENVS_SG=${ENVS_SG}"
    echo "KEY_NAME=${KEY_NAME}"
    echo "KEY_PATH=${KEY_PATH}"
  } > "${STATE_FILE}"
  chmod 600 "${STATE_FILE}"
}

instance_ids() {
  printf '%s\n' "${TOOLS_INSTANCE_ID}" "${DEV_INSTANCE_ID}" "${STAGE_INSTANCE_ID}" "${PROD_INSTANCE_ID}"
}

refresh_public_ips() {
  local env_name id
  for env_name in dev stage prod; do
    id="${env_name^^}_INSTANCE_ID"
    id="${!id}"
    [ -n "${id}" ] || continue
    local public private
    read -r public private < <(aws ec2 describe-instances --instance-ids "${id}" \
      --query 'Reservations[0].Instances[0].[PublicIpAddress,PrivateIpAddress]' --output text)
    set_env_kv "${STATE_FILE}" "${env_name^^}_PUBLIC_IP" "${public:-}"
    set_env_kv "${STATE_FILE}" "${env_name^^}_PRIVATE_IP" "${private:-}"
  done
}

ssh_host() {
  local ip="$1"; shift
  ssh -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    "ubuntu@${ip}" "$@"
}

scp_to() {
  local src="$1" dst="$2" ip="$3"
  scp -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    -r "${src}" "ubuntu@${ip}:${dst}"
}

scp_from() {
  local src="$1" dst="$2" ip="$3"
  scp -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
    "ubuntu@${ip}:${src}" "${dst}"
}

# Copia infra/ y deploy/ al host remoto sin secretos locales ni artefactos de build.
sync_repo() {
  local ip="$1"
  ssh_host "${ip}" "sudo mkdir -p /opt/circleguard && sudo chown -R ubuntu:ubuntu /opt/circleguard"
  tar -C "${REPO_ROOT}" \
    --exclude='infra/state' \
    --exclude='.git' \
    --exclude='.gradle' \
    --exclude='**/build' \
    --exclude='node_modules' \
    --exclude='e12-bootstrap' \
    --exclude='deploy/compose/tools/.env' \
    --exclude='deploy/compose/dev/.env' \
    --exclude='deploy/compose/stage/.env' \
    --exclude='deploy/compose/prod/.env' \
    -czf - infra deploy | \
    ssh -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
      "ubuntu@${ip}" "tar -xzf - -C /opt/circleguard"
}

ensure_packages() {
  local missing=() p
  for p in "$@"; do dpkg -s "$p" &>/dev/null || missing+=("$p"); done
  if [ "${#missing[@]}" -gt 0 ]; then
    log_info "Instalando paquetes: ${missing[*]}"
    sudo apt-get update -y
    sudo apt-get install -y --no-install-recommends "${missing[@]}"
  fi
}

ensure_docker() {
  ensure_packages ca-certificates curl gnupg openssl jq
  if command -v docker &>/dev/null && dk compose version &>/dev/null; then
    log_info "Docker y Compose ya instalados."
  else
    log_info "Instalando Docker..."
    # shellcheck disable=SC1091
    . /etc/os-release
    sudo install -m 0755 -d /etc/apt/keyrings
    curl -fsSL "https://download.docker.com/linux/${ID}/gpg" \
      | sudo gpg --batch --yes --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/${ID} ${VERSION_CODENAME} stable" \
      | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
    sudo apt-get update -y
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  fi
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$(id -un)"
}

ensure_dir() {
  local dir="$1" uid="${2:-$(id -u)}" gid="${3:-$(id -g)}"
  sudo mkdir -p "$dir"
  if [ "$(stat -c '%u:%g' "$dir")" != "$uid:$gid" ]; then
    sudo chown "$uid:$gid" "$dir"
  fi
}

ensure_sysctl() {
  local key="$1" value="$2" conf=/etc/sysctl.d/99-circleguard.conf
  [ "$(sysctl -n "$key")" = "$value" ] || sudo sysctl -w "$key=$value" >/dev/null
  grep -qs "^$key *= *$value$" "$conf" || echo "$key = $value" | sudo tee -a "$conf" >/dev/null
}

ensure_swap() {
  local gb="${1:-4}"
  if [ -n "$(swapon --noheadings 2>/dev/null)" ]; then log_info "Swap ya activo."; return 0; fi
  log_info "Creando swap de ${gb}G..."
  [ -f /swapfile ] || sudo fallocate -l "${gb}G" /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -qs '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
}

# Crea el archivo .env con permisos 600 SOLO si no existe; conserva secretos previos.
ensure_env_file() {
  local f="$1"
  if [ -f "$f" ]; then
    log_info "$f ya existe: se conserva."
    cat >/dev/null
    return 0
  fi
  mkdir -p "$(dirname "$f")"
  ( umask 077; cat > "$f" )
  log_info "Generado $f (600). No lo subas al repositorio."
}

set_env_kv() {
  local f="$1" k="$2" v="$3"
  [ -f "$f" ] || : > "$f"
  awk -v k="$k" -v v="$v" '
    BEGIN { done=0 }
    $0 ~ "^" k "=" { print k "=" v; done=1; next }
    { print }
    END { if (!done) print k "=" v }
  ' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
  chmod 600 "$f"
}

ensure_env_kv() {
  local f="$1" k="$2" v="$3"
  grep -q "^${k}=" "$f" 2>/dev/null || set_env_kv "$f" "$k" "$v"
}

get_env_kv() {
  local f="$1" k="$2"
  grep -m1 "^${k}=" "$f" 2>/dev/null | cut -d= -f2- || true
}

ensure_insecure_registry() {
  local reg="$1" f=/etc/docker/daemon.json
  sudo test -f "$f" || echo '{}' | sudo tee "$f" >/dev/null
  if sudo jq -e --arg r "$reg" '(.["insecure-registries"] // []) | index($r)' "$f" >/dev/null; then
    log_info "Registro ${reg} ya permitido en Docker."
    return 0
  fi
  sudo jq --arg r "$reg" '.["insecure-registries"] = ((.["insecure-registries"] // []) + [$r])' "$f" \
    | sudo tee "$f.tmp" >/dev/null && sudo mv "$f.tmp" "$f"
  sudo systemctl restart docker
  log_info "Registro ${reg} agregado a insecure-registries."
}

wait_http() {
  local url="$1" timeout="${2:-300}" pattern="${3:-}" t=0
  until body="$(curl -fsS -m 5 "$url" 2>/dev/null)" && { [ -z "$pattern" ] || grep -q "$pattern" <<<"$body"; }; do
    t=$((t + 5)); [ "$t" -ge "$timeout" ] && die "Timeout esperando $url"
    sleep 5
  done
}

compose_up() {
  local file="$1" project="$2" env_file="${3:-}" build="${4:-no}"
  [ -f "$file" ] || die "No existe $file"
  local args=(-f "$file" -p "$project")
  if [ -n "$env_file" ]; then
    [ -f "$env_file" ] || die "No existe $env_file"
    args=(--env-file "$env_file" "${args[@]}")
  fi
  [ "$build" = "yes" ] && args+=(--build)
  log_info "Levantando ${project} desde ${file} ..."
  dk compose "${args[@]}" up -d --remove-orphans --wait --wait-timeout 600
  log_info "${project} listo."
}

# Dirección privada de la instancia: es la que Jenkins usa para desplegar y la que
# Kafka anuncia en su listener externo, porque el tráfico entre hosts de la VPC
# nunca sale a Internet.
private_ip() {
  hostname -I | awk '{print $1}'
}

host_ip() {
  local token ip
  token="$(curl -s -m 2 -X PUT http://169.254.169.254/latest/api/token \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' 2>/dev/null || true)"
  if [ -n "${token}" ]; then
    ip="$(curl -s -m 2 -H "X-aws-ec2-metadata-token: ${token}" \
      http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || true)"
    [ -n "${ip}" ] && { echo "${ip}"; return 0; }
  fi
  hostname -I | awk '{print $1}'
}
