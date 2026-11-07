#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_aws

VPC_ID="${VPC_ID:-$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)}"
[[ "${VPC_ID}" != "None" && -n "${VPC_ID}" ]] || { echo "No hay VPC default. Define VPC_ID."; exit 1; }

AMI_ID="$(aws ssm get-parameter \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query 'Parameter.Value' --output text)"

if ! aws ec2 describe-key-pairs --key-names "${KEY_NAME}" >/dev/null 2>&1; then
  log "Creando key pair ${KEY_NAME}"
  aws ec2 create-key-pair --key-name "${KEY_NAME}" --query 'KeyMaterial' --output text > "${KEY_PATH}"
  chmod 600 "${KEY_PATH}"
fi
[[ -f "${KEY_PATH}" ]] || { echo "Falta la llave privada ${KEY_PATH}"; exit 1; }

ensure_sg() {
  local name="$1" desc="$2"
  local id
  id="$(aws ec2 describe-security-groups --filters "Name=group-name,Values=${name}" "Name=vpc-id,Values=${VPC_ID}" --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    id="$(aws ec2 create-security-group --group-name "${name}" --description "${desc}" --vpc-id "${VPC_ID}" --query 'GroupId' --output text)"
  fi
  echo "${id}"
}

allow() {
  local sg="$1" port="$2"
  aws ec2 authorize-security-group-ingress --group-id "${sg}" \
    --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},IpRanges=[{CidrIp=${ADMIN_CIDR}}]" >/dev/null 2>&1 || true
}

TOOLS_SG="$(ensure_sg circleguard-tools-sg "CircleGuard CI/CD tools")"
ENVS_SG="$(ensure_sg circleguard-envs-sg "CircleGuard environments")"
allow "${TOOLS_SG}" 22; allow "${TOOLS_SG}" 8080; allow "${TOOLS_SG}" 9000
allow "${TOOLS_SG}" 8081; allow "${TOOLS_SG}" 8082
allow "${ENVS_SG}" 22; allow "${ENVS_SG}" 8087; allow "${ENVS_SG}" 8088; allow "${ENVS_SG}" 8089

ensure_instance() {
  local name="$1" type="$2" disk="$3" sg="$4"
  local id
  id="$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${name}" "Name=instance-state-name,Values=pending,running,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)"
  if [[ -z "${id}" || "${id}" == "None" ]]; then
    log "Creando instancia ${name} (${type})"
    id="$(aws ec2 run-instances \
      --image-id "${AMI_ID}" --instance-type "${type}" --key-name "${KEY_NAME}" \
      --security-group-ids "${sg}" \
      --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=${disk},VolumeType=gp3,DeleteOnTermination=true}" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${name}},{Key=Project,Value=${PROJECT}},{Key=Component,Value=${name}}]" \
      --query 'Instances[0].InstanceId' --output text)"
  fi
  aws ec2 wait instance-running --instance-ids "${id}"
  echo "${id}"
}

TOOLS_INSTANCE_ID="$(ensure_instance cg-tools "${TOOLS_TYPE}" "${TOOLS_DISK}" "${TOOLS_SG}")"
ENVS_INSTANCE_ID="$(ensure_instance cg-envs "${ENVS_TYPE}" "${ENVS_DISK}" "${ENVS_SG}")"

ensure_eip() {
  local name="$1" instance="$2"
  local alloc
  alloc="$(aws ec2 describe-addresses --filters "Name=tag:Name,Values=${name}" --query 'Addresses[0].AllocationId' --output text 2>/dev/null || true)"
  if [[ -z "${alloc}" || "${alloc}" == "None" ]]; then
    alloc="$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text)"
    aws ec2 create-tags --resources "${alloc}" --tags "Key=Name,Value=${name}" "Key=Project,Value=${PROJECT}"
  fi
  aws ec2 associate-address --instance-id "${instance}" --allocation-id "${alloc}" >/dev/null 2>&1 || true
  aws ec2 describe-addresses --allocation-ids "${alloc}" --query 'Addresses[0].PublicIp' --output text
}

TOOLS_IP="$(ensure_eip cg-tools-eip "${TOOLS_INSTANCE_ID}")"
ENVS_IP="$(ensure_eip cg-envs-eip "${ENVS_INSTANCE_ID}")"

# Jenkins en cg-tools debe desplegar por SSH y consumir Nexus en cg-envs.
for port in 22 8087 8088 8089; do
  aws ec2 authorize-security-group-ingress --group-id "${ENVS_SG}" \
    --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},IpRanges=[{CidrIp=${TOOLS_IP}/32}]" >/dev/null 2>&1 || true
done
for cidr in "${ENVS_IP}/32" "${TOOLS_IP}/32"; do
  for port in 8081 8082; do
    aws ec2 authorize-security-group-ingress --group-id "${TOOLS_SG}" \
      --ip-permissions "IpProtocol=tcp,FromPort=${port},ToPort=${port},IpRanges=[{CidrIp=${cidr}}]" >/dev/null 2>&1 || true
  done
done

save_state
cat <<EOF
Infraestructura lista:
  cg-tools: ${TOOLS_INSTANCE_ID} -> ${TOOLS_IP}
  cg-envs : ${ENVS_INSTANCE_ID} -> ${ENVS_IP}
  estado  : ${state_file}
EOF
