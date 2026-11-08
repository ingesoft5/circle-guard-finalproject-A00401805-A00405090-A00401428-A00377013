#!/usr/bin/env bash
# bootstrap-aws.sh - Aprovisiona las EC2, Security Groups, Elastic IPs y key pair.
# Idempotente: describe antes de crear y tolera recursos ya existentes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

: "${ADMIN_CIDR:?Define ADMIN_CIDR, por ejemplo 190.1.2.3/32}"
export AWS_REGION="${AWS_REGION:-us-east-1}"
export PROJECT="${PROJECT:-circleguard}"
export KEY_NAME="${KEY_NAME:-circleguard-key}"
export KEY_PATH="${KEY_PATH:-${STATE_DIR}/${KEY_NAME}.pem}"
export TOOLS_TYPE="${TOOLS_TYPE:-t3.large}"
export ENVS_TYPE="${ENVS_TYPE:-t3.medium}"
export TOOLS_DISK="${TOOLS_DISK:-60}"
export ENVS_DISK="${ENVS_DISK:-40}"

require_cmd aws jq ssh curl openssl
aws sts get-caller-identity >/dev/null || die "AWS CLI sin credenciales válidas"

VPC_ID="${VPC_ID:-$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)}"
[[ -n "${VPC_ID}" && "${VPC_ID}" != "None" ]] || die "No hay VPC default. Define VPC_ID."

AMI_ID="$(aws ssm get-parameter \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query 'Parameter.Value' --output text)"

if ! aws ec2 describe-key-pairs --key-names "${KEY_NAME}" >/dev/null 2>&1; then
  log_info "Creando key pair ${KEY_NAME}"
  aws ec2 create-key-pair --key-name "${KEY_NAME}" --query 'KeyMaterial' --output text > "${KEY_PATH}"
  chmod 600 "${KEY_PATH}"
fi
[[ -f "${KEY_PATH}" ]] || die "Falta la llave privada ${KEY_PATH}"

ensure_sg() {
  local name="$1" desc="$2" id
  id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${name}" "Name=vpc-id,Values=${VPC_ID}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    id="$(aws ec2 create-security-group --group-name "${name}" --description "${desc}" \
      --vpc-id "${VPC_ID}" --query 'GroupId' --output text)"
    log_info "Security Group creado: ${name}"
  fi
  aws ec2 create-tags --resources "${id}" --tags "Key=Project,Value=${PROJECT}" >/dev/null 2>&1 || true
  echo "${id}"
}

allow_cidr() {
  local sg="$1" port="$2" cidr="$3"
  aws ec2 authorize-security-group-ingress --group-id "${sg}" \
    --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},IpRanges=[{CidrIp=${cidr}}]" \
    >/dev/null 2>&1 || true
}

allow_sg() {
  local sg="$1" port="$2" source_sg="$3"
  aws ec2 authorize-security-group-ingress --group-id "${sg}" \
    --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},UserIdGroupPairs=[{GroupId=${source_sg}}]" \
    >/dev/null 2>&1 || true
}

ensure_instance() {
  local name="$1" type="$2" disk="$3" sg="$4" id state
  id="$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${name}" "Name=instance-state-name,Values=pending,running,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    log_info "Creando instancia ${name} (${type})"
    id="$(aws ec2 run-instances \
      --image-id "${AMI_ID}" --instance-type "${type}" --key-name "${KEY_NAME}" \
      --security-group-ids "${sg}" \
      --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=${disk},VolumeType=gp3,DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${name}},{Key=Project,Value=${PROJECT}},{Key=Component,Value=${name}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    state="$(aws ec2 describe-instances --instance-ids "${id}" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
    if [[ "${state}" == "stopped" ]]; then
      log_info "Iniciando instancia existente ${name}"
      aws ec2 start-instances --instance-ids "${id}" >/dev/null
    fi
  fi
  aws ec2 wait instance-running --instance-ids "${id}"
  echo "${id}"
}

ensure_eip() {
  local name="$1" instance="$2" alloc
  alloc="$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=${name}" \
    --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)"
  if [[ -z "${alloc}" || "${alloc}" == "None" ]]; then
    alloc="$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text)"
    aws ec2 create-tags --resources "${alloc}" --tags "Key=Name,Value=${name}" "Key=Project,Value=${PROJECT}"
  fi
  aws ec2 associate-address --instance-id "${instance}" --allocation-id "${alloc}" --allow-reassociation >/dev/null
  aws ec2 describe-addresses --allocation-ids "${alloc}" --query 'Addresses[0].PublicIp' --output text
}

TOOLS_SG="$(ensure_sg circleguard-tools-sg "CircleGuard CI/CD tools")"
ENVS_SG="$(ensure_sg circleguard-envs-sg "CircleGuard environments")"

# Acceso administrativo (y docente si se define).
for cidr in "${ADMIN_CIDR}" ${TEACHER_CIDR:-}; do
  for port in 22 8080 9000 8081 8082; do allow_cidr "${TOOLS_SG}" "${port}" "${cidr}"; done
  for port in 22 8087 8088 8089; do allow_cidr "${ENVS_SG}" "${port}" "${cidr}"; done
done

# Tráfico interno por referencia de Security Group (no por IP pública).
for port in 8081 8082; do
  allow_sg "${TOOLS_SG}" "${port}" "${TOOLS_SG}"
  allow_sg "${TOOLS_SG}" "${port}" "${ENVS_SG}"
done
for port in 22 8087 8088 8089; do
  allow_sg "${ENVS_SG}" "${port}" "${TOOLS_SG}"
done

TOOLS_INSTANCE_ID="$(ensure_instance cg-tools "${TOOLS_TYPE}" "${TOOLS_DISK}" "${TOOLS_SG}")"
ENVS_INSTANCE_ID="$(ensure_instance cg-envs "${ENVS_TYPE}" "${ENVS_DISK}" "${ENVS_SG}")"
TOOLS_IP="$(ensure_eip cg-tools-eip "${TOOLS_INSTANCE_ID}")"
ENVS_IP="$(ensure_eip cg-envs-eip "${ENVS_INSTANCE_ID}")"

save_state

cat <<EOF
Infraestructura lista:
  cg-tools: ${TOOLS_INSTANCE_ID} -> ${TOOLS_IP}
  cg-envs : ${ENVS_INSTANCE_ID} -> ${ENVS_IP}
  estado  : ${STATE_FILE}
EOF
