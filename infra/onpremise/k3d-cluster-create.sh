#!/usr/bin/env bash
# CG-DOC: Creates the k3d cluster used by the on-premise environment.
# Creates the `cg` k3d cluster on the on-premise host.
# Rebuilt from docs/infra/migracion-onpremise.md and deploy/k8s/K8S.md:
# if the running cluster differs, adjust this script, not the other way around.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REGISTRIES="$HOME/.config/k3d/registries.yaml"

# The Nexus registry serves HTTP on host port 5000; k3d injects it into the nodes.
mkdir -p "$(dirname "$REGISTRIES")"
if [ ! -f "$REGISTRIES" ]; then
  cp "$RAIZ/infra/onpremise/registries.yaml" "$REGISTRIES"
  echo "Copiado $RAIZ/infra/onpremise/registries.yaml a $REGISTRIES"
fi

if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx cg; then
  echo "El clúster cg ya existe; usar 'k3d cluster start cg' tras un reinicio de Docker."
  exit 0
fi

# Traefik ships with k3d; this publication is its host entrypoint.
# v1.35.5-k3s1 is the version validated on the on-prem host. The final cluster
# runs a single server node (0 agents) with the raised inotify limit in
# /etc/sysctl.d/99-cg.conf, matching the evidence captured for the delivery.
K3S_IMAGE="${K3S_IMAGE:-rancher/k3s:v1.35.5-k3s1}"
k3d cluster create cg -p "30080:80@loadbalancer" --image "$K3S_IMAGE" --agents 0

echo "Clúster cg listo."
echo "Falta metrics-server para los HPA (ver deploy/k8s/K8S.md)."
