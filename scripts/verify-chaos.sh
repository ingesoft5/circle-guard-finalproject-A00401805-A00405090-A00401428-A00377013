#!/usr/bin/env bash
# CG-DOC: Verify the L284 chaos engineering bonus on the live k3d cluster.
# Runs on the on-prem k3d host. Chaos Mesh 2.8.8 with two bounded experiments:
# PodChaos (gateway pod-kill) and NetworkChaos (redis network delay). Prints
# REAL results only: it never fabricates chaos outcomes or recovery metrics.
# Key honesty rule: if an experiment cannot be applied or does not actually
# inject, the script FAILS instead of reporting a false recovery PASS.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
TARGET_NS="cg-dev"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }
abort() {
  echo "  [ABORT] $*"
  fail "$*"
  echo "=== RESULT ==="
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed, aborted)"
  exit "$FAILED"
}

echo "=== 1. Chaos Mesh control plane ==="
kubectl --context "$CTX" get ns "$CHAOS_NS" >/dev/null 2>&1 \
  && ok "cg-chaos namespace exists" \
  || abort "cg-chaos namespace missing"
CM_READY=$(kubectl --context "$CTX" get deploy chaos-controller-manager -n "$CHAOS_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$CM_READY" -ge 1 ]] && ok "chaos-controller-manager ready=$CM_READY" || abort "chaos-controller-manager ready=$CM_READY"
DAEMON_READY=$(kubectl --context "$CTX" get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.numberReady}' 2>/dev/null || echo 0)
DAEMON_DESIRED=$(kubectl --context "$CTX" get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.desiredNumberScheduled}' 2>/dev/null || echo 0)
[[ "$DAEMON_READY" -ge 1 && "$DAEMON_READY" == "$DAEMON_DESIRED" ]] \
  && ok "chaos-daemon ready on all nodes ($DAEMON_READY/$DAEMON_DESIRED)" \
  || abort "chaos-daemon ready=$DAEMON_READY/$DAEMON_DESIRED"

echo "=== 2. Baseline: targets healthy before experiments ==="
GW_BEFORE=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
RD_BEFORE=$(kubectl --context "$CTX" get statefulset redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_BEFORE" -ge 1 ]] && ok "gateway baseline ready=$GW_BEFORE" || abort "gateway baseline ready=$GW_BEFORE"
[[ "$RD_BEFORE" -ge 1 ]] && ok "redis baseline ready=$RD_BEFORE" || abort "redis baseline ready=$RD_BEFORE"
GW_UID_BEFORE=$(kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || echo "")
GW_POD_BEFORE=$(kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
echo "  gateway pod before: $GW_POD_BEFORE uid=$GW_UID_BEFORE"

echo "=== 3. Apply Chaos Mesh experiments ==="
kubectl --context "$CTX" apply -f deploy/chaos/chaos-experiments.yml 2>&1 \
  || abort "could not apply experiments (webhook/API unavailable)"
sleep 6
PC_EXISTS=$(kubectl --context "$CTX" get podchaos gateway-pod-kill -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
NC_EXISTS=$(kubectl --context "$CTX" get networkchaos redis-network-delay -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
[[ -n "$PC_EXISTS" && -n "$NC_EXISTS" ]] \
  && ok "PodChaos + NetworkChaos resources created ($PC_EXISTS, $NC_EXISTS)" \
  || abort "experiments were not created (podchaos='$PC_EXISTS' networkchaos='$NC_EXISTS')"
kubectl --context "$CTX" get podchaos,networkchaos -n "$CHAOS_NS" 2>&1

echo "=== 4. Experiment 1: gateway pod-kill (PodChaos, 30s) ==="
# REAL evidence: the pod UID must change because PodChaos deletes the pod and
# the ReplicaSet creates a new one. A mere 'ready>=1' would be a false positive.
GW_KILLED=0
for i in $(seq 1 20); do
  GW_UID_NOW=$(kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || echo "")
  if [[ -n "$GW_UID_BEFORE" && -n "$GW_UID_NOW" && "$GW_UID_NOW" != "$GW_UID_BEFORE" ]]; then
    GW_KILLED=1
    GW_POD_AFTER=$(kubectl --context "$CTX" get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    echo "  gateway pod killed: $GW_POD_BEFORE -> $GW_POD_AFTER (uid changed)"
    break
  fi
  sleep 3
done
[[ "$GW_KILLED" -eq 1 ]] \
  && ok "PodChaos really killed the gateway pod (UID changed)" \
  || fail "no evidence of gateway pod-kill (UID unchanged during 60s window)"
GW_AFTER=$(kubectl --context "$CTX" get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_AFTER" -ge 1 ]] \
  && ok "gateway recovered after pod-kill (ready=$GW_AFTER)" \
  || fail "gateway did not recover (ready=$GW_AFTER)"

echo "=== 5. Experiment 2: redis network delay (NetworkChaos, 60s) ==="
# REAL evidence: NetworkChaos must report AllInjected=True while active.
NC_INJECTED=""
for i in $(seq 1 12); do
  NC_INJECTED=$(kubectl --context "$CTX" get networkchaos redis-network-delay -n "$CHAOS_NS" -o jsonpath='{.status.conditions[?(@.type=="AllInjected")].status}' 2>/dev/null || echo "")
  [[ "$NC_INJECTED" == "True" ]] && break
  sleep 3
done
echo "  NetworkChaos AllInjected=$NC_INJECTED"
[[ "$NC_INJECTED" == "True" ]] \
  && ok "NetworkChaos really injected latency into redis (AllInjected=True)" \
  || fail "NetworkChaos did not inject (AllInjected='$NC_INJECTED')"
echo "  Waiting for delay window + recovery (max 90s)..."
sleep 85
RD_AFTER=$(kubectl --context "$CTX" get statefulset redis -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$RD_AFTER" -ge 1 ]] \
  && ok "redis steady after network delay (ready=$RD_AFTER)" \
  || fail "redis not steady after network delay (ready=$RD_AFTER)"

echo "=== 6. Cleanup (rollback) ==="
kubectl --context "$CTX" delete -f deploy/chaos/chaos-experiments.yml 2>/dev/null \
  && ok "Chaos Mesh experiments deleted (rollback)" \
  || fail "cleanup incomplete"
sleep 8
PC_AFTER=$(kubectl --context "$CTX" get podchaos gateway-pod-kill -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
NC_AFTER=$(kubectl --context "$CTX" get networkchaos redis-network-delay -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
[[ -z "$PC_AFTER" && -z "$NC_AFTER" ]] \
  && ok "no chaos resources remain after rollback" \
  || fail "chaos resources still present after rollback"
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
