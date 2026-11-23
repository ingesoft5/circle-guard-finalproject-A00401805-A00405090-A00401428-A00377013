#!/usr/bin/env bash
# CG-DOC: Verify the L284 FinOps bonus on the live k3d cluster.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
FINOPS_NS="cg-finops"
MON_NS="monitoring"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }

echo "=== 1. KEDA ScaledObject for scale-to-zero ==="
kubectl --context "$CTX" get scaledobject -n "$FINOPS_NS" 2>/dev/null | grep -q file \
  && ok "ScaledObject for file service present" \
  || fail "ScaledObject for file service missing"

kubectl --context "$CTX" get scaledobject -n "$FINOPS_NS" 2>/dev/null | grep -q notification \
  && ok "ScaledObject for notification service present" \
  || fail "ScaledObject for notification service missing"

echo "=== 2. minReplicaCount:0 configuration ==="
MIN_REPLICAS=$(kubectl --context "$CTX" get scaledobject file-finops-scaler -n "$FINOPS_NS" -o jsonpath='{.spec.minReplicaCount}' 2>/dev/null || echo MISSING)
[[ "$MIN_REPLICAS" == "0" ]] \
  && ok "file service minReplicaCount=0 (scale-to-zero)" \
  || fail "file service minReplicaCount=$MIN_REPLICAS"

echo "=== 3. OpenCost monitoring ==="
kubectl --context "$CTX" get configmap opencost-prices -n "$MON_NS" >/dev/null 2>&1 \
  && ok "OpenCost price config present (opencost-prices in monitoring)" \
  || fail "OpenCost price config missing"

echo "=== 4. Grafana cost dashboard ==="
kubectl --context "$CTX" get configmap finops-cost-dashboard -n "$MON_NS" >/dev/null 2>&1 \
  && ok "Grafana cost dashboard configured (finops-cost-dashboard in monitoring)" \
  || fail "Grafana cost dashboard missing"

echo "=== 5. Spot policy documentation ==="
[[ -f docs/costos/spot-policy.md ]] \
  && ok "docs/costos/spot-policy.md exists" \
  || fail "docs/costos/spot-policy.md missing"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "FINOPS_VERIFICATION_PASS"
else
  echo "FINOPS_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"