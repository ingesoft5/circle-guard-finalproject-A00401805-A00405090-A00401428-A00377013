#!/usr/bin/env bash
# CG-DOC: Starts the provisioned EC2 instances and waits until they are running.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

load_state
require_cmd aws

for instance_id in "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}"; do
  state="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
    --query 'Reservations[0].Instances[0].State.Name' --output text)"
  if [ "${state}" = "stopped" ]; then
    log_info "Starting ${instance_id}"
    aws ec2 start-instances --instance-ids "${instance_id}" >/dev/null
  else
    log_info "${instance_id} is already ${state}"
  fi
done

aws ec2 wait instance-running --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}"
log_info "Instances running: cg-tools=${TOOLS_IP} cg-envs=${ENVS_IP}"
log_info "Docker services restart automatically because of the unless-stopped policy."
