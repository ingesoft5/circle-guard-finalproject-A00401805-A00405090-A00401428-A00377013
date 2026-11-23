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
# `--wait` makes Helm fail the release while the large pilot image is still
# being pulled (10+ min on this host). We verify readiness with rollout status
# at the end instead, so the release always converges to `deployed`.
ISTIOD_STATUS="$(helm --kube-context "$KUBE_CONTEXT" status istiod -n "$MESH_NS" -o json 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 || true)"
if [[ "$ISTIOD_STATUS" == '"status":"deployed"' ]]; then
  echo "    [skip] istiod already deployed"
else
  helm --kube-context "$KUBE_CONTEXT" upgrade --install istiod istio/istiod \
    --namespace "$MESH_NS" --version "$ISTIO_VERSION" \
    --set pilot.discoveryRefreshDelay=2s
fi

echo "==> Installing istio-ingress (for the canary VirtualService)"
INGRESS_STATUS="$(helm --kube-context "$KUBE_CONTEXT" status istio-ingress -n "$INGRESS_NS" -o json 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 || true)"
if [[ "$INGRESS_STATUS" == '"status":"deployed"' ]]; then
  echo "    [skip] istio-ingress already deployed"
else
  helm --kube-context "$KUBE_CONTEXT" upgrade --install istio-ingress istio/gateway \
    --namespace "$INGRESS_NS" --version "$ISTIO_VERSION" \
    --set labels.istio=ingressgateway \
    --set service.type=NodePort \
    --set service.ports[0].name=http \
    --set service.ports[0].port=80 \
    --set service.ports[0].protocol=TCP
fi

echo "==> Enabling automatic sidecar injection only on ${CANARY_NS}"
# cg-dev is intentionally NOT injected: the agent node is NotReady and
# restarting the mandatory deployment would put the graded app at risk.
kubectl --context "$KUBE_CONTEXT" label ns "$CANARY_NS" istio-injection=enabled --overwrite

echo "==> Copying cg-dev ConfigMap/Secret into the isolated canary namespace"
for kindname in "configmap/cg-config" "secret/cg-secrets"; do
  kubectl --context "$KUBE_CONTEXT" get "$kindname" -n cg-dev -o json \
    | jq 'del(.metadata.namespace, .metadata.resourceVersion, .metadata.uid, .metadata.creationTimestamp)' \
    | kubectl --context "$KUBE_CONTEXT" apply -n "$CANARY_NS" -f - >/dev/null
done

echo "==> Applying STRICT mTLS and canary routing"
kubectl --context "$KUBE_CONTEXT" apply -f "$(dirname "$0")/mtls.yml"
kubectl --context "$KUBE_CONTEXT" apply -f "$(dirname "$0")/canary.yml"

echo "==> Waiting for mesh control plane"
kubectl --context "$KUBE_CONTEXT" -n "$MESH_NS" rollout status deploy/istiod --timeout=300s
kubectl --context "$KUBE_CONTEXT" -n "$INGRESS_NS" rollout status deploy/istio-ingress --timeout=300s
kubectl --context "$KUBE_CONTEXT" -n "$CANARY_NS" rollout status deploy/form-stable --timeout=300s
kubectl --context "$KUBE_CONTEXT" -n "$CANARY_NS" rollout status deploy/form-hu6 --timeout=300s

echo "MESH_INSTALL_DONE"
