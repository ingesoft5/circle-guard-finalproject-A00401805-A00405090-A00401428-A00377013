#!/usr/bin/env bash
# CG-DOC: Provisions four EC2 hosts, two security groups, one tools Elastic IP, and the SSH key pair.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/bootstrap-common.sh"

: "${ADMIN_CIDR:?Define ADMIN_CIDR, por ejemplo 190.1.2.3/32}"
export AWS_REGION="${AWS_REGION:-us-east-2}"
export PROJECT="${PROJECT:-circleguard}"
export KEY_NAME="${KEY_NAME:-circleguard-e12-key}"
export KEY_PATH="${KEY_PATH:-${STATE_DIR}/${KEY_NAME}.pem}"
export TOOLS_TYPE="${TOOLS_TYPE:-m7i-flex.large}"
export ENVS_TYPE="${ENVS_TYPE:-m7i-flex.large}"
export TOOLS_DISK="${TOOLS_DISK:-40}"
export ENVS_DISK="${ENVS_DISK:-30}"
export TEACHER_CIDR="${TEACHER_CIDR:-}"

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
[[ -f "${KEY_PATH}" ]] || die "La llave ${KEY_PATH} no existe. Define KEY_PATH con la llave privada."

ensure_sg() {
  local name="$1" desc="$2" id
  id="$(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=${name}" "Name=vpc-id,Values=${VPC_ID}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    id="$(aws ec2 create-security-group --group-name "${name}" --description "${desc}" \
      --vpc-id "${VPC_ID}" --query 'GroupId' --output text)"
  fi
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
  local name="$1" type="$2" disk="$3" sg="$4" public_ip="$5" id state
  id="$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${name}" "Name=instance-state-name,Values=pending,running,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    local public_args=()
    [[ "${public_ip}" == "yes" ]] && public_args=(--associate-public-ip-address)
    log_info "Creando instancia ${name} (${type})"
    id="$(aws ec2 run-instances \
      --image-id "${AMI_ID}" --instance-type "${type}" --key-name "${KEY_NAME}" \
      --security-group-ids "${sg}" "${public_args[@]}" \
      --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=${disk},VolumeType=gp3,DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${name}},{Key=Project,Value=${PROJECT}},{Key=Component,Value=${name}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  else
    state="$(aws ec2 describe-instances --instance-ids "${id}" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
    [[ "${state}" == "stopped" ]] && aws ec2 start-instances --instance-ids "${id}" >/dev/null
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

# SSH solo para administración y tools.
allow_cidr "${TOOLS_SG}" 22 "${ADMIN_CIDR}"
allow_cidr "${ENVS_SG}" 22 "${ADMIN_CIDR}"
allow_sg "${ENVS_SG}" 22 "${TOOLS_SG}"

# Herramientas de entrada.
allow_cidr "${TOOLS_SG}" 8080 "0.0.0.0/0"
allow_cidr "${TOOLS_SG}" 80 "0.0.0.0/0"
allow_cidr "${TOOLS_SG}" 443 "0.0.0.0/0"
allow_cidr "${TOOLS_SG}" 9000 "${ADMIN_CIDR}"
allow_cidr "${TOOLS_SG}" 8081 "${ADMIN_CIDR}"
for cidr in ${TEACHER_CIDR:-}; do
  allow_cidr "${TOOLS_SG}" 9000 "${cidr}"
  allow_cidr "${TOOLS_SG}" 8081 "${cidr}"
done

# Registro y gateway privados.
allow_sg "${TOOLS_SG}" 5000 "${ENVS_SG}"
allow_sg "${TOOLS_SG}" 5000 "${TOOLS_SG}"
allow_sg "${ENVS_SG}" 8087 "${TOOLS_SG}"

# Middleware solo desde tools y administración.
for port in 8180 8082 8083 8084 8085 8086 5432 7474 7687 9092 29092 6379 389 1389; do
  allow_sg "${ENVS_SG}" "${port}" "${TOOLS_SG}"
  allow_cidr "${ENVS_SG}" "${port}" "${ADMIN_CIDR}"
done

TOOLS_INSTANCE_ID="$(ensure_instance cg-tools "${TOOLS_TYPE}" "${TOOLS_DISK}" "${TOOLS_SG}" no)"
DEV_INSTANCE_ID="$(ensure_instance cg-dev "${ENVS_TYPE}" "${ENVS_DISK}" "${ENVS_SG}" yes)"
STAGE_INSTANCE_ID="$(ensure_instance cg-stage "${ENVS_TYPE}" "${ENVS_DISK}" "${ENVS_SG}" yes)"
PROD_INSTANCE_ID="$(ensure_instance cg-prod "${ENVS_TYPE}" "${ENVS_DISK}" "${ENVS_SG}" yes)"
TOOLS_IP="$(ensure_eip cg-tools-eip "${TOOLS_INSTANCE_ID}")"

read -r TOOLS_PRIVATE_IP _ < <(aws ec2 describe-instances --instance-ids "${TOOLS_INSTANCE_ID}" \
  --query 'Reservations[0].Instances[0].[PrivateIpAddress,PublicIpAddress]' --output text)
for env_name in dev stage prod; do
  id_var="${env_name^^}_INSTANCE_ID"
  id="${!id_var}"
  read -r public private < <(aws ec2 describe-instances --instance-ids "${id}" \
    --query 'Reservations[0].Instances[0].[PublicIpAddress,PrivateIpAddress]' --output text)
  printf -v "${env_name^^}_PUBLIC_IP" '%s' "${public:-}"
  printf -v "${env_name^^}_PRIVATE_IP" '%s' "${private:-}"
  export "${env_name^^}_PUBLIC_IP" "${env_name^^}_PRIVATE_IP"
done
export TOOLS_PRIVATE_IP

save_state
cat <<EOF
Infraestructura lista:
  cg-tools: ${TOOLS_INSTANCE_ID} privada=${TOOLS_PRIVATE_IP} publica=${TOOLS_IP}
  cg-dev  : ${DEV_INSTANCE_ID} privada=${DEV_PRIVATE_IP} publica=${DEV_PUBLIC_IP}
  cg-stage: ${STAGE_INSTANCE_ID} privada=${STAGE_PRIVATE_IP} publica=${STAGE_PUBLIC_IP}
  cg-prod : ${PROD_INSTANCE_ID} privada=${PROD_PRIVATE_IP} publica=${PROD_PUBLIC_IP}
  estado  : ${STATE_FILE}
EOF
