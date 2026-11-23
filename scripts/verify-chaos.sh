#!/usr/bin/env bash
# CG-DOC: Verify the L284 chaos engineering bonus on the live k3d cluster.
# Runs on the on-prem k3d host with Chaos Mesh 2.8.8. It applies ONE real,
# bounded fault (PodChaos pod-kill on the cg-dev gateway) and proves it with
# the pod UID change and the subsequent recovery, then rolls it back.
#
# Honesty rules:
# - never report PASS if apply/injection/rollback fails;
# - all kubectl calls have a request timeout (the host can be heavily loaded);
# - a NetworkChaos delay was attempted but k3d could not inject it
#   ("unable to flush ip sets"), so it is explicitly out of scope here.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
TARGET_NS="cg-dev"
FAILED=0

k() { kubectl --context "$CTX" --request-timeout=10s "$@"; }
ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }
info() { echo "  [INFO] $*"; }
abort() {
  echo "  [ABORT] $*"
  fail "$*"
  echo "=== RESULT ==="
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed, aborted)"
  exit "$FAILED"
}

echo "=== 1. Chaos Mesh control plane ==="
k get ns "$CHAOS_NS" >/dev/null 2>&1 \
  || abort "cg-chaos namespace missing"
CM_READY=$(k get deploy chaos-controller-manager -n "$CHAOS_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$CM_READY" -ge 1 ]] \
  && ok "chaos-controller-manager ready=$CM_READY" \
  || abort "chaos-controller-manager ready=$CM_READY"
DAEMON_READY=$(k get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.numberReady}' 2>/dev/null || echo 0)
DAEMON_DESIRED=$(k get daemonset chaos-daemon -n "$CHAOS_NS" -o jsonpath='{.status.desiredNumberScheduled}' 2>/dev/null || echo 0)
info "chaos-daemon ready=$DAEMON_READY/$DAEMON_DESIRED (pod-kill uses the controller API; full network injection would need all daemons)"

echo "=== 2. Baseline: gateway healthy before the experiment ==="
GW_BEFORE=$(k get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_BEFORE" -ge 1 ]] \
  && ok "gateway baseline ready=$GW_BEFORE" \
  || abort "gateway baseline ready=$GW_BEFORE"
GW_UID_BEFORE=$(k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || echo "")
GW_POD_BEFORE=$(k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
[[ -n "$GW_UID_BEFORE" ]] \
  && ok "gateway pod baseline captured ($GW_POD_BEFORE uid=$GW_UID_BEFORE)" \
  || abort "could not capture gateway pod UID"
k delete podchaos gateway-pod-kill -n "$CHAOS_NS" --ignore-not-found >/dev/null 2>&1 || true

echo "=== 3. Apply the real PodChaos experiment ==="
k apply -f deploy/chaos/chaos-experiments.yml 2>&1 \
  || abort "could not apply the experiment (webhook/API unavailable)"
sleep 6
PC_EXISTS=$(k get podchaos gateway-pod-kill -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
[[ -n "$PC_EXISTS" ]] \
  && ok "PodChaos resource created ($PC_EXISTS)" \
  || abort "PodChaos resource was not created"
k get podchaos gateway-pod-kill -n "$CHAOS_NS" 2>&1

echo "=== 4. Injection evidence: gateway pod UID must change ==="
GW_KILLED=0
for i in $(seq 1 20); do
  GW_UID_NOW=$(k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.uid}' 2>/dev/null || echo "")
  if [[ -n "$GW_UID_NOW" && "$GW_UID_NOW" != "$GW_UID_BEFORE" ]]; then
    GW_KILLED=1
    GW_POD_AFTER=$(k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")
    echo "  gateway pod killed: $GW_POD_BEFORE -> $GW_POD_AFTER (uid changed)"
    break
  fi
  sleep 3
done
[[ "$GW_KILLED" -eq 1 ]] \
  && ok "PodChaos really killed the gateway pod (UID changed)" \
  || fail "no evidence of pod-kill (UID unchanged during 60s window)"

echo "=== 5. Recovery evidence: gateway returns to ready state ==="
GW_AFTER=0
for i in $(seq 1 20); do
  GW_AFTER=$(k get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
  [[ "$GW_AFTER" -ge 1 ]] && break
  sleep 3
done
[[ "$GW_AFTER" -ge 1 ]] \
  && ok "gateway recovered after pod-kill (ready=$GW_AFTER)" \
  || fail "gateway did not recover (ready=$GW_AFTER after 60s)"

echo "=== 6. Rollback: delete experiment and confirm steady state ==="
k delete -f deploy/chaos/chaos-experiments.yml >/dev/null 2>&1 \
  && ok "Chaos Mesh experiment deleted (rollback)" \
  || fail "cleanup delete failed"
PC_AFTER="pending"
for i in $(seq 1 10); do
  PC_AFTER=$(k get podchaos gateway-pod-kill -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
  [[ -z "$PC_AFTER" ]] && break
  sleep 3
done
[[ -z "$PC_AFTER" ]] \
  && ok "no chaos resource remains after rollback" \
  || fail "chaos resource still present after rollback"
GW_FINAL=$(k get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
[[ "$GW_FINAL" -ge 1 ]] \
  && ok "gateway steady after rollback (ready=$GW_FINAL)" \
  || fail "gateway not steady after rollback (ready=$GW_FINAL)"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "CHAOS_VERIFICATION_PASS"
else
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"
