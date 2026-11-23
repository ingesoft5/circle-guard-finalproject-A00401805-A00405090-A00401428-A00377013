#!/usr/bin/env bash
# CG-DOC: Install the Istio control plane used by the L284 service-mesh bonus.
# Runs on the on-prem k3d host (docker@10.147.20.67). Idempotent: individual
# Helm installs are skipped when the release is already deployed.
#
# Why Helm and not istioctl: the host has no istioctl binary, but Helm and
# internet access to istio-release.storage.googleapis.com are available.
# Pinned versions keep the evidence reproducible.
set -euo pipefail

ISTIO_VERSION="${ISTIO_VERSION:-1.27.1}"
KUBE_CONTEXT="${KUBE_CONTEXT:-k3d-cg}"
MESH_NS="istio-system"
INGRESS_NS="istio-ingress"
CANARY_NS="cg-mesh-canary"

echo "==> Adding pinned Istio Helm repository (${ISTIO_VERSION})"
helm repo add istio https://istio-release.storage.googleapis.com/charts >/dev/null 2>&1 || true
helm repo update istio >/dev/null

echo "==> Ensuring namespaces ${MESH_NS}, ${INGRESS_NS}, ${CANARY_NS}"
for ns in "$MESH_NS" "$INGRESS_NS" "$CANARY_NS"; do
  kubectl --context "$KUBE_CONTEXT" get ns "$ns" >/dev/null 2>&1 \
    || kubectl --context "$KUBE_CONTEXT" create ns "$ns"
done

echo "==> Installing istio-base"
if helm --kube-context "$KUBE_CONTEXT" status istio-base -n "$MESH_NS" >/dev/null 2>&1; then
  echo "    [skip] istio-base already deployed"
else
  helm --kube-context "$KUBE_CONTEXT" upgrade --install istio-base istio/base \
    --namespace "$MESH_NS" --version "$ISTIO_VERSION" --wait
fi

echo "==> Installing istiod"
if helm --kube-context "$KUBE_CONTEXT" status istiod -n "$MESH_NS" >/dev/null 2>&1; then
  echo "    [skip] istiod already deployed"
else
  helm --kube-context "$KUBE_CONTEXT" upgrade --install istiod istio/istiod \
    --namespace "$MESH_NS" --version "$ISTIO_VERSION" --wait \
    --set pilot.discoveryRefreshDelay=2s
fi

echo "==> Installing istio-ingress (for the canary VirtualService)"
if helm --kube-context "$KUBE_CONTEXT" status istio-ingress -n "$INGRESS_NS" >/dev/null 2>&1; then
  echo "    [skip] istio-ingress already deployed"
else
  helm --kube-context "$KUBE_CONTEXT" upgrade --install istio-ingress istio/gateway \
    --namespace "$INGRESS_NS" --version "$ISTIO_VERSION" --wait \
    --set labels.istio=ingressgateway \
    --set service.type=NodePort \
    --set service.ports[0].name=http \
    --set service.ports[0].port=80 \
    --set service.ports[0].protocol=TCP
fi

echo "==> Enabling automatic sidecar injection only on cg-dev and cg-mesh-canary"
for ns in cg-dev "$CANARY_NS"; do
  kubectl --context "$KUBE_CONTEXT" label ns "$ns" istio-injection=enabled --overwrite
done

echo "==> Applying STRICT mTLS and canary routing"
kubectl --context "$KUBE_CONTEXT" apply -f "$(dirname "$0")/mtls.yml"
kubectl --context "$KUBE_CONTEXT" apply -f "$(dirname "$0")/canary.yml"

echo "==> Waiting for mesh control plane"
kubectl --context "$KUBE_CONTEXT" -n "$MESH_NS" rollout status deploy/istiod --timeout=180s
kubectl --context "$KUBE_CONTEXT" -n "$INGRESS_NS" rollout status deploy/istio-ingress --timeout=180s

echo "MESH_INSTALL_DONE"
