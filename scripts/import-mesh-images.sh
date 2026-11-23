#!/usr/bin/env bash
# CG-DOC: Import the service-mesh bonus images into the k3d node containerds.
# Runs on the on-prem k3d host (docker@10.147.20.67). Idempotent: an image is
# imported only when it is missing from a node's k8s.io containerd namespace.
#
# Why this is needed: the k3d nodes cannot pull docker.io/istio/* directly in
# this environment. We pull/save on the host, copy the tarball into each node
# container and import it with `ctr`. The images are pinned for reproducibility.
set -euo pipefail

ISTIO_VERSION="${ISTIO_VERSION:-1.27.1}"
NODES="${NODES:-k3d-cg-server-0 k3d-cg-agent-0}"

# host-image -> image name as seen by containerd (docker.io/ prefix)
IMAGES=(
  "istio/pilot:${ISTIO_VERSION}"
  "istio/proxyv2:${ISTIO_VERSION}"
  "circleguard-docker/form:1.8.0"
)

echo "==> Ensuring pinned images exist in the host Docker daemon"
for img in "${IMAGES[@]}"; do
  if docker image inspect "$img" >/dev/null 2>&1; then
    echo "    [skip] host has $img"
  else
    echo "    [pull] $img"
    docker pull "$img"
  fi
done

echo "==> Saving image tarballs on the host"
mkdir -p /tmp/cg-mesh-images
for img in "${IMAGES[@]}"; do
  slug="$(echo "$img" | tr '/:' '__')"
  tarfile="/tmp/cg-mesh-images/${slug}.tar"
  if [[ -s "$tarfile" ]]; then
    echo "    [skip] $tarfile"
  else
    docker save "$img" -o "$tarfile"
    echo "    [saved] $tarfile"
  fi
done

for node in $NODES; do
  echo "==> Node ${node}"
  if ! docker ps --format '{{.Names}}' | grep -qx "$node"; then
    echo "    [warn] container $node is not running; skipping"
    continue
  fi
  for img in "${IMAGES[@]}"; do
    ctd_img="docker.io/${img}"
    if docker exec "$node" ctr --namespace k8s.io images ls -q 2>/dev/null | grep -qx "$ctd_img"; then
      echo "    [skip] $node already has $ctd_img"
      continue
    fi
    slug="$(echo "$img" | tr '/:' '__')"
    tarfile="/tmp/cg-mesh-images/${slug}.tar"
    echo "    [import] $img -> $node"
    docker cp "$tarfile" "$node:/tmp/$(basename "$tarfile")" >/dev/null
    docker exec "$node" ctr --namespace k8s.io images import "/tmp/$(basename "$tarfile")" >/dev/null
    docker exec "$node" rm -f "/tmp/$(basename "$tarfile")"
  done
done

echo "==> Verification"
for node in $NODES; do
  echo "  ${node}:"
  docker exec "$node" ctr --namespace k8s.io images ls -q 2>/dev/null \
    | grep -E 'istio/(pilot|proxyv2)|circleguard-docker/form' | sort | sed 's/^/    /'
done

echo "IMPORT_IMAGES_DONE"
