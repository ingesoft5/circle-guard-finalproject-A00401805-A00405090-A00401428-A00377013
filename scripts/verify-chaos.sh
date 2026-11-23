#!/usr/bin/env bash
# CG-DOC: Verify the L284 chaos engineering bonus on the live k3d cluster.
# Runs on the on-prem k3d host. Chaos Mesh 2.8.8 with two bounded experiments:
# PodChaos (gateway pod-kill) and NetworkChaos (redis network delay). Prints
# REAL results only: it never fabricates chaos outcomes or recovery metrics.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
TARGET_NS="cg-dev"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }

echo "=== 1. Chaos Mesh control plane ==="
kubectl --context "$CTX" get ns "$CHAOS_NS" 2>/dev/null \
  && ok "cg-chaos namespace exists" \
  || fail "cg-chaos namespace missing"
CM_READY=$(kubectl --context "$CTX" get deploy chaos-controller-manager -n "$CHAOS_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$CM_READY" -ge 1 ]] && ok "chaos-controller-manager ready" || fail "chaos-controller-manager ready=$CM_READY"
DAEMON_READY=$(kubectl --context "$CTX" get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.numberReady}' 2>/dev/null || echo 0)
DAEMON_DESIRED=$(kubectl --context "$CTX" get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.desiredNumberScheduled}' 2>/dev/null || echo 0)
[[ "$DAEMON_READY" -ge 1 && "$DAEMON_READY" == "$DAEMON_DESIRED" ]] \
  && ok "chaos-daemon ready on all nodes ($DAEMON_READY/$DAEMON_DESIRED)" \
  || fail "chaos-daemon ready=$DAEMON_READY/$DAEMON_DESIRED"

echo "=== 2. Baseline: targets healthy before experiments ==="
GW_BEFORE=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
RD_BEFORE=$(kubectl --context "$CTX" get statefulset redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_BEFORE" -ge 1 ]] && ok "gateway baseline ready=$GW_BEFORE" || fail "gateway baseline ready=$GW_BEFORE"
[[ "$RD_BEFORE" -ge 1 ]] && ok "redis baseline ready=$RD_BEFORE" || fail "redis baseline ready=$RD_BEFORE"

echo "=== 3. Apply Chaos Mesh experiments ==="
kubectl --context "$CTX" apply -f deploy/chaos/chaos-experiments.yml 2>&1 \
  && ok "PodChaos + NetworkChaos applied" \
  || fail "could not apply experiments"
sleep 5
kubectl --context "$CTX" get podchaos,networkchaos -n "$CHAOS_NS" 2>&1

echo "=== 4. Experiment 1: gateway pod-kill (PodChaos, 30s) ==="
echo "  Waiting for pod-kill window + recovery (max 75s)..."
sleep 70
GW_AFTER=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_AFTER" -ge 1 ]] \
  && ok "gateway recovered after pod-kill (ready=$GW_AFTER)" \
  || fail "gateway did not recover (ready=$GW_AFTER)"

echo "=== 5. Experiment 2: redis network delay (NetworkChaos, 60s) ==="
echo "  Waiting for delay window + recovery (max 90s)..."
sleep 85
RD_AFTER=$(kubectl --context "$CTX" get statefulset redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$RD_AFTER" -ge 1 ]] \
  && ok "redis recovered after network delay (ready=$RD_AFTER)" \
  || fail "redis did not recover (ready=$RD_AFTER)"

echo "=== 6. Cleanup (rollback) ==="
kubectl --context "$CTX" delete -f deploy/chaos/chaos-experiments.yml 2>/dev/null \
  && ok "Chaos Mesh experiments deleted (rollback)" \
  || fail "cleanup incomplete"
sleep 5
GW_FINAL=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
RD_FINAL=$(kubectl --context "$CTX" get statefulset redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_FINAL" -ge 1 && "$RD_FINAL" -ge 1 ]] \
  && ok "all targets steady after rollback (gateway=$GW_FINAL redis=$RD_FINAL)" \
  || fail "targets not steady after rollback (gateway=$GW_FINAL redis=$RD_FINAL)"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "CHAOS_VERIFICATION_PASS"
else
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"
