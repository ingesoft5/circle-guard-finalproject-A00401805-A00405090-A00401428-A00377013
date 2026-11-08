#!/usr/bin/env bash
# CG-DOC: Stops the provisioned EC2 instances to reduce compute cost.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

load_state
require_cmd aws

for instance_id in "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}"; do
  state="$(aws ec2 describe-instances --instance-ids "${instance_id}" \
    --query 'Reservations[0].Instances[0].State.Name' --output text)"
  if [ "${state}" = "running" ]; then
    log_info "Stopping ${instance_id}"
    aws ec2 stop-instances --instance-ids "${instance_id}" >/dev/null
  else
    log_info "${instance_id} is already ${state}"
  fi
done

aws ec2 wait instance-stopped --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}"
log_info "Instances stopped. EBS and Elastic IP charges continue while stopped."
