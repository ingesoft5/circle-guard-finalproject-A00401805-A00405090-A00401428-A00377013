<!-- CG-DOC: Chaos Engineering Bonus - Verification Record. -->
# Chaos Engineering Bonus - Verification Record
# Enunciado L284: Chaos Engineering with Chaos Mesh/Litmus

## Hypothesis
The CircleGuard system maintains acceptable availability and response times
during transient infrastructure failures (pod deletions, network latency).

## Steady State Definition
- All core services (gateway, form, auth, identity) respond to health checks
  within 500ms at P99 latency
- Zero 5xx errors during chaos injection
- Service recovery time < 30 seconds after fault injection stops

## Experiment Plan

### Experiment 1: Gateway Pod Delete
- **Target**: gateway deployment (app=gateway) in cg-dev
- **Action**: Delete 100% of pods for 30 seconds
- **Expected Impact**: Brief 503 errors during pod restart
- **Recovery Criteria**: All pods return to Running within 30s, health checks pass
- **Rollback**: `kubectl rollout restart deployment/gateway -n cg-dev`

### Experiment 2: Redis Network Latency
- **Target**: redis deployment in cg-dev
- **Action**: Inject 2000ms ± 500ms network latency for 60 seconds
- **Expected Impact**: Degraded response times for cached operations
- **Recovery Criteria**: Latency returns to <100ms after experiment ends
- **Rollback**: `kubectl rollout restart deployment/redis -n cg-dev`

## Success Criteria
1. System remains available (no complete outage)
2. Health checks recover to UP within 60s of fault removal
3. No data corruption observed
4. Monitoring alerts fire appropriately

## Rollback Procedure
1. Stop chaos experiment: `kubectl delete chaosengine <name> -n cg-chaos`
2. Force restart affected deployment if needed
3. Verify service health: `kubectl get pods -n cg-dev`