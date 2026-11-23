#!/usr/bin/env bash
# CG-DOC: Install OpenCost for the L284 FinOps cost-monitoring bonus.
# Runs on the on-prem k3d host. Idempotent. Deploys OpenCost with a local
# on-prem price ConfigMap (no cloud billing API available on-prem) and a
# Grafana cost dashboard ConfigMap discovered by the sidecar in monitoring.
set -euo pipefail

KUBE_CONTEXT="${KUBE_CONTEXT:-k3d-cg}"
MON_NS="monitoring"

echo "==> Adding pinned OpenCost Helm repository"
helm repo add opencost https://opencost.github.io/opencost-helm-chart >/dev/null 2>&1 || true
helm repo update opencost >/dev/null

echo "==> Installing OpenCost into ${MON_NS}"
if helm status opencost -n "$MON_NS" >/dev/null 2>&1; then
  echo "    [skip] opencost release already deployed"
else
  helm upgrade --install opencost opencost/opencost \
    --namespace "$MON_NS" \
    --set opencost.exporter.defaultClusterName=k3d-cg-onprem \
    --set opencost.ui.enabled=false \
    --wait --timeout 5m
fi

echo "==> Applying on-prem price ConfigMap"
kubectl --context "$KUBE_CONTEXT" apply -f deploy/finops/opencost-prices.yml

echo "==> Applying Grafana cost dashboard ConfigMap"
kubectl --context "$KUBE_CONTEXT" apply -f deploy/finops/grafana-cost-dashboard.yml

echo "==> OpenCost state"
kubectl --context "$KUBE_CONTEXT" get pods -n "$MON_NS" -l app.kubernetes.io/name=opencost 2>&1 || true
kubectl --context "$KUBE_CONTEXT" get configmap -n "$MON_NS" -l finops=cost 2>&1 || true
echo "OPENCOST_INSTALL_DONE"
