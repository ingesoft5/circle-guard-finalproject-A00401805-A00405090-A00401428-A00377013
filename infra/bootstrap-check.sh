#!/usr/bin/env bash
# CG-DOC: Validates AWS and local prerequisites without creating resources.
# bootstrap-check.sh - Verifica prerequisitos sin crear recursos.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

require_cmd aws jq ssh curl openssl
aws sts get-caller-identity >/dev/null || die "AWS CLI sin credenciales válidas"

export AWS_REGION="${AWS_REGION:-us-east-1}"
export TOOLS_TYPE="${TOOLS_TYPE:-m7i-flex.xlarge}"
export ENVS_TYPE="${ENVS_TYPE:-t3.medium}"

log_info "Cuenta AWS: $(aws sts get-caller-identity --query Account --output text)"
log_info "Región: ${AWS_REGION}"
log_info "CIDR administrativo: ${ADMIN_CIDR:-no definido}"
log_info "Tipos: ${TOOLS_TYPE} / ${ENVS_TYPE}"

if available="$(aws ec2 describe-instance-types \
  --instance-types "${TOOLS_TYPE}" "${ENVS_TYPE}" \
  --query 'InstanceTypes[].InstanceType' --output text 2>/dev/null)"; then
  for instance_type in "${TOOLS_TYPE}" "${ENVS_TYPE}"; do
    grep -qw "${instance_type}" <<<"${available}" || die "Tipo de instancia no disponible en ${AWS_REGION}: ${instance_type}"
  done
  aws ec2 describe-instance-types \
    --instance-types "${TOOLS_TYPE}" "${ENVS_TYPE}" \
    --query 'InstanceTypes[].{Type:InstanceType,vCPU:VCpuInfo.DefaultVCpus,RAM:MemoryInfo.SizeInMiB}' \
    --output table
else
  log_warn "No se pudo validar DescribeInstanceTypes (permiso o SCP). Se omite la comprobación."
fi

log_info "Free Tier only covers t3.micro/t2.micro; these sizes use account credits."
log_info "Create an AWS Budget alert before provisioning."
