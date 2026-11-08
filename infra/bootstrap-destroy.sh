#!/usr/bin/env bash
# CG-DOC: Destroys AWS resources created by the bootstrap.
# bootstrap-destroy.sh - Destruye la infraestructura AWS creada por bootstrap-aws.sh.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

[[ "${CONFIRM:-}" == "yes" ]] || die "Ejecuta con CONFIRM=yes para destruir la infraestructura"
load_state
require_cmd aws

log_info "Terminando instancias"
aws ec2 terminate-instances --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}" >/dev/null || true
aws ec2 wait instance-terminated --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}" || true

log_info "Liberando Elastic IPs"
for name in cg-tools-eip cg-envs-eip; do
  alloc="$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=${name}" \
    --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)"
  [[ -n "${alloc}" && "${alloc}" != "None" ]] && aws ec2 release-address --allocation-id "${alloc}" || true
done

log_info "Eliminando Security Groups"
for sg in "${TOOLS_SG}" "${ENVS_SG}"; do
  [[ -n "${sg}" && "${sg}" != "None" ]] && aws ec2 delete-security-group --group-id "${sg}" >/dev/null 2>&1 || true
done

log_info "Eliminando key pair"
aws ec2 delete-key-pair --key-name "${KEY_NAME:-circleguard-key}" >/dev/null 2>&1 || true
rm -f "${KEY_PATH}" "${STATE_FILE}" "${STATE_DIR}/tools.env" "${STATE_DIR}/registry.env"
log_info "Destrucción completada"
