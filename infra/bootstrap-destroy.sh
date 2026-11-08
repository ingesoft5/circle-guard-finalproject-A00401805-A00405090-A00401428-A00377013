#!/usr/bin/env bash
# CG-DOC: Destroys the four hosts, tools Elastic IP, security groups, key pair, and local state.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

[[ "${CONFIRM:-}" == "yes" ]] || die "Ejecuta con CONFIRM=yes para destruir la infraestructura"
load_state
require_cmd aws
readarray -t ids < <(instance_ids)

aws ec2 terminate-instances --instance-ids "${ids[@]}" >/dev/null || true
aws ec2 wait instance-terminated --instance-ids "${ids[@]}" || true

alloc="$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=cg-tools-eip" \
  --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)"
[[ -n "${alloc}" && "${alloc}" != "None" ]] && aws ec2 release-address --allocation-id "${alloc}" || true

for sg in "${TOOLS_SG}" "${ENVS_SG}"; do
  [[ -n "${sg}" && "${sg}" != "None" ]] && aws ec2 delete-security-group --group-id "${sg}" >/dev/null 2>&1 || true
done

aws ec2 delete-key-pair --key-name "${KEY_NAME:-circleguard-e12-key}" >/dev/null 2>&1 || true
rm -f "${KEY_PATH}" "${STATE_FILE}" "${STATE_DIR}/tools.env" "${STATE_DIR}/registry.env"
log_info "Destroy complete."
