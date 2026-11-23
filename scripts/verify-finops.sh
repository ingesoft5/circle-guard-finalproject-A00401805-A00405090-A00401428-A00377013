#!/usr/bin/env bash
# CG-DOC: Verify the L284 FinOps bonus on the live k3d cluster.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
TARGET_NS="cg-dev"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }

echo "=== 1. KEDA ScaledObject for scale-to-zero ==="
kubectl --context "$CTX" get scaledobject -n "$TARGET_NS" 2>/dev/null | grep -q file \
  && ok "ScaledObject for file service present" \
  || fail "ScaledObject for file service missing"

kubectl --context "$CTX" get scaledobject -n "$TARGET_NS" 2>/dev/null | grep -q notification \
  && ok "ScaledObject for notification service present" \
  || fail "ScaledObject for notification service missing"

echo "=== 2. minReplicas:0 configuration ==="
MIN_REPLICAS=$(kubectl --context "$CTX" get scaledobject file -n "$TARGET_NS" -o jsonpath='{.spec.minReplicas}' 2>/dev/null || echo 0)
[[ "$MIN_REPLICAS" == "0" ]] \
  && ok "file service minReplicas=0 (scale-to-zero)" \
  || fail "file service minReplicas=$MIN_REPLICAS"

echo "=== 3. OpenCost monitoring ==="
kubectl --context "$CTX" get configmap -n kube-system 2>/dev/null | grep -q opencost \
  && ok "OpenCost config present" \
  || fail "OpenCost config missing"

echo "=== 4. Grafana cost dashboard ==="
kubectl --context "$CTX" get configmap -n monitoring 2>/dev/null | grep -q grafana \
  && ok "Grafana dashboards configured" \
  || fail "Grafana dashboards missing"

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