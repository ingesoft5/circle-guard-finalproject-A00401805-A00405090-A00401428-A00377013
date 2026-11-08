#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"

log "Verificando AWS CLI y credenciales"
require_aws

for cmd in jq ssh curl; do
  command -v "${cmd}" >/dev/null 2>&1 || { echo "Falta ${cmd}"; exit 1; }
done

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
log "Cuenta AWS: ${ACCOUNT_ID}"
log "Región: ${AWS_REGION}"
log "CIDR administrativo: ${ADMIN_CIDR}"
log "Tipos: ${TOOLS_TYPE} / ${ENVS_TYPE}"

aws ec2 describe-instance-types \
  --instance-types "${TOOLS_TYPE}" "${ENVS_TYPE}" \
  --query 'InstanceTypes[].{Type:InstanceType,vCPU:VCpuInfo.DefaultVCpus,RAM:MemoryInfo.SizeInMiB}' \
  --output table

log "Revisa la capa gratuita y crea una alarma de presupuesto antes de continuar."
