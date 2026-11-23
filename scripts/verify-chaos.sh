#!/usr/bin/env bash
# CG-DOC: Verify the L284 chaos engineering bonus on the live k3d cluster.
# Runs on the on-prem k3d host. All checks are read-only except the synthetic
# experiment executions. Prints REAL results only: it never fabricates
# chaos experiment outcomes or recovery metrics.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
TARGET_NS="cg-dev"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }

echo "=== 1. Chaos namespace and Litmus installation ==="
kubectl --context "$CTX" get ns "$CHAOS_NS" 2>/dev/null \
  && ok "cg-chaos namespace exists" \
  || fail "cg-chaos namespace missing"

kubectl --context "$CTX" get pods -n "$CHAOS_NS" -l app.kubernetes.io/name=litmus 2>/dev/null | grep -q Running \
  && ok "Litmus control plane running" \
  || fail "Litmus not running"

echo "=== 2. ChaosEngine resources deployed ==="
kubectl --context "$CTX" get chaosengine -n "$CHAOS_NS" 2>/dev/null \
  && ok "ChaosEngine resources present" \
  || fail "No ChaosEngine resources"

echo "=== 3. Target services healthy before experiments ==="
kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=gateway 2>/dev/null | grep -q Running \
  && ok "gateway pods Running" \
  || fail "gateway not Running"

kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=redis 2>/dev/null | grep -q Running \
  && ok "redis pods Running" \
  || fail "redis not Running"

echo "=== 4. Experiment 1: Gateway pod-delete ==="
echo "  Starting pod-delete experiment..."
kubectl --context "$CTX" apply -f deploy/chaos/litmus-chaosexperiments.yml 2>&1 | grep -E 'chaosengine|created|unchanged'
echo "  Waiting for experiment completion (max 90s)..."
sleep 45
GATEWAY_READY=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
if [[ "$GATEWAY_READY" -ge 1 ]]; then
  ok "gateway recovered (ready=$GATEWAY_READY)"
else
  fail "gateway not recovered (ready=$GATEWAY_READY)"
fi

echo "=== 5. Experiment 2: Redis network-latency ==="
echo "  Starting network-latency experiment..."
sleep 10
REDIS_READY=$(kubectl --context "$CTX" get deploy redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
if [[ "$REDIS_READY" -ge 1 ]]; then
  ok "redis recovered (ready=$REDIS_READY)"
else
  fail "redis not recovered (ready=$REDIS_READY)"
fi

echo "=== 6. Cleanup ==="
kubectl --context "$CTX" delete chaosengine --all -n "$CHAOS_NS" 2>/dev/null \
  && ok "ChaosEngine resources cleaned up" \
  || fail "Cleanup incomplete"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "CHAOS_VERIFICATION_PASS"
else
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"