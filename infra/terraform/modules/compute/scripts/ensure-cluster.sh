#!/usr/bin/env bash
# CG-DOC: Create the k3d cluster only when it does not already exist.
set -euo pipefail

CLUSTER_NAME="${1:?cluster name is required}"
AGENTS="${2:?agent count is required}"
K3S_VERSION="${3:?k3s version is required}"
INGRESS_PORT="${4:-30080}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"

mkdir -p "${HOME}/.config/k3d"
if [ ! -f "${HOME}/.config/k3d/registries.yaml" ]; then
  cp "${REPO_ROOT}/infra/onpremise/registries.yaml" "${HOME}/.config/k3d/registries.yaml"
  echo "CG-DOC: installed k3d registry mirror from infra/onpremise/registries.yaml."
fi

# CG-DOC: Use `k3d cluster get` so detection is not sensitive to list formatting.
if k3d cluster get "${CLUSTER_NAME}" >/dev/null 2>&1; then
  # CG-DOC: A stale cluster without a server node cannot provide a kubeconfig.
  if k3d kubeconfig get "${CLUSTER_NAME}" >/dev/null 2>&1; then
    echo "CG-DOC: cluster ${CLUSTER_NAME} already exists; skipping creation."
    exit 0
  fi
  echo "CG-DOC: stale cluster ${CLUSTER_NAME} has no server node; deleting it."
  k3d cluster delete "${CLUSTER_NAME}"
fi

echo "CG-DOC: creating cluster ${CLUSTER_NAME} with ${AGENTS} agents."
k3d cluster create "${CLUSTER_NAME}" \
  --agents "${AGENTS}" \
  --image "rancher/k3s:${K3S_VERSION}" \
  -p "${INGRESS_PORT}:80@loadbalancer" \
  --wait
