#!/usr/bin/env bash
# CG-DOC: Install Chaos Mesh for the L284 chaos-engineering bonus.
# Runs on the on-prem k3d host. Idempotent: Helm releases are left as-is when
# already deployed. Pinned versions keep the evidence reproducible.
set -euo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
CERT_NS="cert-manager"
CERT_VERSION="${CERT_VERSION:-1.16.4}"
CHAOS_VERSION="${CHAOS_VERSION:-2.8.8}"

echo "==> Adding pinned Helm repositories"
helm repo add cert-manager https://charts.jetstack.io >/dev/null 2>&1 || true
helm repo add chaos-mesh https://charts.chaos-mesh.org >/dev/null 2>&1 || true
helm repo update cert-manager chaos-mesh >/dev/null

echo "==> Installing cert-manager ${CERT_VERSION} (Chaos Mesh webhook dependency)"
if helm status cert-manager -n "$CERT_NS" >/dev/null 2>&1; then
  echo "    [skip] cert-manager already deployed"
else
  kubectl --context "$CTX" create ns "$CERT_NS" >/dev/null 2>&1 || true
  helm upgrade --install cert-manager cert-manager/cert-manager \
    --version "$CERT_VERSION" --namespace "$CERT_NS" \
    --set crds.enabled=true --wait --timeout 300s
fi

echo "==> Ensuring namespace ${CHAOS_NS}"
kubectl --context "$CTX" create ns "$CHAOS_NS" >/dev/null 2>&1 || true

echo "==> Installing/upgrading Chaos Mesh ${CHAOS_VERSION}"
# Always run helm upgrade --install (convergent idempotence): on k3s the
# containerd socket lives at /run/k3s/containerd/containerd.sock, not the
# chart default /run/containerd/containerd.sock. Re-applying guarantees the
# daemon pods converge on the correct socket even if an earlier install used
# the wrong path and entered CrashLoopBackOff.
helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh \
  --version "$CHAOS_VERSION" --namespace "$CHAOS_NS" \
  --set controllerManager.replicaCount=1 \
  --set dashboard.create=false \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/k3s/containerd/containerd.sock \
  --wait --timeout 600s

echo "==> Verification"
kubectl --context "$CTX" get pods -n "$CERT_NS" 2>&1
kubectl --context "$CTX" get pods -n "$CHAOS_NS" 2>&1
echo "CHAOS_INSTALL_DONE"
