#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
load_state

[[ "${CONFIRM:-}" == "yes" ]] || { echo "Ejecuta con CONFIRM=yes para destruir la infraestructura"; exit 1; }

log "Terminando instancias"
aws ec2 terminate-instances --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}" >/dev/null || true
aws ec2 wait instance-terminated --instance-ids "${TOOLS_INSTANCE_ID}" "${ENVS_INSTANCE_ID}" || true

log "Liberando Elastic IPs"
for name in cg-tools-eip cg-envs-eip; do
  alloc="$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=${name}" --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)"
  [[ -n "${alloc}" && "${alloc}" != "None" ]] && aws ec2 release-address --allocation-id "${alloc}" || true
done

log "Eliminando Security Groups"
for sg in "${TOOLS_SG}" "${ENVS_SG}"; do
  [[ -n "${sg}" && "${sg}" != "None" ]] && aws ec2 delete-security-group --group-id "${sg}" >/dev/null 2>&1 || true
done

log "Eliminando key pair"
aws ec2 delete-key-pair --key-name "${KEY_NAME}" >/dev/null 2>&1 || true
rm -f "${KEY_PATH}"
log "Destrucción completada"
