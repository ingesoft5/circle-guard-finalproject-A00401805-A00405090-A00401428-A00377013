#!/usr/bin/env bash
# CG-DOC: Validates AWS and local prerequisites without creating resources.
# bootstrap-check.sh - Verifica prerequisitos sin crear recursos.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

require_cmd aws jq ssh curl openssl
aws sts get-caller-identity >/dev/null || die "AWS CLI sin credenciales válidas"

export AWS_REGION="${AWS_REGION:-us-east-1}"
export TOOLS_TYPE="${TOOLS_TYPE:-t3.large}"
export ENVS_TYPE="${ENVS_TYPE:-t3.medium}"

log_info "Cuenta AWS: $(aws sts get-caller-identity --query Account --output text)"
log_info "Región: ${AWS_REGION}"
log_info "CIDR administrativo: ${ADMIN_CIDR:-no definido}"
log_info "Tipos: ${TOOLS_TYPE} / ${ENVS_TYPE}"

aws ec2 describe-instance-types \
  --instance-types "${TOOLS_TYPE}" "${ENVS_TYPE}" \
  --query 'InstanceTypes[].{Type:InstanceType,vCPU:VCpuInfo.DefaultVCpus,RAM:MemoryInfo.SizeInMiB}' \
  --output table

log_info "Revisa la capa gratuita y crea una alarma de presupuesto antes de continuar."
