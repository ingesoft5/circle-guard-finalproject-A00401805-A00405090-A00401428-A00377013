#!/usr/bin/env bash
# CG-DOC: Verify the L284 chaos engineering bonus on the live k3d cluster.
# Runs on the on-prem k3d host with Chaos Mesh 2.8.8. It applies ONE real,
# bounded fault (PodChaos pod-kill on the cg-dev gateway) and proves it with
# the pod UID change and the subsequent recovery, then rolls it back.
#
# Verification rules:
# - report PASS only when apply/injection/rollback succeed;
# - all kubectl calls have a request timeout (the host can be heavily loaded);
# - NetworkChaos delay is out of scope; this verifier covers the PodChaos fault.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CHAOS_NS="cg-chaos"
TARGET_NS="cg-dev"
FAILED=0

k() { timeout 25s kubectl --context "$CTX" --request-timeout=15s "$@"; }
pod_uids() {
  k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[*].metadata.uid}' 2>/dev/null \
    | tr ' ' '\n' | sed '/^$/d' | sort
}
pod_ready_any() {
  k get pods -n "$TARGET_NS" -l app=gateway -o jsonpath='{.items[*].status.containerStatuses[0].ready}' 2>/dev/null \
    | tr ' ' '\n' | grep -q '^true$'
}
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
GW_UIDS_BEFORE=$(pod_uids)
[[ -n "$GW_UIDS_BEFORE" ]] \
  && ok "gateway pod UIDs captured ($(echo "$GW_UIDS_BEFORE" | tr '\n' ' '))" \
  || abort "no se capturaron los UID de los pods gateway"
k delete podchaos gateway-pod-kill -n "$CHAOS_NS" --ignore-not-found >/dev/null 2>&1 || true
k delete networkchaos redis-network-delay -n "$CHAOS_NS" --ignore-not-found >/dev/null 2>&1 || true

echo "=== 3. Apply the real PodChaos experiment ==="
k apply -f deploy/chaos/chaos-experiments.yml 2>&1 \
  || abort "no se aplicó el experimento (webhook/API no disponible)"
sleep 6
PC_EXISTS=$(k get podchaos gateway-pod-kill -n "$CHAOS_NS" -o name 2>/dev/null || echo "")
[[ -n "$PC_EXISTS" ]] \
  && ok "PodChaos resource created ($PC_EXISTS)" \
  || abort "PodChaos resource was not created"
k get podchaos gateway-pod-kill -n "$CHAOS_NS" 2>&1

echo "=== 4. Injection evidence: one gateway pod UID must disappear ==="
GW_KILLED=0
for i in $(seq 1 20); do
  GW_UIDS_NOW=$(pod_uids)
  if [[ -n "$GW_UIDS_NOW" ]]; then
    MISSING_UID=$(comm -23 <(echo "$GW_UIDS_BEFORE") <(echo "$GW_UIDS_NOW") | head -1)
    if [[ -n "$MISSING_UID" ]]; then
      GW_KILLED=1
      echo "  gateway pod killed: uid $MISSING_UID disappeared; current uids: $(echo "$GW_UIDS_NOW" | tr '\n' ' ')"
      break
    fi
  fi
  sleep 3
done
[[ "$GW_KILLED" -eq 1 ]] \
  && ok "PodChaos really killed a gateway pod (UID disappeared)" \
  || fail "no evidence of pod-kill (UID set unchanged during 60s window)"

echo "=== 5. Recovery evidence: gateway returns to ready state (max 180s) ==="
GW_AFTER=0
for i in $(seq 1 60); do
  GW_AFTER=$(k get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
  [[ -z "$GW_AFTER" ]] && GW_AFTER=0
  if [[ "$GW_AFTER" -ge 1 ]]; then break; fi
  # The host is heavily loaded; a ready pod is real evidence even if the
  # Deployment status has not caught up yet.
  if pod_ready_any; then
    echo "  gateway pod ready again (Deployment readyReplicas not reported yet)"
    GW_AFTER=1
    break
  fi
  sleep 3
done
[[ "$GW_AFTER" -ge 1 ]] \
  && ok "gateway recovered after pod-kill (ready=$GW_AFTER)" \
  || fail "gateway did not recover (ready=$GW_AFTER after 180s)"

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
GW_FINAL=0
for i in $(seq 1 60); do
  GW_FINAL=$(k get deploy gateway -n "$TARGET_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)
  [[ -z "$GW_FINAL" ]] && GW_FINAL=0
  if [[ "$GW_FINAL" -ge 1 ]]; then break; fi
  if pod_ready_any; then
    GW_FINAL=1
    break
  fi
  sleep 3
done
[[ "$GW_FINAL" -ge 1 ]] \
  && ok "gateway steady after rollback (ready=$GW_FINAL)" \
  || fail "gateway not steady after rollback (ready=$GW_FINAL after 180s)"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "CHAOS_VERIFICATION_PASS"
else
  echo "CHAOS_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"
