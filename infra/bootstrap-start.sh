#!/usr/bin/env bash
# CG-DOC: Starts the four provisioned instances and refreshes ephemeral public IPs.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

load_state
require_cmd aws
readarray -t ids < <(instance_ids)

for instance_id in "${ids[@]}"; do
  state="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
    --query 'Reservations[0].Instances[0].State.Name' --output text)"
  if [ "${state}" = "stopped" ]; then
    log_info "Starting ${instance_id}"
    aws ec2 start-instances --instance-ids "${instance_id}" >/dev/null
  fi
done

aws ec2 wait instance-running --instance-ids "${ids[@]}"
refresh_public_ips
log_info "Instances running. Tools: ${TOOLS_IP}"
log_info "Environment public IPs: dev=$(get_env_kv "${STATE_FILE}" DEV_PUBLIC_IP) stage=$(get_env_kv "${STATE_FILE}" STAGE_PUBLIC_IP) prod=$(get_env_kv "${STATE_FILE}" PROD_PUBLIC_IP)"
