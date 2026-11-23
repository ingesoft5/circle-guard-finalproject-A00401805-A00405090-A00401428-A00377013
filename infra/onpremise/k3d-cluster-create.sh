#!/usr/bin/env bash
# Crea el clúster k3d `cg` del host on-premise.
# Reconstruido desde docs/infra/migracion-onpremise.md y deploy/k8s/README.md:
# si el clúster que corre difiere, ajustar este script, no al revés.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REGISTRIES="$HOME/.config/k3d/registries.yaml"

# El registro Nexus sirve por HTTP en el :5000 del host; k3d lo inyecta en los nodos.
mkdir -p "$(dirname "$REGISTRIES")"
if [ ! -f "$REGISTRIES" ]; then
  cp "$RAIZ/infra/onpremise/registries.yaml" "$REGISTRIES"
  echo "Copiado $RAIZ/infra/onpremise/registries.yaml a $REGISTRIES"
fi

if k3d cluster list 2>/dev/null | awk 'NR>1 {print $1}' | grep -qx cg; then
  echo "El clúster cg ya existe; usar 'k3d cluster start cg' tras un reinicio de Docker."
  exit 0
fi

# Traefik ya viene con k3d; esta publicación es su entrada en el host.
# v1.35.5-k3s1 arranca containerd sin la API runtime.v1 en este host, por lo que
# el agente queda NotReady tras cualquier reinicio. Se fija v1.31.1-k3s1, que ya
# arranca correctamente con el límite de inotify elevado en /etc/sysctl.d/99-cg.conf.
K3S_IMAGE="${K3S_IMAGE:-rancher/k3s:v1.31.1-k3s1}"
k3d cluster create cg -p "30080:80@loadbalancer" --image "$K3S_IMAGE" --agents 1

echo "Clúster cg listo."
echo "Falta metrics-server para los HPA (ver deploy/k8s/README.md)."
