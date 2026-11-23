#!/usr/bin/env bash
# CG-DOC: Verify that the shared on-prem k3d cluster exists and has a usable
# kubeconfig. This module deliberately does not create clusters: a second k3d
# cluster would collide with the host port and exhaust inotify on this host.
set -euo pipefail

CLUSTER_NAME="${1:?cluster name is required}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"

mkdir -p "${HOME}/.config/k3d"
if [ ! -f "${HOME}/.config/k3d/registries.yaml" ]; then
  cp "${REPO_ROOT}/infra/onpremise/registries.yaml" "${HOME}/.config/k3d/registries.yaml"
  echo "CG-DOC: installed k3d registry mirror from infra/onpremise/registries.yaml."
fi

# CG-DOC: Use `k3d cluster get` so detection is not sensitive to list formatting.
if ! k3d cluster get "${CLUSTER_NAME}" >/dev/null 2>&1; then
  echo "CG-DOC: required k3d cluster ${CLUSTER_NAME} does not exist." >&2
  exit 1
fi

if ! k3d kubeconfig get "${CLUSTER_NAME}" >/dev/null 2>&1; then
  echo "CG-DOC: k3d cluster ${CLUSTER_NAME} has no usable kubeconfig." >&2
  exit 1
fi

echo "CG-DOC: shared k3d cluster ${CLUSTER_NAME} is available; skipping creation."
