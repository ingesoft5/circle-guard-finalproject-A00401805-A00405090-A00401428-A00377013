#!/usr/bin/env bash
# CG-DOC: Verify the L284 service-mesh bonus on the live k3d cluster.
# Runs on the on-prem k3d host. All checks are read-only except the synthetic
# HTTP requests used to measure the canary split. Prints REAL results only:
# it never fabricates mTLS certificates or traffic distributions.
set -uo pipefail

CTX="${KUBE_CONTEXT:-k3d-cg}"
CANARY_NS="cg-mesh-canary"
INGRESS_NS="istio-ingress"
NODE_IP="${NODE_IP:-172.20.0.2}"
NODE_PORT="${NODE_PORT:-31353}"
REQS="${REQS:-40}"
FAILED=0

ok()   { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; FAILED=$((FAILED+1)); }

echo "=== 1. Mesh control plane and canary pods ==="
for ns in istio-system "$INGRESS_NS" "$CANARY_NS"; do
  echo "-- namespace $ns --"
  kubectl --context "$CTX" get pods -n "$ns" -o wide 2>&1
done
STABLE_READY="$(kubectl --context "$CTX" get deploy form-stable -n "$CANARY_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
HU6_READY="$(kubectl --context "$CTX" get deploy form-hu6 -n "$CANARY_NS" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
[[ "$STABLE_READY" == "1" ]] && ok "form-stable ready=1" || fail "form-stable ready=$STABLE_READY"
[[ "$HU6_READY" == "1" ]] && ok "form-hu6 ready=1" || fail "form-hu6 ready=$HU6_READY"
SIDECARS="$(kubectl --context "$CTX" get pods -n "$CANARY_NS" -o jsonpath='{.items[*].spec.containers[*].name}' 2>/dev/null | tr ' ' '\n' | grep -c '^istio-proxy$')"
[[ "$SIDECARS" == "2" ]] && ok "2 istio-proxy sidecars injected" || fail "istio-proxy sidecars=$SIDECARS (expected 2)"

echo "=== 2. mTLS policy (PeerAuthentication + DestinationRule) ==="
kubectl --context "$CTX" get peerauthentication,destinationrule -n "$CANARY_NS" 2>&1
STRICT_COUNT="$(kubectl --context "$CTX" get peerauthentication -n "$CANARY_NS" -o json 2>/dev/null | grep -o '"mode": "STRICT"' | wc -l)"
[[ "$STRICT_COUNT" -ge 2 ]] && ok "STRICT mTLS on both canary ports" || fail "STRICT count=$STRICT_COUNT"
MUTUAL_COUNT="$(kubectl --context "$CTX" get destinationrule -n "$CANARY_NS" -o json 2>/dev/null | grep -c 'ISTIO_MUTUAL')"
[[ "$MUTUAL_COUNT" -ge 2 ]] && ok "ISTIO_MUTUAL originations present" || fail "ISTIO_MUTUAL count=$MUTUAL_COUNT"

echo "=== 3. SPIFFE certificate from the Envoy sidecar ==="
CERT="$(kubectl --context "$CTX" exec -n "$CANARY_NS" deploy/form-stable -c istio-proxy -- curl -s --max-time 5 http://localhost:15000/certs 2>/dev/null)"
echo "$CERT" | grep -F 'spiffe://cluster.local/ns/cg-mesh-canary/sa/default' | head -2
# `grep -q` exits early and SIGPIPEs printf on the large cert output; with
# `set -o pipefail` that yields a false 141. Count matches instead.
SPIFFE_COUNT="$(printf '%s' "$CERT" | grep -cF 'spiffe://cluster.local/ns/cg-mesh-canary/sa/default')"
[[ "$SPIFFE_COUNT" -ge 1 ]] \
  && ok "workload identity SPIFFE present in sidecar ($SPIFFE_COUNT matches)" \
  || fail "SPIFFE identity not found in sidecar certs"

echo "=== 4. Traffic through Envoy ==="
HEALTH="$(curl -s -i --max-time 10 "http://${NODE_IP}:${NODE_PORT}/actuator/health" 2>/dev/null)"
echo "$HEALTH" | grep -iE '^HTTP/|server: istio-envoy' | head -3
ENVOY_COUNT="$(printf '%s' "$HEALTH" | grep -ci 'istio-envoy')"
[[ "$ENVOY_COUNT" -ge 1 ]] && ok "response served by istio-envoy" || fail "no istio-envoy header"

echo "=== 5. Canary 90/10 split (${REQS} requests) ==="
stats_counter() {
  local workload="$1"
  kubectl --context "$CTX" exec -n "$INGRESS_NS" deploy/istio-ingress -c istio-proxy -- \
    curl -s --max-time 5 http://localhost:15000/stats 2>/dev/null \
    | grep 'istiocustom.istio_requests_total.reporter.source' \
    | grep "destination_workload.${workload}." \
    | grep 'response_code.200' \
    | awk -F': ' '{s+=$NF} END{print s+0}'
}
B_STABLE="$(stats_counter form-stable)"
B_HU6="$(stats_counter form-hu6)"
echo "  before: stable=$B_STABLE hu6=$B_HU6"
for i in $(seq 1 "$REQS"); do
  curl -s -o /dev/null --max-time 5 "http://${NODE_IP}:${NODE_PORT}/actuator/health" || true
done
A_STABLE="$(stats_counter form-stable)"
A_HU6="$(stats_counter form-hu6)"
echo "  after:  stable=$A_STABLE hu6=$A_HU6"
D_STABLE=$((A_STABLE - B_STABLE))
D_HU6=$((A_HU6 - B_HU6))
TOTAL=$((D_STABLE + D_HU6))
echo "  delta:  stable=$D_STABLE hu6=$D_HU6 total=$TOTAL"
if [[ "$TOTAL" -gt 0 ]]; then
  PCT_HU6=$(( (D_HU6 * 100) / TOTAL ))
  echo "  canary share: ${PCT_HU6}%"
  [[ "$D_HU6" -gt 0 && "$D_STABLE" -gt "$D_HU6" ]] \
    && ok "weighted routing sends traffic to BOTH versions (stable=$D_STABLE, canary=$D_HU6)" \
    || fail "expected both versions to receive traffic"
else
  fail "no requests counted in Envoy stats"
fi

echo "=== 6. Header-based stable pin (x-canary: false -> 100% stable) ==="
P_STABLE="$(stats_counter form-stable)"
P_HU6="$(stats_counter form-hu6)"
for i in $(seq 1 10); do
  curl -s -o /dev/null --max-time 5 -H 'x-canary: false' "http://${NODE_IP}:${NODE_PORT}/actuator/health" || true
done
Q_STABLE="$(stats_counter form-stable)"
Q_HU6="$(stats_counter form-hu6)"
echo "  pinned delta: stable=$((Q_STABLE-P_STABLE)) hu6=$((Q_HU6-P_HU6))"
[[ "$((Q_HU6-P_HU6))" -eq 0 && "$((Q_STABLE-P_STABLE))" -eq 10 ]] \
  && ok "x-canary:false pinned 10/10 to stable" \
  || fail "header pin did not route 10/10 to stable"

echo "=== RESULT ==="
if [[ "$FAILED" -eq 0 ]]; then
  echo "MESH_VERIFICATION_PASS"
else
  echo "MESH_VERIFICATION_FAIL ($FAILED checks failed)"
fi
exit "$FAILED"
