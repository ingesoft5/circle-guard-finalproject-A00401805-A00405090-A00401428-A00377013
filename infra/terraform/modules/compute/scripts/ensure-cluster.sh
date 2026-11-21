#!/usr/bin/env bash
# CG-DOC: Create the k3d cluster only when it does not already exist.
set -euo pipefail

CLUSTER_NAME="${1:?cluster name is required}"
AGENTS="${2:?agent count is required}"
K3S_VERSION="${3:?k3s version is required}"

if k3d cluster list --no-headers | awk '{print $1}' | grep -qx "${CLUSTER_NAME}"; then
  echo "CG-DOC: cluster ${CLUSTER_NAME} already exists; skipping creation."
  exit 0
fi

echo "CG-DOC: creating cluster ${CLUSTER_NAME} with ${AGENTS} agents."
k3d cluster create "${CLUSTER_NAME}" \
  --agents "${AGENTS}" \
  --image "rancher/k3s:${K3S_VERSION}" \
  --wait
