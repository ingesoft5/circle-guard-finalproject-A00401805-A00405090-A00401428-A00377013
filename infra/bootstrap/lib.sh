#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATE_DIR="${ROOT_DIR}/infra/state"
mkdir -p "${STATE_DIR}"

export AWS_REGION="${AWS_REGION:-us-east-1}"
export PROJECT="${PROJECT:-circleguard}"
export KEY_NAME="${KEY_NAME:-circleguard-key}"
export KEY_PATH="${KEY_PATH:-${STATE_DIR}/${KEY_NAME}.pem}"
export ADMIN_CIDR="${ADMIN_CIDR:?Define ADMIN_CIDR, por ejemplo 190.1.2.3/32}"
export TOOLS_TYPE="${TOOLS_TYPE:-t3.large}"
export ENVS_TYPE="${ENVS_TYPE:-t3.medium}"
export TOOLS_DISK="${TOOLS_DISK:-60}"
export ENVS_DISK="${ENVS_DISK:-40}"

state_file="${STATE_DIR}/aws.env"

log() { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }

require_aws() {
  command -v aws >/dev/null 2>&1 || { echo "Falta AWS CLI v2"; exit 1; }
  aws sts get-caller-identity >/dev/null
}

load_state() {
  [[ -f "${state_file}" ]] || { echo "Falta ${state_file}. Ejecuta 01-provision-aws.sh"; exit 1; }
  # shellcheck disable=SC1090
  source "${state_file}"
}

save_state() {
  {
    echo "AWS_REGION=${AWS_REGION}"
    echo "TOOLS_INSTANCE_ID=${TOOLS_INSTANCE_ID}"
    echo "ENVS_INSTANCE_ID=${ENVS_INSTANCE_ID}"
    echo "TOOLS_IP=${TOOLS_IP}"
    echo "ENVS_IP=${ENVS_IP}"
    echo "TOOLS_SG=${TOOLS_SG}"
    echo "ENVS_SG=${ENVS_SG}"
    echo "KEY_PATH=${KEY_PATH}"
  } > "${state_file}"
}

ssh_host() {
  local ip="$1"; shift
  ssh -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null "ubuntu@${ip}" "$@"
}

scp_host() {
  local src="$1" dst="$2" ip="$3"
  scp -i "${KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -r "${src}" "ubuntu@${ip}:${dst}"
}
